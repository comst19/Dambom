package com.comst19.dambom.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
        repository: DownloadRepository,
        private val navigation: NavigationDispatcher,
        private val savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val selectedId = savedStateHandle.getStateFlow<String?>(SELECTED_ID_KEY, null)

        val uiState: StateFlow<LibraryUiState> =
            combine(repository.downloads, selectedId, ::toLibraryUiState)
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
    }

internal data class LibraryUiState(
    val videos: List<DownloadTask> = emptyList(),
    val selectedVideo: DownloadTask? = null,
)

internal fun toLibraryUiState(
    tasks: List<DownloadTask>,
    selectedId: String?,
): LibraryUiState {
    val videos =
        tasks.filter { task ->
            task.status == DownloadStatus.COMPLETED && task.localFilePath != null
        }
    return LibraryUiState(
        videos = videos,
        selectedVideo = videos.firstOrNull { it.id == selectedId },
    )
}

private const val SELECTED_ID_KEY = "library-selected-video-id"
private const val STOP_TIMEOUT_MILLIS = 5_000L
