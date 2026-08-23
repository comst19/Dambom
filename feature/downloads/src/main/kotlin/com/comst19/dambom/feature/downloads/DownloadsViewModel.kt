package com.comst19.dambom.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.core.domain.repository.DownloadRepository
import com.comst19.dambom.core.navigation.NavigationDispatcher
import com.comst19.dambom.core.navigation.NavigationEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class DownloadsViewModel
    @Inject
    constructor(
        private val repository: DownloadRepository,
        private val navigation: NavigationDispatcher,
    ) : ViewModel() {
        val uiState: StateFlow<DownloadsUiState> =
            repository.downloads
                .map(::DownloadsUiState)
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = DownloadsUiState(),
                )

        fun pause(id: String) = launchCommand { repository.pause(id) }

        fun resume(id: String) = launchCommand { repository.resume(id) }

        fun cancel(id: String) = launchCommand { repository.cancel(id) }

        fun retry(id: String) = launchCommand { repository.retry(id) }

        fun pauseAll() = launchCommand(repository::pauseAll)

        fun resumeAll() = launchCommand(repository::resumeAll)

        fun goBack() {
            viewModelScope.launch { navigation.dispatch(NavigationEvent.Back) }
        }

        private fun launchCommand(block: suspend () -> Unit) {
            viewModelScope.launch { block() }
        }
    }

internal data class DownloadsUiState(
    val tasks: List<DownloadTask> = emptyList(),
) {
    val activeCount: Int
        get() = tasks.count { it.status == DownloadStatus.DOWNLOADING }

    val totalCount: Int
        get() = tasks.size

    val canPauseAll: Boolean
        get() = tasks.any { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }

    val canResumeAll: Boolean
        get() = tasks.any { it.status == DownloadStatus.PAUSED }

    val progress: Float
        get() {
            val relevant = tasks.filter { it.status != DownloadStatus.FAILED }
            val totalBytes = relevant.mapNotNull { it.expectedBytes }.sum()
            return if (totalBytes > 0L) {
                (relevant.sumOf { it.downloadedBytes }.toFloat() / totalBytes).coerceIn(0f, 1f)
            } else {
                0f
            }
        }
}

private const val STOP_TIMEOUT_MILLIS = 5_000L
