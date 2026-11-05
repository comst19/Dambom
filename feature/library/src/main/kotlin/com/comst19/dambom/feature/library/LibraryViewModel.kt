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
import com.comst19.dambom.core.coroutine.DefaultDispatcher
import com.comst19.dambom.core.domain.model.AppSettings
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.core.domain.model.PrivateVideoSession
import com.comst19.dambom.core.domain.repository.DownloadRepository
import com.comst19.dambom.core.domain.repository.PlaybackHistoryRepository
import com.comst19.dambom.core.domain.repository.SettingsRepository
import com.comst19.dambom.core.navigation.NavigationDispatcher
import com.comst19.dambom.core.navigation.NavigationEvent
import com.comst19.dambom.core.navigation.contract.LibraryGraph.PrivateLibraryKey
import com.comst19.dambom.core.navigation.contract.LibraryGraph.VideoCompressionKey
import com.comst19.dambom.core.navigation.contract.LibraryGraph.VideoDetailKey
import com.comst19.dambom.core.navigation.contract.LibraryGraph.VideoSubtitleExportKey
import com.comst19.dambom.core.navigation.contract.LibraryGraph.VideoSubtitleKey
import com.comst19.dambom.core.navigation.contract.LibraryGraph.VideoTrimKey
import com.comst19.dambom.feature.library.contract.LibrarySourceFilter
import com.comst19.dambom.feature.library.contract.LibraryUiState
import com.comst19.dambom.feature.library.contract.LibraryViewMode
import com.comst19.dambom.feature.library.file.LibraryFileManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class LibraryViewModel
    @Inject
    @Suppress("LongParameterList")
    constructor(
        private val repository: DownloadRepository,
        private val settingsRepository: SettingsRepository,
        private val navigation: NavigationDispatcher,
        private val savedStateHandle: SavedStateHandle,
        private val fileManager: LibraryFileManager,
        private val appEventBus: AppEventBus,
        private val playbackHistoryRepository: PlaybackHistoryRepository,
        val privateVideoSession: PrivateVideoSession,
        @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val privateVault = MutableStateFlow(false)
        private val selectedId = savedStateHandle.getStateFlow<String?>(SELECTED_ID_KEY, null)
        private val query = savedStateHandle.getStateFlow(QUERY_KEY, "")
        private val viewMode = savedStateHandle.getStateFlow(VIEW_MODE_KEY, LibraryViewMode.GRID.name)
        private val sourceFilter = savedStateHandle.getStateFlow(SOURCE_FILTER_KEY, LibrarySourceFilter.ALL.name)
        private val favoritesOnly = savedStateHandle.getStateFlow(FAVORITES_ONLY_KEY, false)
        private val recentlyPlayedOnly = savedStateHandle.getStateFlow(RECENTLY_PLAYED_ONLY_KEY, false)
        private val selection = MutableStateFlow(LibrarySelectionState())
        private var deletingSelection = false
        private val displayPreferences =
            combine(
                viewMode,
                sourceFilter,
                favoritesOnly,
                recentlyPlayedOnly,
            ) { viewMode, sourceFilter, favoritesOnly, recentlyPlayedOnly ->
                LibraryDisplayPreferences(
                    viewMode = LibraryViewMode.entries.firstOrNull { it.name == viewMode } ?: LibraryViewMode.GRID,
                    sourceFilter = LibrarySourceFilter.entries.firstOrNull { it.name == sourceFilter } ?: LibrarySourceFilter.ALL,
                    favoritesOnly = favoritesOnly,
                    recentlyPlayedOnly = recentlyPlayedOnly,
                )
            }
        private val libraryDownloads =
            combine(repository.completedDownloads, repository.deletionPendingDownloads) { completed, pending ->
                (
                    completed.filterNot(DownloadTask::deletePending) +
                        pending.filter { it.status == DownloadStatus.COMPLETED }
                ).distinctBy(DownloadTask::id)
            }
        private val scopedDownloads =
            combine(
                libraryDownloads,
                repository.privateCompletedDownloads,
                privateVault,
                privateVideoSession.isUnlocked,
            ) {
                publicVideos,
                privateVideos,
                privateScope,
                unlocked,
                ->
                LibrarySnapshot(
                    if (privateScope) {
                        if (unlocked) privateVideos.filter(DownloadTask::isPrivate) else emptyList()
                    } else {
                        publicVideos.filterNot(DownloadTask::isPrivate)
                    },
                    isPrivateVault = privateScope,
                )
            }

        val settings: StateFlow<AppSettings> =
            settingsRepository.settings.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = AppSettings(),
            )

        private val libraryWithHistory =
            combine(
                scopedDownloads,
                playbackHistoryRepository.observeHistory().distinctUntilChanged { old, new ->
                    old.size == new.size &&
                        old.all { (id, item) -> new[id]?.lastPlayedAtMillis == item.lastPlayedAtMillis }
                },
            ) { tasks, history -> tasks to history }

        private val mappedUiState =
            combine(
                libraryWithHistory,
                selectedId,
                query,
                displayPreferences,
                selection,
            ) { tasks, selectedId, query, preferences, selection ->
                toLibraryUiState(
                    tasks = tasks.first,
                    playbackHistory = tasks.second,
                    selectedId = selectedId,
                    query = query,
                    viewMode = preferences.viewMode,
                    sourceFilter = preferences.sourceFilter,
                    selection = selection,
                    favoritesOnly = preferences.favoritesOnly,
                    recentlyPlayedOnly = preferences.recentlyPlayedOnly,
                ).copy(isPrivateVault = tasks.first.isPrivateVault)
            }.flowOn(defaultDispatcher)
        val uiState: StateFlow<LibraryUiState> =
            combine(mappedUiState, privateVault, privateVideoSession.isUnlocked) { state, privateScope, unlocked ->
                if ((privateScope && !unlocked) || state.isPrivateVault != privateScope) {
                    LibraryUiState(isPrivateVault = privateScope)
                } else {
                    state
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = LibraryUiState(),
            )

        fun setPrivateVault(enabled: Boolean) {
            privateVault.value = enabled
        }

        fun openPrivateVault() {
            viewModelScope.launch { navigation.dispatch(NavigationEvent.Navigate(PrivateLibraryKey)) }
        }

        fun lockPrivateVault() {
            privateVideoSession.lock()
            selection.value = LibrarySelectionState()
            savedStateHandle[SELECTED_ID_KEY] = null
        }

        fun leavePrivateVault() {
            goBack()
        }

        fun changePrivacy(
            ids: Set<String>,
            isPrivate: Boolean,
        ) {
            if (!privateVideoSession.isUnlocked.value || ids.isEmpty()) return
            viewModelScope.launch {
                suspendRunCatching { repository.setPrivate(ids, isPrivate) }.fold(
                    onSuccess = {
                        selection.value = LibrarySelectionState()
                        savedStateHandle[SELECTED_ID_KEY] = null
                        showMessage(if (isPrivate) R.string.vault_move_success else R.string.vault_restore_success)
                    },
                    onFailure = { showMessage(R.string.vault_move_failure) },
                )
            }
        }

        fun observeVideo(id: String) =
            repository
                .observeDownload(id)
                .combine(privateVideoSession.isUnlocked) { task, unlocked ->
                    task?.takeIf { !it.isPrivate || unlocked }
                }.asVideoDetailState()

        fun openVideo(
            id: String,
            replaceDetail: Boolean = false,
        ) {
            val task = uiState.value.videos.firstOrNull { it.id == id }
            if (task?.deletePending != false || task.localFilePath == null) return
            savedStateHandle[SELECTED_ID_KEY] = id
            viewModelScope.launch {
                val key = VideoDetailKey(id)
                navigation.dispatch(if (replaceDetail) NavigationEvent.Replace(key) else NavigationEvent.Navigate(key))
            }
        }

        fun compressVideo(task: DownloadTask) {
            if (task.isPrivate) return
            if (task.deletePending || task.localFilePath == null || task.status != DownloadStatus.COMPLETED) return
            viewModelScope.launch { navigation.dispatch(NavigationEvent.Navigate(VideoCompressionKey(task.id))) }
        }

        fun translateVideo(task: DownloadTask) {
            if (task.isPrivate) return
            if (task.deletePending || task.localFilePath == null || task.status != DownloadStatus.COMPLETED) return
            viewModelScope.launch { navigation.dispatch(NavigationEvent.Navigate(VideoSubtitleKey(task.id))) }
        }

        fun trimVideo(task: DownloadTask) {
            if (task.isPrivate || task.deletePending || task.localFilePath == null) return
            viewModelScope.launch { navigation.dispatch(NavigationEvent.Navigate(VideoTrimKey(task.id))) }
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

        fun setFavoritesOnly(enabled: Boolean) {
            savedStateHandle[FAVORITES_ONLY_KEY] = enabled
        }

        fun setRecentlyPlayedOnly(enabled: Boolean) {
            savedStateHandle[RECENTLY_PLAYED_ONLY_KEY] = enabled
        }

        fun toggleFavorite(task: DownloadTask) {
            if (task.deletePending || task.status != DownloadStatus.COMPLETED || task.localFilePath == null) return
            viewModelScope.launch {
                suspendRunCatching { repository.toggleFavorite(task.id) }
                    .onFailure { showMessage(R.string.library_favorite_failure) }
            }
        }

        fun toggleSelection(id: String) {
            if (deletingSelection) return
            selection.update { it.toggle(id) }
        }

        fun selectAllVisible(ids: List<String> = uiState.value.videos.map(DownloadTask::id)) {
            if (deletingSelection) return
            selection.update { it.selectAll(ids) }
        }

        fun clearSelection() {
            if (deletingSelection) return
            selection.update(LibrarySelectionState::clear)
        }

        fun deleteSelected() {
            val ids = uiState.value.selectedIds
            if (ids.isEmpty() || deletingSelection) return
            deletingSelection = true
            viewModelScope.launch {
                try {
                    var deletedCount = 0
                    ids.forEach { id ->
                        if (suspendRunCatching { repository.delete(id) }.isSuccess) {
                            deletedCount++
                            if (selectedId.value == id) savedStateHandle[SELECTED_ID_KEY] = null
                            selection.update { it.removeDeleted(id) }
                        }
                    }
                    if (deletedCount == ids.size) {
                        showMessage(R.string.library_delete_selected_success, ids.size)
                    } else {
                        showMessage(R.string.library_delete_selected_partial, deletedCount, ids.size - deletedCount)
                    }
                } finally {
                    deletingSelection = false
                }
            }
        }

        fun rename(
            task: DownloadTask,
            title: String,
        ) {
            if (task.deletePending || task.localFilePath == null) return
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

        suspend fun findExportTask(id: String?): DownloadTask? {
            val task =
                suspendRunCatching {
                    id
                        ?.let { repository.observeDownload(it).first() }
                        ?.takeIf { !it.isPrivate && !it.deletePending && it.localFilePath != null }
                }.getOrNull()
            if (task == null) showMessage(R.string.library_export_failure)
            return task
        }

        fun exportRestored(
            id: String?,
            destination: Uri,
        ) {
            viewModelScope.launch {
                val task = findExportTask(id)
                if (task == null) {
                    fileManager.discardExport(destination)
                } else {
                    export(task, destination)
                }
            }
        }

        fun export(
            task: DownloadTask,
            destination: Uri,
        ) {
            if (task.isPrivate || task.deletePending || task.localFilePath == null) return
            viewModelScope.launch {
                suspendRunCatching { fileManager.export(task, destination) }
                    .notifyResult(R.string.library_export_success, R.string.library_export_failure)
            }
        }

        suspend fun hasSubtitles(task: DownloadTask): Boolean = fileManager.hasSubtitles(task)

        fun exportWithSubtitles(task: DownloadTask) {
            if (task.isPrivate || task.deletePending || task.localFilePath == null) return
            viewModelScope.launch {
                navigation.dispatch(NavigationEvent.Navigate(VideoSubtitleExportKey(task.id)))
            }
        }

        fun exportToConfiguredLocation(task: DownloadTask) {
            if (task.isPrivate || task.deletePending || task.localFilePath == null) return
            viewModelScope.launch {
                val treeUri =
                    settingsRepository.settings
                        .first()
                        .downloadTreeUri
                        ?.let(Uri::parse)
                if (treeUri != null && !fileManager.hasPersistedTreePermission(treeUri)) {
                    clearInvalidDownloadLocation()
                    return@launch
                }
                suspendRunCatching { fileManager.exportToConfiguredLocation(task, treeUri) }
                    .fold(
                        onSuccess = { showMessage(R.string.library_export_success) },
                        onFailure = {
                            if (treeUri != null && !fileManager.hasPersistedTreePermission(treeUri)) {
                                clearInvalidDownloadLocation()
                            } else {
                                showMessage(R.string.library_export_failure)
                            }
                        },
                    )
            }
        }

        suspend fun hasValidConfiguredDownloadLocation(treeUri: String?): Boolean =
            treeUri?.let(Uri::parse)?.let { fileManager.hasPersistedTreePermission(it) } ?: true

        fun clearInvalidDownloadLocation() {
            viewModelScope.launch {
                settingsRepository.setDownloadLocation(enabled = false, treeUri = null)
                showMessage(R.string.library_export_location_permission_expired)
            }
        }

        suspend fun createShareIntent(task: DownloadTask): Intent? =
            task
                .takeIf { !it.isPrivate && !it.deletePending && it.localFilePath != null }
                ?.let { fileManager.createShareIntent(it) }

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
    val favoritesOnly: Boolean,
    val recentlyPlayedOnly: Boolean,
)

private const val SELECTED_ID_KEY = "library-selected-video-id"
private const val QUERY_KEY = "library-search-query"
private const val VIEW_MODE_KEY = "library-view-mode"
private const val SOURCE_FILTER_KEY = "library-source-filter"
private const val FAVORITES_ONLY_KEY = "library-favorites-only"
private const val RECENTLY_PLAYED_ONLY_KEY = "library-recently-played-only"
private const val STOP_TIMEOUT_MILLIS = 5_000L
