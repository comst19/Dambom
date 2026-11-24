package com.comst19.dambom.feature.library.trim.contract

import com.comst19.dambom.core.domain.model.DownloadTask

internal data class VideoTrimState(
    val task: DownloadTask? = null,
    val durationMillis: Long = 0L,
    val selection: TrimSelection = TrimSelection(0L, 0L),
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val exporting: Boolean = false,
    val progress: Int? = null,
    val exportFailed: Boolean = false,
    val savedUri: String? = null,
)
