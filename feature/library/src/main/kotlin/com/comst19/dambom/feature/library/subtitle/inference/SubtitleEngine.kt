package com.comst19.dambom.feature.library.subtitle.inference

import com.comst19.dambom.feature.library.subtitle.model.ModelDownloadProgress
import com.comst19.dambom.feature.library.subtitle.model.SubtitleCue
import com.comst19.dambom.feature.library.subtitle.model.SubtitleLanguagePair

internal enum class SubtitleModelStatus {
    CHECKING,
    DOWNLOADABLE,
    PREPARATION_REQUIRED,
    DOWNLOADING,
    AVAILABLE,
    UNAVAILABLE,
    FAILED,
}

internal interface SubtitleEngine {
    fun cachedStatus(pair: SubtitleLanguagePair): SubtitleModelStatus? = null

    suspend fun deleteModel()

    suspend fun status(
        pair: SubtitleLanguagePair,
        forceRefresh: Boolean = false,
    ): SubtitleModelStatus

    suspend fun download(
        pair: SubtitleLanguagePair,
        onBytes: (ModelDownloadProgress) -> Unit,
    ): SubtitleModelStatus

    suspend fun generate(
        path: String,
        pair: SubtitleLanguagePair,
        onProgress: (Long) -> Unit,
    ): List<SubtitleCue>
}
