package com.comst19.dambom.feature.detection.contract

import androidx.compose.runtime.Immutable
import com.comst19.dambom.core.domain.model.MediaCandidate
import com.comst19.dambom.core.domain.model.UnsupportedReason
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf

@Immutable
internal sealed interface DetectionUiState {
    data object Loading : DetectionUiState

    data object NetworkUnavailable : DetectionUiState

    @Immutable
    data class Content(
        val pageTitle: String,
        val candidates: PersistentList<MediaCandidate>,
        val selectedIds: PersistentSet<String>,
        val selectedVariantUrls: PersistentMap<String, String> = persistentMapOf(),
        val isSubmitting: Boolean = false,
        val enqueueFailed: Boolean = false,
    ) : DetectionUiState

    @Immutable
    data class Unsupported(
        val reason: UnsupportedReason,
    ) : DetectionUiState
}
