package com.comst19.dambom.feature.library.subtitle.inference

import com.comst19.dambom.feature.library.subtitle.model.SubtitleCue
import com.comst19.dambom.feature.library.subtitle.model.SubtitleLanguagePair

internal enum class SubtitleModelStatus { CHECKING, DOWNLOADABLE, DOWNLOADING, AVAILABLE, UNAVAILABLE, FAILED }

internal interface SubtitleEngine {
    suspend fun deleteModel()

    suspend fun status(pair: SubtitleLanguagePair): SubtitleModelStatus

    suspend fun download(
        pair: SubtitleLanguagePair,
        onBytes: (Long) -> Unit,
    ): SubtitleModelStatus

    suspend fun generate(
        path: String,
        pair: SubtitleLanguagePair,
        onProgress: (Long) -> Unit,
    ): List<SubtitleCue>
}
