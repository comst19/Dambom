package com.comst19.dambom.feature.library.contract

import androidx.compose.runtime.Immutable
import com.comst19.dambom.core.domain.model.DownloadTask
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

@Immutable
internal data class LibraryUiState(
    val videos: PersistentList<DownloadTask> = persistentListOf(),
    val selectedVideo: DownloadTask? = null,
    val query: String = "",
    val hasVideos: Boolean = false,
    val viewMode: LibraryViewMode = LibraryViewMode.GRID,
    val isSelecting: Boolean = false,
    val selectedIds: PersistentSet<String> = persistentSetOf(),
    val totalBytes: Long = 0L,
    val totalVideoCount: Int = 0,
    val sourceFilter: LibrarySourceFilter = LibrarySourceFilter.ALL,
)

internal enum class LibraryViewMode {
    GRID,
    LIST,
}

internal enum class LibrarySourceFilter {
    ALL,
    X,
    WEB,
}
