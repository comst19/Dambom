package com.comst19.dambom.feature.library

import com.comst19.dambom.feature.library.contract.LibrarySourceFilter
import com.comst19.dambom.feature.library.contract.LibraryUiState
import com.comst19.dambom.feature.library.contract.LibraryViewMode
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentSet

@Suppress("LongParameterList")
internal fun toLibraryUiState(
    tasks: LibrarySnapshot,
    selectedId: String?,
    query: String = "",
    viewMode: LibraryViewMode = LibraryViewMode.GRID,
    sourceFilter: LibrarySourceFilter = LibrarySourceFilter.ALL,
    selection: LibrarySelectionState = LibrarySelectionState(),
): LibraryUiState {
    val savedVideos = tasks.videos
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
    return LibraryUiState(
        videos = videos.toPersistentList(),
        selectedVideo = savedVideos.firstOrNull { it.id == selectedId },
        query = query,
        hasVideos = savedVideos.isNotEmpty(),
        viewMode = viewMode,
        isSelecting = selection.isActive,
        selectedIds = selection.selectedIds.filterTo(linkedSetOf()) { it in tasks.ids }.toPersistentSet(),
        totalBytes = tasks.totalBytes,
        totalVideoCount = savedVideos.size,
        sourceFilter = sourceFilter,
    )
}
