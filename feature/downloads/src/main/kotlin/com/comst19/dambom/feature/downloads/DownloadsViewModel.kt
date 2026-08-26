package com.comst19.dambom.feature.downloads

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comst19.dambom.core.common.ui.AppEvent
import com.comst19.dambom.core.common.ui.AppEventBus
import com.comst19.dambom.core.common.ui.UiText
import com.comst19.dambom.core.common.util.suspendRunCatching
import com.comst19.dambom.core.domain.repository.DownloadRepository
import com.comst19.dambom.core.navigation.NavigationDispatcher
import com.comst19.dambom.core.navigation.NavigationEvent
import com.comst19.dambom.feature.downloads.contract.DownloadsUiState
import com.comst19.dambom.feature.downloads.contract.DownloadsViewMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class DownloadsViewModel
    @Inject
    constructor(
        private val repository: DownloadRepository,
        private val navigation: NavigationDispatcher,
        private val savedStateHandle: SavedStateHandle,
        private val appEventBus: AppEventBus,
    ) : ViewModel() {
        private val viewMode =
            MutableStateFlow(
                savedStateHandle
                    .get<String>(VIEW_MODE_KEY)
                    ?.let { stored -> DownloadsViewMode.entries.firstOrNull { it.name == stored } }
                    ?: DownloadsViewMode.GRID,
            )
        val uiState: StateFlow<DownloadsUiState> =
            combine(repository.downloads, viewMode) { tasks, currentViewMode ->
                DownloadsUiState(
                    tasks = tasks.toPersistentList(),
                    viewMode = currentViewMode,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = DownloadsUiState(viewMode = viewMode.value),
            )

        fun setViewMode(mode: DownloadsViewMode) {
            viewMode.value = mode
            savedStateHandle[VIEW_MODE_KEY] = mode.name
        }

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
            viewModelScope.launch {
                suspendRunCatching(block)
                    .onFailure {
                        appEventBus.send(
                            AppEvent.ShowSnackbar(UiText.Resource(R.string.downloads_command_failed)),
                        )
                    }
            }
        }
    }

private const val STOP_TIMEOUT_MILLIS = 5_000L
private const val VIEW_MODE_KEY = "downloads-view-mode"
