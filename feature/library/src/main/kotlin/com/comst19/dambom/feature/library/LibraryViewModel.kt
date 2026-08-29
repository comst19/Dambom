package com.comst19.dambom.feature.library

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comst19.dambom.core.common.ui.AppEvent
import com.comst19.dambom.core.common.ui.AppEventBus
import com.comst19.dambom.core.common.ui.UiText
import com.comst19.dambom.core.common.util.suspendRunCatching
import com.comst19.dambom.core.domain.model.AppSettings
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.core.domain.repository.DownloadRepository
import com.comst19.dambom.core.domain.repository.SettingsRepository
import com.comst19.dambom.core.navigation.NavigationDispatcher
import com.comst19.dambom.core.navigation.NavigationEvent
import com.comst19.dambom.core.navigation.contract.LibraryGraph.VideoDetailKey
import com.comst19.dambom.feature.library.contract.LibrarySourceFilter
import com.comst19.dambom.feature.library.contract.LibraryUiState
import com.comst19.dambom.feature.library.contract.LibraryViewMode
import com.comst19.dambom.feature.library.file.LibraryFileManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class LibraryViewModel
    @Inject
    constructor(
        private val repository: DownloadRepository,
        settingsRepository: SettingsRepository,
        private val navigation: NavigationDispatcher,
        private val savedStateHandle: SavedStateHandle,
        private val fileManager: LibraryFileManager,
        private val appEventBus: AppEventBus,
    ) : ViewModel() {
        private val selectedId = savedStateHandle.getStateFlow<String?>(SELECTED_ID_KEY, null)
        private val query = savedStateHandle.getStateFlow(QUERY_KEY, "")
        private val viewMode = savedStateHandle.getStateFlow(VIEW_MODE_KEY, LibraryViewMode.GRID.name)
        private val sourceFilter = savedStateHandle.getStateFlow(SOURCE_FILTER_KEY, LibrarySourceFilter.ALL.name)
        private val selection = MutableStateFlow(LibrarySelectionState())
        private val displayPreferences =
            combine(viewMode, sourceFilter) { viewMode, sourceFilter ->
                LibraryDisplayPreferences(
                    viewMode = LibraryViewMode.entries.firstOrNull { it.name == viewMode } ?: LibraryViewMode.GRID,
                    sourceFilter = LibrarySourceFilter.entries.firstOrNull { it.name == sourceFilter } ?: LibrarySourceFilter.ALL,
                )
            }

        val settings: StateFlow<AppSettings> =
            settingsRepository.settings.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = AppSettings(),
            )

        val uiState: StateFlow<LibraryUiState> =
            combine(
                repository.completedDownloads,
                selectedId,
                query,
                displayPreferences,
                selection,
            ) { tasks, selectedId, query, preferences, selection ->
                toLibraryUiState(
                    tasks = tasks,
                    selectedId = selectedId,
                    query = query,
                    viewMode = preferences.viewMode,
                    sourceFilter = preferences.sourceFilter,
                    selection = selection,
                )
            }.stateIn(
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

        fun setViewMode(viewMode: LibraryViewMode) {
            savedStateHandle[VIEW_MODE_KEY] = viewMode.name
        }

        fun setSourceFilter(sourceFilter: LibrarySourceFilter) {
            savedStateHandle[SOURCE_FILTER_KEY] = sourceFilter.name
        }

        fun startSelection() {
            selection.update { it.copy(isActive = true) }
        }

        fun toggleSelection(id: String) {
            selection.update { it.toggle(id) }
        }

        fun selectAllVisible() {
            selection.update { it.selectAll(uiState.value.videos.map(DownloadTask::id)) }
        }

        fun clearSelection() {
            selection.update(LibrarySelectionState::clear)
        }

        fun deleteSelected() {
            val ids = uiState.value.selectedIds
            if (ids.isEmpty()) return
            viewModelScope.launch {
                suspendRunCatching { ids.forEach { repository.delete(it) } }.fold(
                    onSuccess = {
                        if (selectedId.value in ids) savedStateHandle[SELECTED_ID_KEY] = null
                        selection.value = LibrarySelectionState()
                        showMessage(R.string.library_delete_selected_success, ids.size)
                    },
                    onFailure = { showMessage(R.string.library_delete_failure) },
                )
            }
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

        fun exportToConfiguredLocation(task: DownloadTask) {
            val treeUri = settings.value.downloadTreeUri?.let(Uri::parse)
            viewModelScope.launch {
                suspendRunCatching { fileManager.exportToConfiguredLocation(task, treeUri) }
                    .notifyResult(R.string.library_export_success, R.string.library_export_failure)
            }
        }

        fun createShareIntent(task: DownloadTask): Intent? = fileManager.createShareIntent(task)

        fun notifyShareFailure() {
            viewModelScope.launch { showMessage(R.string.library_share_failure) }
        }

        fun notifyShareLinkFailure() {
            viewModelScope.launch { showMessage(R.string.library_share_link_failure) }
        }

        fun notifyLinkCopied() {
            viewModelScope.launch { showMessage(R.string.library_copy_link_success) }
        }

        fun notifyOpenOriginalFailure() {
            viewModelScope.launch { showMessage(R.string.library_open_original_failure) }
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

        private suspend fun showMessage(
            message: Int,
            vararg args: Any,
        ) {
            appEventBus.send(AppEvent.ShowSnackbar(UiText.Resource(message, args.toList())))
        }
    }

private data class LibraryDisplayPreferences(
    val viewMode: LibraryViewMode,
    val sourceFilter: LibrarySourceFilter,
)

private const val SELECTED_ID_KEY = "library-selected-video-id"
private const val QUERY_KEY = "library-search-query"
private const val VIEW_MODE_KEY = "library-view-mode"
private const val SOURCE_FILTER_KEY = "library-source-filter"
private const val STOP_TIMEOUT_MILLIS = 5_000L
