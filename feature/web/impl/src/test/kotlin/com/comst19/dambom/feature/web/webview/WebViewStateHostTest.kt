package com.comst19.dambom.feature.web.webview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.core.domain.model.MediaDetectionResult
import com.comst19.dambom.core.domain.model.UnsupportedReason
import com.comst19.dambom.core.domain.repository.MediaDetectionRepository
import com.comst19.dambom.core.navigation.NavigationDispatcher
import com.comst19.dambom.core.navigation.NavigationEvent
import com.comst19.dambom.feature.web.R
import com.comst19.dambom.feature.web.WebRoute
import com.comst19.dambom.feature.web.WebViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WebViewStateHostTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `retains at most three bundles in last-saved order without promoting on read`() {
        val host = WebViewStateHost()
        val bundle0 = Bundle()
        val bundle1 = Bundle()
        val bundle2 = Bundle()
        val bundle3 = Bundle()
        val bundle1Updated = Bundle()
        val bundle4 = Bundle()

        host.saveWebState(0L, bundle0)
        host.saveWebState(1L, bundle1)
        host.saveWebState(2L, bundle2)

        assertSame(bundle0, host.savedWebState(0L))

        host.saveWebState(3L, bundle3)
        assertNull(host.savedWebState(0L))
        assertSame(bundle1, host.savedWebState(1L))
        assertSame(bundle2, host.savedWebState(2L))
        assertSame(bundle3, host.savedWebState(3L))

        host.saveWebState(1L, bundle1Updated)
        host.saveWebState(4L, bundle4)
        assertNull(host.savedWebState(2L))
        assertSame(bundle3, host.savedWebState(3L))
        assertSame(bundle1Updated, host.savedWebState(1L))
        assertSame(bundle4, host.savedWebState(4L))
    }

    @Test
    fun `clearing host view model store releases all retained bundles`() {
        val host = WebViewStateHost()
        val store = ViewModelStore()
        val factory = viewModelFactory { initializer { host } }
        ViewModelProvider.create(store, factory)[WebViewStateHost::class]

        host.saveWebState(1L, Bundle())
        host.saveWebState(2L, Bundle())
        assertNotNull(host.savedWebState(1L))

        store.clear()

        assertNull(host.savedWebState(1L))
        assertNull(host.savedWebState(2L))
    }

    @Test
    fun `closing active tab in WebRoute does not resurrect closed tab or evict active tab caches`() {
        val viewModel = createViewModel()
        val stateHost = WebViewStateHost()
        val tab1Id = viewModel.uiState.value.currentTabId
        viewModel.navigateCurrentTab("https://example.com/1")
        viewModel.updatePage(tab1Id, "https://example.com/1", "Active Tab 1")
        viewModel.createTab("https://example.com/2")
        val tab2Id = viewModel.uiState.value.currentTabId
        viewModel.createTab("https://example.com/3")
        val tab3Id = viewModel.uiState.value.currentTabId
        viewModel.createTab("https://example.com/4")
        val tab4Id = viewModel.uiState.value.currentTabId
        viewModel.selectTab(tab1Id)

        val bundle2 = Bundle()
        val bundle3 = Bundle()
        val bundle4 = Bundle()
        stateHost.saveWebState(tab2Id, bundle2)
        stateHost.saveWebState(tab3Id, bundle3)
        stateHost.saveWebState(tab4Id, bundle4)

        composeRule.setContent {
            DambomTheme {
                WebRoute(
                    initialUrl = null,
                    viewModel = viewModel,
                    stateHost = stateHost,
                )
            }
        }
        composeRule.waitForIdle()

        val tabsButtonDesc =
            composeRule.activity.resources.getQuantityString(R.plurals.web_tabs_title, 4, 4)
        composeRule.onNodeWithContentDescription(tabsButtonDesc).performClick()
        composeRule.waitForIdle()

        val closeTab1Desc = composeRule.activity.getString(R.string.web_close_tab, "Active Tab 1")
        composeRule.onNodeWithContentDescription(closeTab1Desc).performClick()
        composeRule.waitForIdle()

        assertNull(stateHost.savedWebState(tab1Id))
        assertSame(bundle2, stateHost.savedWebState(tab2Id))
        assertSame(bundle3, stateHost.savedWebState(tab3Id))
        assertSame(bundle4, stateHost.savedWebState(tab4Id))
    }

    @Test
    fun `closing last remaining tab in WebRoute does not resurrect closed tab on disposal`() {
        val viewModel = createViewModel()
        val stateHost = WebViewStateHost()
        val onlyTabId = viewModel.uiState.value.currentTabId
        viewModel.navigateCurrentTab("https://example.com/only")
        viewModel.updatePage(onlyTabId, "https://example.com/only", "Only Tab")

        composeRule.setContent {
            DambomTheme {
                WebRoute(
                    initialUrl = null,
                    viewModel = viewModel,
                    stateHost = stateHost,
                )
            }
        }
        composeRule.waitForIdle()

        val tabsButtonDesc =
            composeRule.activity.resources.getQuantityString(R.plurals.web_tabs_title, 1, 1)
        composeRule.onNodeWithContentDescription(tabsButtonDesc).performClick()
        composeRule.waitForIdle()

        val closeOnlyDesc = composeRule.activity.getString(R.string.web_close_tab, "Only Tab")
        composeRule.onNodeWithContentDescription(closeOnlyDesc).performClick()
        composeRule.waitForIdle()

        assertNull(stateHost.savedWebState(onlyTabId))
    }

    private fun createViewModel(): WebViewModel =
        WebViewModel(
            mediaDetectionRepository = NoOpStateHostMediaDetectionRepository,
            navigation = NoOpStateHostNavigationDispatcher,
            savedStateHandle = SavedStateHandle(),
        )
}

private object NoOpStateHostMediaDetectionRepository : MediaDetectionRepository {
    override suspend fun detect(url: String): MediaDetectionResult = noMedia()

    override suspend fun detectRequest(
        url: String,
        headers: Map<String, String>,
    ): MediaDetectionResult = noMedia()

    private fun noMedia(): MediaDetectionResult = MediaDetectionResult.Unsupported(UnsupportedReason.NO_MEDIA)
}

private object NoOpStateHostNavigationDispatcher : NavigationDispatcher {
    override val events: Flow<NavigationEvent> = emptyFlow()

    override suspend fun dispatch(event: NavigationEvent) = Unit
}
