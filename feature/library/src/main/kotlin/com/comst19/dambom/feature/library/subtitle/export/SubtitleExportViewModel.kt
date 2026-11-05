package com.comst19.dambom.feature.library.subtitle.export

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comst19.dambom.core.common.util.suspendRunCatching
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.core.domain.repository.DownloadRepository
import com.comst19.dambom.core.navigation.NavigationDispatcher
import com.comst19.dambom.core.navigation.NavigationEvent
import com.comst19.dambom.feature.library.subtitle.data.SubtitleFiles
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

internal data class SubtitleExportState(
    val task: DownloadTask? = null,
    val loading: Boolean = true,
    val exporting: Boolean = false,
    val progress: Int? = null,
    val failed: Boolean = false,
    val saved: Boolean = false,
)

@HiltViewModel
internal class SubtitleExportViewModel
    @Inject
    constructor(
        private val repository: DownloadRepository,
        private val files: SubtitleFiles,
        private val exporter: SubtitleVideoExporter,
        private val navigation: NavigationDispatcher,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(SubtitleExportState())
        val state = mutableState.asStateFlow()
        private var loadJob: Job? = null
        private var exportJob: Job? = null
        private var activeDestination: String? = null

        fun load(id: String) {
            if (loadJob?.isActive == true || state.value.task != null) return
            loadJob =
                viewModelScope.launch {
                    suspendRunCatching {
                        val task = requireTask(id)
                        check(files.read(checkNotNull(task.localFilePath)).isNotEmpty())
                        mutableState.update { it.copy(task = task) }
                    }.onFailure { mutableState.update { it.copy(failed = true) } }
                    mutableState.update { it.copy(loading = false) }
                }
        }

        fun export(
            id: String,
            destination: Uri,
        ) {
            val uri = destination.toString()
            if (exportJob?.isActive == true) {
                if (activeDestination != uri) viewModelScope.launch { exporter.discard(uri) }
                return
            }
            activeDestination = uri
            exportJob =
                viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    mutableState.update { it.copy(exporting = true, failed = false, saved = false, progress = null) }
                    var owned = false
                    try {
                        val task = requireTask(id)
                        mutableState.update { it.copy(task = task, loading = false) }
                        val path = checkNotNull(task.localFilePath)
                        val cues = files.read(path)
                        check(cues.isNotEmpty())
                        owned = true
                        exporter.export(path, cues, uri) { value -> mutableState.update { it.copy(progress = value) } }
                        mutableState.update { it.copy(saved = true) }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        mutableState.update { it.copy(failed = true) }
                    } finally {
                        if (!owned) withContext(NonCancellable) { exporter.discard(uri) }
                        activeDestination = null
                        mutableState.update { it.copy(exporting = false, progress = null) }
                    }
                }
        }

        private suspend fun requireTask(id: String): DownloadTask =
            checkNotNull(repository.observeDownload(id).first()).also {
                check(!it.isPrivate && !it.deletePending && it.status == DownloadStatus.COMPLETED)
            }

        fun cancel() {
            exportJob?.cancel()
        }

        fun goBack() {
            viewModelScope.launch { navigation.dispatch(NavigationEvent.Back) }
        }
    }
