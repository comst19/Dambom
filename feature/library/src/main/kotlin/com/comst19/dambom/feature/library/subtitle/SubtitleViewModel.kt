package com.comst19.dambom.feature.library.subtitle

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comst19.dambom.core.common.util.suspendRunCatching
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.repository.DownloadRepository
import com.comst19.dambom.core.navigation.NavigationDispatcher
import com.comst19.dambom.core.navigation.NavigationEvent
import com.comst19.dambom.core.navigation.contract.LibraryGraph.VideoSubtitleTranscriptKey
import com.comst19.dambom.feature.library.subtitle.data.SubtitleFiles
import com.comst19.dambom.feature.library.subtitle.inference.SubtitleEngine
import com.comst19.dambom.feature.library.subtitle.inference.SubtitleModelStatus
import com.comst19.dambom.feature.library.subtitle.model.SubtitleLanguagePair
import com.comst19.dambom.feature.library.subtitle.work.SubtitleJobs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Suppress("TooManyFunctions")
@HiltViewModel
internal class SubtitleViewModel
    @Inject
    constructor(
        private val repository: DownloadRepository,
        private val engine: SubtitleEngine,
        private val files: SubtitleFiles,
        private val navigation: NavigationDispatcher,
        private val savedState: SavedStateHandle,
        private val jobs: SubtitleJobs,
    ) : ViewModel() {
        private val initialPair =
            SubtitleLanguagePair.entries.firstOrNull {
                it.name == savedState.get<String>(SUBTITLE_PAIR_STATE)
            } ?: SubtitleLanguagePair.ENGLISH_KOREAN
        private val initialModel = engine.cachedStatus(initialPair)
        private val mutableState =
            MutableStateFlow(
                SubtitleState(
                    pair = initialPair,
                    model = initialModel ?: SubtitleModelStatus.CHECKING,
                    localOperation = if (initialModel == null) SubtitleOperation.CHECKING else SubtitleOperation.IDLE,
                ),
            )
        val state = mutableState.asStateFlow()
        private var job: Job? = null
        private var activeDestination: Uri? = null
        private var observation: Job? = null
        private var modelObservation: Job? = null

        fun load(id: String) {
            if (state.value.task != null || job?.isActive == true) return
            job =
                viewModelScope.launch {
                    suspendRunCatching {
                        val task = checkNotNull(repository.observeDownload(id).first())
                        check(task.status == DownloadStatus.COMPLETED && !task.deletePending)
                        val path = checkNotNull(task.localFilePath)
                        val cues = files.read(path)
                        mutableState.update { it.copy(task = task, cues = cues.toPersistentList()) }
                        observeModelDownload()
                        observeJobs(task.id, path)
                        checkModel()
                    }.onFailure {
                        mutableState.update { it.copy(loadFailed = true, localOperation = SubtitleOperation.IDLE) }
                    }
                }
        }

        fun select(pair: SubtitleLanguagePair) {
            if (state.value.task == null || state.value.busy || state.value.pair == pair) return
            job?.cancel()
            savedState[SUBTITLE_PAIR_STATE] = pair.name
            mutableState.update { it.copy(pair = pair, failed = false, exported = false) }
            refresh()
        }

        fun refresh() {
            if (state.value.busy) return
            job?.cancel()
            job = viewModelScope.launch { checkModel(forceRefresh = true) }
        }

        private suspend fun checkModel(forceRefresh: Boolean = false) {
            val known = engine.cachedStatus(state.value.pair)
            mutableState.update {
                it.copy(
                    localOperation =
                        if (known == null || forceRefresh) {
                            SubtitleOperation.CHECKING
                        } else {
                            SubtitleOperation.IDLE
                        },
                    model = known ?: SubtitleModelStatus.CHECKING,
                )
            }
            var checkedPair: SubtitleLanguagePair
            var result: SubtitleModelStatus
            do {
                checkedPair = state.value.pair
                result =
                    suspendRunCatching { engine.status(checkedPair, forceRefresh) }
                        .getOrDefault(SubtitleModelStatus.FAILED)
            } while (checkedPair != state.value.pair)
            mutableState.update { it.copy(model = result, localOperation = SubtitleOperation.IDLE) }
        }

        fun download() {
            if (state.value.operation != SubtitleOperation.IDLE ||
                state.value.model !in
                setOf(
                    SubtitleModelStatus.DOWNLOADABLE,
                    SubtitleModelStatus.PREPARATION_REQUIRED,
                    SubtitleModelStatus.AVAILABLE,
                )
            ) {
                return
            }
            job =
                viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    mutableState.update { it.copy(localOperation = SubtitleOperation.DOWNLOADING, failed = false) }
                    suspendRunCatching { jobs.enqueueModelDownload(state.value.pair) }.onFailure {
                        mutableState.update { it.copy(localOperation = SubtitleOperation.IDLE, failed = true) }
                    }
                }
        }

        private fun observeModelDownload() {
            modelObservation?.cancel()
            modelObservation =
                viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    jobs
                        .observeModelDownload()
                        .distinctUntilChanged()
                        .catch {
                            mutableState.update { it.copy(localOperation = SubtitleOperation.IDLE, failed = true) }
                        }.collect { work ->
                            mutableState.update {
                                val wasDownloading = it.modelDownload?.active == true
                                val enqueuing = it.localOperation == SubtitleOperation.DOWNLOADING
                                it.copy(
                                    modelDownload = work,
                                    localOperation =
                                        if (it.localOperation ==
                                            SubtitleOperation.DOWNLOADING
                                        ) {
                                            SubtitleOperation.IDLE
                                        } else {
                                            it.localOperation
                                        },
                                    failed =
                                        when {
                                            work == null -> it.failed
                                            work.active -> false
                                            wasDownloading || enqueuing -> work.failed
                                            else -> it.failed
                                        },
                                )
                            }
                            if (work != null && !work.active) checkModel()
                        }
                }
        }

        fun speechPermissionDenied() {
            mutableState.update { it.copy(failed = true, speechPermissionDenied = true) }
        }

        fun generate() {
            val current = state.value
            val task = current.task ?: return
            if (current.operation != SubtitleOperation.IDLE || current.model != SubtitleModelStatus.AVAILABLE) return
            mutableState.update {
                it.copy(
                    localOperation = SubtitleOperation.GENERATING,
                    failed = false,
                    speechPermissionDenied = false,
                    processedMillis = 0,
                )
            }
            job =
                viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    suspendRunCatching { jobs.enqueue(task.id, current.pair) }.onFailure {
                        mutableState.update { it.copy(localOperation = SubtitleOperation.IDLE, failed = true) }
                    }
                }
        }

        private fun observeJobs(
            id: String,
            path: String,
        ) {
            observation?.cancel()
            observation =
                viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    jobs
                        .observe(id)
                        .distinctUntilChanged()
                        .catch {
                            mutableState.update {
                                it.copy(
                                    failed = true,
                                    generation = null,
                                    localOperation =
                                        if (it.localOperation == SubtitleOperation.GENERATING) {
                                            SubtitleOperation.IDLE
                                        } else {
                                            it.localOperation
                                        },
                                )
                            }
                        }.collect { work ->
                            if (work != null) {
                                val restorePair =
                                    work.active || (work.completed && !savedState.contains(SUBTITLE_PAIR_STATE))
                                if (restorePair) savedState[SUBTITLE_PAIR_STATE] = work.pair.name
                                mutableState.update {
                                    val wasGenerating = it.generation?.active == true
                                    val enqueuing = it.localOperation == SubtitleOperation.GENERATING
                                    it.copy(
                                        generation = work,
                                        localOperation =
                                            if (it.localOperation == SubtitleOperation.GENERATING) {
                                                SubtitleOperation.IDLE
                                            } else {
                                                it.localOperation
                                            },
                                        pair = if (restorePair) work.pair else it.pair,
                                        processedMillis = work.processedMillis,
                                        failed = work.failed && (wasGenerating || enqueuing),
                                    )
                                }
                                if (work.completed) {
                                    suspendRunCatching { files.read(path) }
                                        .onSuccess { cues ->
                                            mutableState.update { it.copy(cues = cues.toPersistentList()) }
                                        }.onFailure { mutableState.update { it.copy(failed = true) } }
                                }
                            }
                        }
                }
        }

        fun viewSubtitles() {
            val current = state.value
            val task = current.task ?: return
            if (current.busy || current.cues.isEmpty()) return
            viewModelScope.launch { navigation.dispatch(NavigationEvent.Navigate(VideoSubtitleTranscriptKey(task.id))) }
        }

        fun export(destination: Uri) {
            val path = state.value.task?.localFilePath
            if (state.value.operation != SubtitleOperation.IDLE || path == null || state.value.cues.isEmpty()) {
                if (activeDestination != destination) viewModelScope.launch { files.discard(destination) }
                return
            }
            activeDestination = destination
            job =
                viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    mutableState.update {
                        it.copy(localOperation = SubtitleOperation.EXPORTING, failed = false, exported = false)
                    }
                    try {
                        files.export(path, destination)
                        mutableState.update { it.copy(exported = true) }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        mutableState.update { it.copy(failed = true) }
                    } finally {
                        activeDestination = null
                        mutableState.update { it.copy(localOperation = SubtitleOperation.IDLE) }
                    }
                }
        }

        fun cancel() {
            if (state.value.operation == SubtitleOperation.GENERATING) {
                state.value.task
                    ?.id
                    ?.let { id ->
                        viewModelScope.launch {
                            suspendRunCatching { jobs.cancel(id) }
                                .onFailure { mutableState.update { it.copy(failed = true) } }
                        }
                    }
            } else if (state.value.operation == SubtitleOperation.DOWNLOADING) {
                viewModelScope.launch {
                    suspendRunCatching { jobs.cancelModelDownload() }.onFailure {
                        mutableState.update { it.copy(failed = true) }
                    }
                }
            } else {
                job?.cancel()
            }
        }

        fun deleteModel() {
            if (state.value.busy) return
            job?.cancel()
            job =
                viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    mutableState.update { it.copy(localOperation = SubtitleOperation.DELETING, failed = false) }
                    try {
                        engine.deleteModel()
                        checkModel()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        mutableState.update { it.copy(failed = true) }
                    } finally {
                        mutableState.update { it.copy(localOperation = SubtitleOperation.IDLE) }
                    }
                }
        }

        fun goBack() {
            viewModelScope.launch { navigation.dispatch(NavigationEvent.Back) }
        }
    }

private const val SUBTITLE_PAIR_STATE = "subtitle-pair"
