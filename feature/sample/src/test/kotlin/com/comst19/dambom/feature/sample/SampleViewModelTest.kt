package com.comst19.dambom.feature.sample

import com.comst19.dambom.core.common.ui.SnackbarEvent
import com.comst19.dambom.core.common.ui.SnackbarEventBus
import com.comst19.dambom.core.common.ui.UiText
import com.comst19.dambom.core.domain.error.AppErrorCode
import com.comst19.dambom.core.domain.error.AppRequestException
import com.comst19.dambom.core.domain.usecase.ObserveSampleUseCase
import com.comst19.dambom.core.domain.usecase.ObserveSamplesUseCase
import com.comst19.dambom.core.domain.usecase.RefreshSamplesUseCase
import com.comst19.dambom.core.navigation.NavigationEvent
import com.comst19.dambom.core.navigation.contract.HomeGraph.SampleDetailKey
import com.comst19.dambom.core.navigation.contract.SampleMatchingGraph.SampleMatchingDetailKey
import com.comst19.dambom.core.navigation.contract.SampleMatchingGraph.SampleMatchingProfileEditKey
import com.comst19.dambom.core.navigation.contract.SampleProfileGraph.SampleProfileEditKey
import com.comst19.dambom.core.navigation.contract.SampleProfileGraph.SampleProfileKey
import com.comst19.dambom.core.testfixture.FakeSampleRepository
import com.comst19.dambom.core.testfixture.sampleFixture
import com.comst19.dambom.core.testing.MainDispatcherRule
import com.comst19.dambom.core.testing.SpyNavigationDispatcher
import com.comst19.dambom.feature.sample.contract.SampleIntent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SampleViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `navigation demo supports both profile edit back paths`() =
        runTest(mainDispatcherRule.dispatcher) {
            val navigation = SpyNavigationDispatcher()
            val viewModel = SampleNavigationDemoViewModel(navigation)

            viewModel.openMatchingDetail()
            viewModel.openProfileEditReturningToMatching()
            viewModel.openProfileEditThroughProfile()
            advanceUntilIdle()

            assertEquals(
                listOf(
                    NavigationEvent.Navigate(SampleMatchingDetailKey),
                    NavigationEvent.Navigate(SampleMatchingProfileEditKey),
                    NavigationEvent.NavigateDeepLink(
                        topLevelKey = SampleProfileKey,
                        backStack = listOf(SampleProfileKey, SampleProfileEditKey),
                    ),
                ),
                navigation.dispatched,
            )
        }

    @Test
    fun `detail observes the item identified by its assisted navigation key`() =
        runTest(mainDispatcherRule.dispatcher) {
            val expected = sampleFixture(id = 7)
            val viewModel =
                SampleDetailViewModel(
                    observeSample = ObserveSampleUseCase(FakeSampleRepository(listOf(expected))),
                    key = SampleDetailKey(expected.id),
                )

            val state = viewModel.state.first { it.title != null }

            assertEquals(expected.id, state.id)
            assertEquals(expected.title, state.title)
            assertEquals(expected.description, state.description)
        }

    @Test
    fun `mvvm loads shared domain data and emits navigation`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeSampleRepository(listOf(sampleFixture()))
            val navigation = SpyNavigationDispatcher()
            val viewModel =
                SampleMvvmViewModel(
                    ObserveSamplesUseCase(repository),
                    RefreshSamplesUseCase(repository),
                    navigation,
                )

            advanceUntilIdle()
            assertFalse(viewModel.state.value.isLoading)
            assertEquals(
                1L,
                viewModel.state.value.items
                    .single()
                    .id,
            )
            viewModel.onItemClick(1)
            advanceUntilIdle()
            assertEquals(NavigationEvent.Navigate(SampleDetailKey(1)), navigation.dispatched.single())
        }

    @Test
    fun `mvi reduces state and emits navigation`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeSampleRepository(listOf(sampleFixture(id = 2)))
            val navigation = SpyNavigationDispatcher()
            val viewModel =
                SampleMviViewModel(
                    observeSamples = ObserveSamplesUseCase(repository),
                    refreshSamples = RefreshSamplesUseCase(repository),
                    snackbarEventBus = SnackbarEventBus(),
                    navigation = navigation,
                )

            advanceUntilIdle()
            assertEquals(
                2L,
                viewModel.state.value.items
                    .single()
                    .id,
            )
            viewModel.onIntent(SampleIntent.ClickItem(2))
            advanceUntilIdle()
            assertEquals(NavigationEvent.Navigate(SampleDetailKey(2)), navigation.dispatched.single())
        }

    @Test
    fun `mvvm ignores refresh while one is running`() =
        runTest(mainDispatcherRule.dispatcher) {
            val refreshGate = CompletableDeferred<Unit>()
            val repository = FakeSampleRepository().apply { onRefresh = { refreshGate.await() } }
            val viewModel =
                SampleMvvmViewModel(
                    ObserveSamplesUseCase(repository),
                    RefreshSamplesUseCase(repository),
                    SpyNavigationDispatcher(),
                )

            runCurrent()
            viewModel.refresh()
            runCurrent()

            assertEquals(1, repository.refreshCallCount)
            refreshGate.complete(Unit)
            advanceUntilIdle()
        }

    @Test
    fun `mvi ignores refresh while one is running`() =
        runTest(mainDispatcherRule.dispatcher) {
            val refreshGate = CompletableDeferred<Unit>()
            val repository = FakeSampleRepository().apply { onRefresh = { refreshGate.await() } }
            val viewModel =
                SampleMviViewModel(
                    observeSamples = ObserveSamplesUseCase(repository),
                    refreshSamples = RefreshSamplesUseCase(repository),
                    snackbarEventBus = SnackbarEventBus(),
                    navigation = SpyNavigationDispatcher(),
                )

            runCurrent()
            viewModel.onIntent(SampleIntent.Refresh)
            runCurrent()

            assertEquals(1, repository.refreshCallCount)
            refreshGate.complete(Unit)
            advanceUntilIdle()
        }

    @Test
    fun `mvvm converts refresh failure to ui state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeSampleRepository()
            repository.refreshFailure = networkFailure()

            val viewModel =
                SampleMvvmViewModel(
                    ObserveSamplesUseCase(repository),
                    RefreshSamplesUseCase(repository),
                    SpyNavigationDispatcher(),
                )

            advanceUntilIdle()

            assertFalse(viewModel.state.value.isLoading)
            assertEquals("Refresh failed", viewModel.state.value.errorMessage)
        }

    @Test
    fun `mvi converts refresh failure to ui state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeSampleRepository()
            repository.refreshFailure = networkFailure()
            val snackbarEventBus = SnackbarEventBus()

            val viewModel =
                SampleMviViewModel(
                    observeSamples = ObserveSamplesUseCase(repository),
                    refreshSamples = RefreshSamplesUseCase(repository),
                    snackbarEventBus = snackbarEventBus,
                    navigation = SpyNavigationDispatcher(),
                )

            advanceUntilIdle()

            assertFalse(viewModel.state.value.isLoading)
            assertEquals("Refresh failed", viewModel.state.value.errorMessage)
            assertEquals(
                SnackbarEvent(UiText.Dynamic("Refresh failed")),
                snackbarEventBus.events.first(),
            )
        }
}

private fun networkFailure(): AppRequestException =
    AppRequestException(
        statusCode = 401,
        errorCode = AppErrorCode.TOKEN_EXPIRED,
        rawErrorCode = "TOKEN_EXPIRED",
        message = "Token expired",
        cause = IOException(),
    )
