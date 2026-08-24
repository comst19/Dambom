package com.comst19.dambom.feature.library.contract

import com.comst19.dambom.core.domain.model.DownloadTask

internal data class LibraryUiState(
    val videos: List<DownloadTask> = emptyList(),
    val selectedVideo: DownloadTask? = null,
    val query: String = "",
    val hasVideos: Boolean = false,
)
