package com.comst19.dambom.feature.library

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comst19.dambom.core.common.suspendRunCatching
import com.comst19.dambom.core.common.ui.SnackbarEvent
import com.comst19.dambom.core.common.ui.SnackbarEventBus
import com.comst19.dambom.core.common.ui.UiText
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.core.domain.repository.DownloadRepository
import com.comst19.dambom.core.navigation.NavigationDispatcher
import com.comst19.dambom.core.navigation.NavigationEvent
import com.comst19.dambom.core.navigation.contract.LibraryGraph.VideoDetailKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class LibraryViewModel
    @Inject
    constructor(
        private val repository: DownloadRepository,
        private val navigation: NavigationDispatcher,
        private val savedStateHandle: SavedStateHandle,
        private val fileManager: LibraryFileManager,
        private val snackbarEventBus: SnackbarEventBus,
    ) : ViewModel() {
        private val selectedId = savedStateHandle.getStateFlow<String?>(SELECTED_ID_KEY, null)
        private val query = savedStateHandle.getStateFlow(QUERY_KEY, "")

        val uiState: StateFlow<LibraryUiState> =
            combine(repository.downloads, selectedId, query, ::toLibraryUiState)
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = LibraryUiState(),
                )

        fun openVideo(id: String) {
            savedStateHandle[SELECTED_ID_KEY] = id
            viewModelScope.launch { navigation.dispatch(NavigationEvent.Navigate(VideoDetailKey(id))) }
        }

        fun goBack() {
            viewModelScope.launch { navigation.dispatch(NavigationEvent.Back) }
        }

        fun updateQuery(query: String) {
            savedStateHandle[QUERY_KEY] = query
        }

        fun rename(
            task: DownloadTask,
            title: String,
        ) {
            val trimmedTitle = title.trim()
            if (trimmedTitle.isEmpty() || trimmedTitle == task.title) return
            viewModelScope.launch {
                suspendRunCatching { repository.rename(task.id, trimmedTitle) }
                    .notifyResult(R.string.library_rename_success, R.string.library_rename_failure)
            }
        }

        fun delete(
            task: DownloadTask,
            closeDetail: Boolean = false,
        ) {
            viewModelScope.launch {
                suspendRunCatching { repository.delete(task.id) }.fold(
                    onSuccess = {
                        if (selectedId.value == task.id) savedStateHandle[SELECTED_ID_KEY] = null
                        if (closeDetail) navigation.dispatch(NavigationEvent.Back)
                        showMessage(R.string.library_delete_success)
                    },
                    onFailure = { showMessage(R.string.library_delete_failure) },
                )
            }
        }

        fun export(
            task: DownloadTask,
            destination: Uri,
        ) {
            viewModelScope.launch {
                suspendRunCatching { fileManager.export(task, destination) }
                    .notifyResult(R.string.library_export_success, R.string.library_export_failure)
            }
        }

        fun createShareIntent(task: DownloadTask): Intent = fileManager.createShareIntent(task)

        fun notifyShareFailure() {
            viewModelScope.launch { showMessage(R.string.library_share_failure) }
        }

        private suspend fun Result<Unit>.notifyResult(
            successMessage: Int,
            failureMessage: Int,
        ) {
            fold(
                onSuccess = { showMessage(successMessage) },
                onFailure = { showMessage(failureMessage) },
            )
        }

        private suspend fun showMessage(message: Int) {
            snackbarEventBus.send(SnackbarEvent(UiText.Resource(message)))
        }
    }

internal data class LibraryUiState(
    val videos: List<DownloadTask> = emptyList(),
    val selectedVideo: DownloadTask? = null,
    val query: String = "",
    val hasVideos: Boolean = false,
)

internal fun toLibraryUiState(
    tasks: List<DownloadTask>,
    selectedId: String?,
    query: String = "",
): LibraryUiState {
    val savedVideos =
        tasks.filter { task ->
            task.status == DownloadStatus.COMPLETED && task.localFilePath != null
        }
    val trimmedQuery = query.trim()
    val videos =
        if (trimmedQuery.isEmpty()) {
            savedVideos
        } else {
            savedVideos.filter { it.title.contains(trimmedQuery, ignoreCase = true) }
        }
    return LibraryUiState(
        videos = videos,
        selectedVideo = savedVideos.firstOrNull { it.id == selectedId },
        query = query,
        hasVideos = savedVideos.isNotEmpty(),
    )
}

private const val SELECTED_ID_KEY = "library-selected-video-id"
private const val QUERY_KEY = "library-search-query"
private const val STOP_TIMEOUT_MILLIS = 5_000L
