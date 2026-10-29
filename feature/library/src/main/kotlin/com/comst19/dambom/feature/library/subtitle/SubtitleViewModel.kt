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
        private val mutableState =
            MutableStateFlow(
                SubtitleState(
                    pair =
                        SubtitleLanguagePair.entries.firstOrNull {
                            it.name == savedState.get<String>(SUBTITLE_PAIR_STATE)
                        }
                            ?: SubtitleLanguagePair.ENGLISH_KOREAN,
                ),
            )
        val state = mutableState.asStateFlow()
        private var job: Job? = null
        private var activeDestination: Uri? = null
        private var observation: Job? = null

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
            mutableState.update { it.copy(pair = pair, failed = false, exported = false, downloadedBytes = 0) }
            refresh()
        }

        fun refresh() {
            if (state.value.busy) return
            job?.cancel()
            job = viewModelScope.launch { checkModel() }
        }

        private suspend fun checkModel() {
            mutableState.update {
                it.copy(localOperation = SubtitleOperation.CHECKING, model = SubtitleModelStatus.CHECKING)
            }
            var checkedPair: SubtitleLanguagePair
            var result: SubtitleModelStatus
            do {
                checkedPair = state.value.pair
                result = suspendRunCatching { engine.status(checkedPair) }.getOrDefault(SubtitleModelStatus.FAILED)
            } while (checkedPair != state.value.pair)
            mutableState.update { it.copy(model = result, localOperation = SubtitleOperation.IDLE) }
        }

        fun download() {
            if (state.value.operation != SubtitleOperation.IDLE ||
                state.value.model !in setOf(SubtitleModelStatus.DOWNLOADABLE, SubtitleModelStatus.AVAILABLE)
            ) {
                return
            }
            job =
                viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    mutableState.update {
                        it.copy(localOperation = SubtitleOperation.DOWNLOADING, failed = false, downloadedBytes = 0)
                    }
                    try {
                        val status =
                            engine.download(state.value.pair) { bytes ->
                                mutableState.update { it.copy(downloadedBytes = bytes) }
                            }
                        mutableState.update { it.copy(model = status) }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        val remaining =
                            suspendRunCatching { engine.status(state.value.pair) }
                                .getOrDefault(SubtitleModelStatus.FAILED)
                        mutableState.update { it.copy(model = remaining, failed = true) }
                    } finally {
                        mutableState.update { it.copy(localOperation = SubtitleOperation.IDLE) }
                    }
                }
        }

        fun generate() {
            val current = state.value
            val task = current.task ?: return
            if (current.operation != SubtitleOperation.IDLE || current.model != SubtitleModelStatus.AVAILABLE) return
            mutableState.update {
                it.copy(localOperation = SubtitleOperation.GENERATING, failed = false, processedMillis = 0)
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
                                        failed = work.failed,
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
