package com.comst19.dambom.presentation.contract

import com.comst19.dambom.core.navigation.TopLevelNavKey

sealed interface AppStartupState {
    data object Initializing : AppStartupState

    data class Ready(
        val startKey: TopLevelNavKey,
    ) : AppStartupState

    data class Failed(
        val reason: StartupFailure,
    ) : AppStartupState
}

sealed interface StartupFailure {
    data object InitializationFailed : StartupFailure
}
