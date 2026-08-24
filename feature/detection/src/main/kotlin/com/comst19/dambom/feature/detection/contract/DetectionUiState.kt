package com.comst19.dambom.feature.detection.contract

import com.comst19.dambom.core.domain.model.MediaCandidate
import com.comst19.dambom.core.domain.model.UnsupportedReason

internal sealed interface DetectionUiState {
    data object Loading : DetectionUiState

    data object NetworkUnavailable : DetectionUiState

    data class Content(
        val pageTitle: String,
        val candidates: List<MediaCandidate>,
        val selectedIds: Set<String>,
        val isSubmitting: Boolean = false,
        val enqueueFailed: Boolean = false,
    ) : DetectionUiState

    data class Unsupported(
        val reason: UnsupportedReason,
    ) : DetectionUiState
}
