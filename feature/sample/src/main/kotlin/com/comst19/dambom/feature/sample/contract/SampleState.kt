package com.comst19.dambom.feature.sample.contract

import androidx.compose.runtime.Immutable
import com.comst19.dambom.core.common.ui.UiState
import com.comst19.dambom.feature.sample.SampleUiModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
internal data class SampleState(
    val isLoading: Boolean = true,
    val items: ImmutableList<SampleUiModel> = persistentListOf(),
    val errorMessage: String? = null,
) : UiState
