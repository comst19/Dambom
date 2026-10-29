package com.comst19.dambom.feature.library.compress

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comst19.dambom.core.common.util.suspendRunCatching
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.repository.DownloadRepository
import com.comst19.dambom.core.navigation.NavigationDispatcher
import com.comst19.dambom.core.navigation.NavigationEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
internal class VideoCompressionViewModel
    @Inject
    constructor(
        private val repository: DownloadRepository,
        private val compressor: VideoCompressor,
        private val navigation: NavigationDispatcher,
        private val savedState: SavedStateHandle,
    ) : ViewModel() {
        private val mutableState =
            MutableStateFlow(
                VideoCompressionState(
                    profile =
                        CompressionProfile.entries.firstOrNull {
                            it.name == savedState.get<String>("compression-profile")
                        }
                            ?: CompressionProfile.BALANCED,
                    savedUri = savedState["compression-saved-uri"],
                    savedBytes = savedState["compression-saved-bytes"],
                ),
            )
        val state = mutableState.asStateFlow()
        private var loadJob: Job? = null
        private var exportJob: Job? = null
        private var activeDestination: String? = null

        fun load(id: String) {
            if (state.value.task != null || loadJob?.isActive == true) return
            loadJob =
                viewModelScope.launch {
                    mutableState.update { it.copy(loading = true, loadFailed = false) }
                    suspendRunCatching {
                        val task = checkNotNull(repository.observeDownload(id).first())
                        check(task.status == DownloadStatus.COMPLETED && !task.deletePending)
                        val source = compressor.inspect(checkNotNull(task.localFilePath))
                        mutableState.update { it.copy(task = task, source = source, loading = false) }
                    }.onFailure { mutableState.update { it.copy(loading = false, loadFailed = true) } }
                }
        }

        fun select(profile: CompressionProfile) {
            if (state.value.exporting || state.value.profile == profile) return
            savedState["compression-profile"] = profile.name
            savedState["compression-saved-uri"] = null
            savedState["compression-saved-bytes"] = null
            mutableState.update { it.copy(profile = profile, failure = null, savedUri = null, savedBytes = null) }
        }

        fun export(
            id: String,
            destination: Uri,
        ) {
            if (exportJob?.isActive == true) {
                if (activeDestination != destination.toString()) {
                    viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                        withContext(NonCancellable) { compressor.discard(destination.toString()) }
                    }
                }
                return
            }
            activeDestination = destination.toString()
            exportJob =
                viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    mutableState.update {
                        it.copy(exporting = true, progress = null, failure = null, savedUri = null, savedBytes = null)
                    }
                    savedState["compression-saved-uri"] = null
                    savedState["compression-saved-bytes"] = null
                    var compressorOwnsDocument = false
                    try {
                        load(id)
                        loadJob?.join()
                        val current = state.value
                        val path = checkNotNull(current.task?.localFilePath)
                        compressorOwnsDocument = true
                        val bytes =
                            compressor.compress(path, current.profile, destination.toString()) { progress ->
                                mutableState.update { it.copy(progress = progress) }
                            }
                        savedState["compression-saved-uri"] = destination.toString()
                        savedState["compression-saved-bytes"] = bytes
                        mutableState.update { it.copy(savedUri = destination.toString(), savedBytes = bytes) }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: CompressionNotSmallerException) {
                        mutableState.update { it.copy(failure = CompressionFailure.NOT_SMALLER) }
                    } catch (_: Exception) {
                        mutableState.update { it.copy(failure = CompressionFailure.FAILED) }
                    } finally {
                        if (!compressorOwnsDocument) {
                            withContext(NonCancellable) { compressor.discard(destination.toString()) }
                        }
                        activeDestination = null
                        mutableState.update { it.copy(exporting = false, progress = null) }
                    }
                }
        }

        fun cancelExport() {
            exportJob?.cancel()
        }

        fun goBack() {
            viewModelScope.launch { navigation.dispatch(NavigationEvent.Back) }
        }
    }
