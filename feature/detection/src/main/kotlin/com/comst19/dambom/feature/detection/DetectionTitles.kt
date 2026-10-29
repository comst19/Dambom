package com.comst19.dambom.feature.detection

import com.comst19.dambom.core.common.net.readableMediaTitle
import com.comst19.dambom.core.domain.model.MediaCandidate
import com.comst19.dambom.feature.detection.contract.DetectionUiState
import kotlinx.collections.immutable.toPersistentList

internal fun MediaCandidate.displayTitle(
    fallback: String,
    index: Int,
    count: Int,
    pageTitle: String = "",
): String {
    val page = pageTitle.readableMediaTitle()
    val label = title.readableMediaTitle() ?: page ?: fallback
    return if (count > 1 && (label == page || label == fallback)) "$label · ${index + 1}" else label
}

internal fun DetectionUiState.Content.withFallbackTitles(fallback: String): DetectionUiState.Content =
    copy(
        pageTitle = pageTitle.readableMediaTitle() ?: fallback,
        candidates =
            candidates
                .mapIndexed { index, candidate ->
                    candidate.copy(title = candidate.displayTitle(fallback, index, candidates.size, pageTitle))
                }.toPersistentList(),
    )
