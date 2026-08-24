package com.comst19.dambom.presentation

import com.comst19.dambom.core.common.ui.SnackbarEventBus
import com.comst19.dambom.core.domain.error.ErrorHandler
import com.comst19.dambom.core.domain.model.AppSettings
import com.comst19.dambom.core.domain.model.ThemeMode
import com.comst19.dambom.core.domain.repository.SettingsRepository
import com.comst19.dambom.core.navigation.TopLevelNavKey
import com.comst19.dambom.core.navigation.contract.HomeGraph.HomeKey
import com.comst19.dambom.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `startup is ready with the coordinator destination`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel =
                MainViewModel(
                    FakeSettingsRepository(),
                    SuccessfulStartupCoordinator,
                    ErrorHandler(),
                    SnackbarEventBus(),
                )

            advanceUntilIdle()

            assertEquals(AppStartupState.Ready(HomeKey), viewModel.startupState.value)
        }

    @Test
    fun `startup failure clears the initializing state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel =
                MainViewModel(
                    FakeSettingsRepository(),
                    FailingStartupCoordinator,
                    ErrorHandler(),
                    SnackbarEventBus(),
                )

            advanceUntilIdle()

            assertEquals(AppStartupState.Failed(StartupFailure.InitializationFailed), viewModel.startupState.value)
        }
}

private object SuccessfulStartupCoordinator : StartupCoordinator {
    override suspend fun initialize(): TopLevelNavKey = HomeKey
}

private object FailingStartupCoordinator : StartupCoordinator {
    override suspend fun initialize(): TopLevelNavKey = error("Initialization failed")
}

private class FakeSettingsRepository : SettingsRepository {
    override val settings: Flow<AppSettings> = flowOf(AppSettings())

    override suspend fun setThemeMode(mode: ThemeMode) = Unit

    override suspend fun setClipboardSuggestion(
        promptShown: Boolean,
        enabled: Boolean,
    ) = Unit

    override suspend fun setWifiOnlyDownloads(enabled: Boolean) = Unit
}
