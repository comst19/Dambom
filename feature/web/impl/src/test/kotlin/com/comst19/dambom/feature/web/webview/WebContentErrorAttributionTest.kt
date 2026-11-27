package com.comst19.dambom.feature.web.webview

import android.webkit.RecordingSslErrorHandler
import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.feature.web.contract.WebTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en")
class WebContentErrorAttributionTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `web content keeps page visible on subresource ssl error and shows security error on main frame ssl error`() {
        var readyWebView: WebView? = null
        composeRule.setContent {
            DambomTheme {
                val failureState = remember { mutableStateOf<WebNavigationFailure?>(null) }
                Column(modifier = Modifier.fillMaxSize()) {
                    WebContent(
                        tab = WebTab(id = 1L, url = "https://good.example/page"),
                        navigationFailureState = failureState,
                        savedState = null,
                        callbacks =
                            WebContentCallbacks(
                                onWebViewReady = { readyWebView = it },
                                onPageStarted = { _, _, _, _ -> },
                                onPageFinished = { _, _, _, _ -> },
                                onPageChanged = { _, _, _ -> },
                                onMediaRequest = { _, _, _ -> },
                                onSaveWebState = { _, _ -> },
                                onScan = {},
                                onOpenDetectedMedia = {},
                                onOpenExternal = {},
                            ),
                    )
                }
            }
        }
        composeRule.waitForIdle()
        val webView = requireNotNull(readyWebView)

        val subresourceSsl = RecordingSslErrorHandler()
        composeRule.runOnIdle {
            webView.webViewClient.onPageStarted(webView, "https://good.example/page", null)
            webView.webViewClient.onReceivedSslError(
                webView,
                subresourceSsl,
                sslError("https://bad-subresource.example/tracker.js"),
            )
            webView.webViewClient.onPageFinished(webView, "https://good.example/page")
        }
        composeRule.waitForIdle()
        assertEquals(1, subresourceSsl.cancelCount)
        composeRule.onNodeWithText("The site could not be opened securely").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Scan again").assertIsDisplayed()

        val mainSsl = RecordingSslErrorHandler()
        composeRule.runOnIdle {
            webView.loadUrl("https://expired.badssl.com")
            webView.webViewClient.onReceivedSslError(
                webView,
                mainSsl,
                sslError("https://expired.badssl.com/"),
            )
        }
        composeRule.waitForIdle()
        assertEquals(1, mainSsl.cancelCount)
        composeRule.onNodeWithText("The site could not be opened securely").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Refresh").assertIsNotEnabled()
    }

    @Test
    fun `web content toolbar refresh clears http server failure on same url reload`() {
        var readyWebView: WebView? = null
        val url = "https://flaky.example/page"
        composeRule.setContent {
            DambomTheme {
                val failureState = remember { mutableStateOf<WebNavigationFailure?>(null) }
                Column(modifier = Modifier.fillMaxSize()) {
                    WebContent(
                        tab = WebTab(id = 1L, url = url),
                        navigationFailureState = failureState,
                        savedState = null,
                        callbacks =
                            WebContentCallbacks(
                                onWebViewReady = { readyWebView = it },
                                onPageStarted = { _, _, _, _ -> },
                                onPageFinished = { _, _, _, _ -> },
                                onPageChanged = { _, _, _ -> },
                                onMediaRequest = { _, _, _ -> },
                                onSaveWebState = { _, _ -> },
                                onScan = {},
                                onOpenDetectedMedia = {},
                                onOpenExternal = {},
                            ),
                    )
                }
            }
        }
        composeRule.waitForIdle()
        val webView = requireNotNull(readyWebView)

        composeRule.runOnIdle {
            webView.webViewClient.onReceivedHttpError(
                webView,
                FakeWebResourceRequest(url, isForMainFrame = true),
                httpResponse(500),
            )
            webView.webViewClient.onPageStarted(webView, url, null)
            webView.webViewClient.onPageFinished(webView, url)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("The site is temporarily unavailable").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Refresh").performClick()
        composeRule.runOnIdle {
            webView.webViewClient.onPageStarted(webView, url, null)
            webView.webViewClient.onPageFinished(webView, url)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("The site is temporarily unavailable").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Scan again").assertIsDisplayed()
        assertTrue(webView.url == url || webView.url == null)
    }
}
