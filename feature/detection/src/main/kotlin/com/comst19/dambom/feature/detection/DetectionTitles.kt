package com.comst19.dambom.feature.detection

import com.comst19.dambom.core.domain.model.MediaCandidate
import com.comst19.dambom.feature.detection.contract.DetectionUiState
import kotlinx.collections.immutable.toPersistentList

internal fun MediaCandidate.displayTitle(
    fallback: String,
    index: Int,
    count: Int,
): String = title.ifBlank { if (count > 1) "$fallback · ${index + 1}" else fallback }

internal fun DetectionUiState.Content.withFallbackTitles(fallback: String): DetectionUiState.Content =
    copy(
        pageTitle = pageTitle.ifBlank { fallback },
        candidates =
            candidates
                .mapIndexed { index, candidate ->
                    candidate.copy(title = candidate.displayTitle(fallback, index, candidates.size))
                }.toPersistentList(),
    )
