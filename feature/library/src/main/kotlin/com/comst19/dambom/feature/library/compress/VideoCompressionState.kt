package com.comst19.dambom.feature.library.compress

import com.comst19.dambom.core.domain.model.DownloadTask

internal data class VideoCompressionState(
    val task: DownloadTask? = null,
    val source: CompressionSource? = null,
    val profile: CompressionProfile = CompressionProfile.BALANCED,
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val exporting: Boolean = false,
    val progress: Int? = null,
    val failure: CompressionFailure? = null,
    val savedUri: String? = null,
    val savedBytes: Long? = null,
)

internal enum class CompressionFailure { FAILED, NOT_SMALLER }
