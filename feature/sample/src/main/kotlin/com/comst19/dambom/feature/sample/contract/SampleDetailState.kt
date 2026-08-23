package com.comst19.dambom.feature.sample.contract

import androidx.compose.runtime.Immutable
import com.comst19.dambom.core.common.ui.UiState

@Immutable
internal data class SampleDetailState(
    val id: Long,
    val title: String? = null,
    val description: String? = null,
) : UiState
