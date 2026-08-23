package com.comst19.dambom.feature.sample.contract

import com.comst19.dambom.core.common.ui.UiIntent

internal sealed interface SampleIntent : UiIntent {
    data object Refresh : SampleIntent

    data class ClickItem(
        val id: Long,
    ) : SampleIntent
}
