package com.comst19.dambom.feature.web.webview

import android.os.Looper
import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.feature.web.contract.WebTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en")
class WebViewHostLifecycleTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `stopping and restarting the host pauses and resumes the current WebView`() {
        val owner =
            object : LifecycleOwner {
                override val lifecycle = LifecycleRegistry(this)
            }
        var webView: WebView? = null
        composeRule.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                DambomTheme {
                    val failure = remember { mutableStateOf<WebNavigationFailure?>(null) }
                    Column {
                        WebContent(
                            tab = WebTab(id = 1L),
                            navigationFailureState = failure,
                            savedState = null,
                            onWebViewReady = { webView = it },
                            onPageStarted = { _, _, _, _ -> },
                            onPageFinished = { _, _, _, _ -> },
                            onPageChanged = { _, _, _ -> },
                            onMediaRequest = { _, _, _ -> },
                            onSaveWebState = { _, _ -> },
                            onScan = {},
                            onOpenDetectedMedia = {},
                            onOpenExternal = {},
                        )
                    }
                }
            }
        }
        composeRule.runOnIdle {
            owner.lifecycle.currentState = Lifecycle.State.CREATED
            assertTrue(shadowOf(requireNotNull(webView)).wasOnPauseCalled())
            owner.lifecycle.currentState = Lifecycle.State.RESUMED
            assertTrue(shadowOf(requireNotNull(webView)).wasOnResumeCalled())
        }
    }

    @Test
    fun `renderer loss replaces only the failed WebView`() {
        val readyEvents = mutableListOf<WebView?>()

        composeRule.setContent {
            DambomTheme {
                val navigationFailureState = remember { mutableStateOf<WebNavigationFailure?>(null) }
                Column {
                    WebContent(
                        tab = WebTab(id = 1L),
                        navigationFailureState = navigationFailureState,
                        savedState = null,
                        onWebViewReady = readyEvents::add,
                        onPageStarted = { _, _, _, _ -> },
                        onPageFinished = { _, _, _, _ -> },
                        onPageChanged = { _, _, _ -> },
                        onMediaRequest = { _, _, _ -> },
                        onSaveWebState = { _, _ -> },
                        onScan = {},
                        onOpenDetectedMedia = {},
                        onOpenExternal = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        val failedWebView = readyEvents.filterNotNull().single()

        composeRule.runOnIdle {
            failedWebView.webViewClient.onRenderProcessGone(failedWebView, null)
        }
        shadowOf(Looper.getMainLooper()).idle()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            readyEvents.count { it != null } == 2
        }

        val replacementWebView = readyEvents.filterNotNull().last()
        assertNotSame(failedWebView, replacementWebView)
        assertEquals(1, readyEvents.count { it == null })
        assertSame(replacementWebView, readyEvents.last())
    }
}
