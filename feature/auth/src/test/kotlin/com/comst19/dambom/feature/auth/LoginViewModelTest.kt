package com.comst19.dambom.feature.auth

import com.comst19.dambom.core.navigation.NavigationEvent
import com.comst19.dambom.core.navigation.contract.HomeGraph.HomeKey
import com.comst19.dambom.core.testing.MainDispatcherRule
import com.comst19.dambom.core.testing.SpyNavigationDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `login resets navigation to home`() =
        runTest(mainDispatcherRule.dispatcher) {
            val navigation = SpyNavigationDispatcher()
            val viewModel = LoginViewModel(navigation)

            viewModel.login()
            advanceUntilIdle()

            assertEquals(listOf(NavigationEvent.SetRoot(HomeKey)), navigation.dispatched)
        }
}
