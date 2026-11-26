package com.comst19.dambom.feature.library.trim

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comst19.dambom.core.common.util.suspendRunCatching
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.repository.DownloadRepository
import com.comst19.dambom.core.navigation.NavigationDispatcher
import com.comst19.dambom.core.navigation.NavigationEvent
import com.comst19.dambom.feature.library.trim.contract.TrimSelection
import com.comst19.dambom.feature.library.trim.contract.VideoTrimState
import com.comst19.dambom.feature.library.trim.export.ClipExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
internal class VideoTrimViewModel
    @Inject
    constructor(
        private val repository: DownloadRepository,
        private val exporter: ClipExporter,
        private val navigation: NavigationDispatcher,
        private val savedState: SavedStateHandle,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(VideoTrimState(savedUri = savedState["trim-saved-uri"]))
        val state = mutableState.asStateFlow()
        private var exportJob: Job? = null
        private var loadJob: Job? = null
        private var activeExportToken: Any? = null
        private var activeDestination: String? = null

        fun load(id: String) {
            if (state.value.task != null || loadJob?.isActive == true) return
            loadJob = viewModelScope.launch { loadVideo(id) }
        }

        private suspend fun loadVideo(id: String) {
            mutableState.update { it.copy(loading = true, loadFailed = false) }
            suspendRunCatching {
                val task = checkNotNull(repository.observeDownload(id).first())
                check(!task.isPrivate && task.status == DownloadStatus.COMPLETED && !task.deletePending)
                val duration = exporter.durationMillis(checkNotNull(task.localFilePath))
                val restored = TrimSelection(savedState["trim-start"] ?: 0L, savedState["trim-end"] ?: duration)
                mutableState.update {
                    it.copy(
                        task = task,
                        durationMillis = duration,
                        selection = restored.takeIf { it.isValidFor(duration) } ?: TrimSelection(0L, duration),
                        loading = false,
                    )
                }
            }.onFailure { mutableState.update { it.copy(loading = false, loadFailed = true) } }
        }

        fun select(selection: TrimSelection) {
            if (state.value.exporting || !selection.isValidFor(state.value.durationMillis)) return
            savedState["trim-start"] = selection.startMillis
            savedState["trim-end"] = selection.endMillis
            savedState["trim-saved-uri"] = null
            mutableState.update { it.copy(selection = selection, savedUri = null, exportFailed = false) }
        }

        fun export(
            id: String,
            destination: Uri,
        ) {
            val destinationUri = destination.toString()
            if (exportJob?.isActive == true) {
                if (activeDestination != destinationUri) {
                    viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                        withContext(NonCancellable) {
                            suspendRunCatching { exporter.discard(destinationUri) }
                        }
                    }
                }
                return
            }
            val exportToken = Any()
            activeExportToken = exportToken
            activeDestination = destinationUri
            exportJob =
                viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    mutableState.update {
                        it.copy(exporting = true, exportFailed = false, savedUri = null, progress = null)
                    }
                    savedState["trim-saved-uri"] = null
                    var exporterOwnsDocument = false
                    try {
                        load(id)
                        loadJob?.join()
                        val current = state.value
                        val path = checkNotNull(current.task?.localFilePath)
                        currentCoroutineContext().ensureActive()
                        exporterOwnsDocument = true
                        exporter.export(
                            path = path,
                            selection = current.selection,
                            destination = destinationUri,
                            onProgress = { progress -> mutableState.update { it.copy(progress = progress) } },
                        )
                        savedState["trim-saved-uri"] = destinationUri
                        mutableState.update { it.copy(savedUri = destinationUri) }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        mutableState.update { it.copy(exportFailed = true) }
                    } finally {
                        if (!exporterOwnsDocument) {
                            withContext(NonCancellable) {
                                suspendRunCatching { exporter.discard(destinationUri) }
                            }
                        }
                        if (activeExportToken === exportToken) {
                            activeExportToken = null
                            activeDestination = null
                            mutableState.update { it.copy(exporting = false, progress = null) }
                        }
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
