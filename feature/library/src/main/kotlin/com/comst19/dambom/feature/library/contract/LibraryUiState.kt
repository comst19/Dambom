package com.comst19.dambom.feature.library.contract

import androidx.compose.runtime.Immutable
import com.comst19.dambom.core.domain.model.DownloadTask
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

@Immutable
internal data class LibraryUiState(
    val videos: PersistentList<DownloadTask> = persistentListOf(),
    val selectedVideo: DownloadTask? = null,
    val query: String = "",
    val hasVideos: Boolean = false,
)
