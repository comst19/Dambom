package com.comst19.dambom.feature.web.webview

import android.os.Looper
import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.feature.web.contract.WebTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
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
