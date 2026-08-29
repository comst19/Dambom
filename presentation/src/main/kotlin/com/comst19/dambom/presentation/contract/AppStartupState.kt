package com.comst19.dambom.presentation.contract

import androidx.compose.runtime.Immutable
import com.comst19.dambom.core.navigation.contract.TopLevelNavKey

@Immutable
sealed interface AppStartupState {
    data object Initializing : AppStartupState

    @Immutable
    data class Ready(
        val startKey: TopLevelNavKey,
    ) : AppStartupState

    @Immutable
    data class Failed(
        val reason: StartupFailure,
    ) : AppStartupState
}

@Immutable
sealed interface StartupFailure {
    data object InitializationFailed : StartupFailure
}
