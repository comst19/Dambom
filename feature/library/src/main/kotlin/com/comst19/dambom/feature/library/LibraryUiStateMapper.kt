package com.comst19.dambom.feature.library

import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.feature.library.contract.LibrarySourceFilter
import com.comst19.dambom.feature.library.contract.LibraryUiState
import com.comst19.dambom.feature.library.contract.LibraryViewMode
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentSet

internal fun toLibraryUiState(
    tasks: List<DownloadTask>,
    selectedId: String?,
    query: String = "",
    viewMode: LibraryViewMode = LibraryViewMode.GRID,
    sourceFilter: LibrarySourceFilter = LibrarySourceFilter.ALL,
    selection: LibrarySelectionState = LibrarySelectionState(),
): LibraryUiState {
    val completedTasks =
        tasks.filter { task ->
            task.status == DownloadStatus.COMPLETED && task.localFilePath != null
        }
    val savedVideos = completedTasks.sortedByDescending(DownloadTask::updatedAtMillis)
    val sourceVideos =
        savedVideos.filter { task ->
            when (sourceFilter) {
                LibrarySourceFilter.ALL -> true
                LibrarySourceFilter.X -> videoSourcePresentation(task.sourcePageUrl).kind == VideoSourceKind.X
                LibrarySourceFilter.WEB -> videoSourcePresentation(task.sourcePageUrl).kind == VideoSourceKind.WEBSITE
            }
        }
    val trimmedQuery = query.trim()
    val videos =
        if (trimmedQuery.isEmpty()) {
            sourceVideos
        } else {
            sourceVideos.filter { it.title.contains(trimmedQuery, ignoreCase = true) }
        }
    val savedIds = savedVideos.mapTo(hashSetOf(), DownloadTask::id)
    return LibraryUiState(
        videos = videos.toPersistentList(),
        selectedVideo = savedVideos.firstOrNull { it.id == selectedId },
        query = query,
        hasVideos = savedVideos.isNotEmpty(),
        viewMode = viewMode,
        isSelecting = selection.isActive,
        selectedIds = selection.selectedIds.filterTo(linkedSetOf()) { it in savedIds }.toPersistentSet(),
        totalBytes = savedVideos.sumOf(DownloadTask::downloadedBytes),
        totalVideoCount = savedVideos.size,
        sourceFilter = sourceFilter,
    )
}
