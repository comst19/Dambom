package com.comst19.dambom.feature.library.subtitle

import androidx.compose.runtime.Immutable
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.feature.library.subtitle.inference.SubtitleModelStatus
import com.comst19.dambom.feature.library.subtitle.model.SubtitleCue
import com.comst19.dambom.feature.library.subtitle.model.SubtitleLanguagePair
import com.comst19.dambom.feature.library.subtitle.work.SubtitleJob
import com.comst19.dambom.feature.library.subtitle.work.SubtitleModelDownload
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

internal enum class SubtitleOperation { IDLE, CHECKING, DOWNLOADING, GENERATING, EXPORTING, DELETING }

@Immutable
internal data class SubtitleState(
    val task: DownloadTask? = null,
    val pair: SubtitleLanguagePair = SubtitleLanguagePair.ENGLISH_KOREAN,
    val model: SubtitleModelStatus = SubtitleModelStatus.CHECKING,
    val localOperation: SubtitleOperation = SubtitleOperation.CHECKING,
    val modelDownload: SubtitleModelDownload? = null,
    val processedMillis: Long = 0,
    val cues: ImmutableList<SubtitleCue> = persistentListOf(),
    val generation: SubtitleJob? = null,
    val failed: Boolean = false,
    val speechPermissionDenied: Boolean = false,
    val loadFailed: Boolean = false,
    val exported: Boolean = false,
) {
    val operation: SubtitleOperation
        get() =
            when {
                generation?.active == true -> SubtitleOperation.GENERATING
                modelDownload?.active == true -> SubtitleOperation.DOWNLOADING
                else -> localOperation
            }

    val busy: Boolean get() = operation != SubtitleOperation.IDLE && operation != SubtitleOperation.CHECKING
}
