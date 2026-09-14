package com.comst19.dambom.feature.web

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.feature.web.component.WebAddressBar
import com.comst19.dambom.feature.web.component.WebNavigationErrorContent
import com.comst19.dambom.feature.web.component.WebToolbar
import com.comst19.dambom.feature.web.contract.WebDetectionState
import com.comst19.dambom.feature.web.contract.WebTab
import com.comst19.dambom.feature.web.webview.WebNavigationFailure
import com.comst19.dambom.feature.web.webview.createWebView
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en")
class WebNavigationErrorContentTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `access failure has no retry action`() {
        showError(WebNavigationFailure.HTTP_CLIENT)

        composeRule.onNodeWithText("Page access failed").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").assertDoesNotExist()
        composeRule.onNodeWithText("Open in default browser").assertIsDisplayed()
    }

    @Test
    fun `timeout failure offers retry`() {
        showError(WebNavigationFailure.TIMEOUT)
        composeRule.onNodeWithText("The site took too long to respond").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").assertIsDisplayed()
    }

    @Test
    fun `server failure offers retry`() {
        showError(WebNavigationFailure.HTTP_SERVER)
        composeRule.onNodeWithText("The site is temporarily unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").assertIsDisplayed()
    }

    @Test
    fun `security failure offers no bypass action`() {
        showError(WebNavigationFailure.SECURITY)

        composeRule.onNodeWithText("The site could not be opened securely").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").assertDoesNotExist()
        composeRule.onNodeWithText("Open in default browser").assertDoesNotExist()
    }

    @Test
    fun `security state disables the address menu`() {
        composeRule.setContent {
            DambomTheme {
                WebAddressBar(
                    address = "https://expired.badssl.com",
                    tabCount = 1,
                    onAddressChange = {},
                    onSubmit = {},
                    onBack = {},
                    onOpenTabs = {},
                    currentUrl = null,
                    onOpenExternal = {},
                    onCopyLink = {},
                    onShareLink = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Current tab actions").assertIsNotEnabled()
        composeRule.onNodeWithText("Open in default browser").assertDoesNotExist()
    }

    @Test
    fun `non retryable failure disables toolbar refresh`() {
        val webView = WebView(ApplicationProvider.getApplicationContext())
        composeRule.setContent {
            DambomTheme {
                WebToolbar(
                    webView = webView,
                    canRefresh = false,
                    detectionState = WebDetectionState.Idle,
                    onOpenDetectedMedia = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Refresh").assertIsNotEnabled()
        webView.destroy()
    }

    @Test
    fun `retryable failure keeps toolbar refresh enabled`() {
        val webView = WebView(ApplicationProvider.getApplicationContext())
        composeRule.setContent {
            DambomTheme {
                WebToolbar(
                    webView = webView,
                    canRefresh = true,
                    detectionState = WebDetectionState.Idle,
                    onOpenDetectedMedia = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Refresh").assertIsEnabled()
        webView.destroy()
    }

    @Test
    fun `http failure reported before page start remains visible`() {
        var failure: WebNavigationFailure? = null
        val context = ApplicationProvider.getApplicationContext<Context>()
        val webView =
            createWebView(
                context = context,
                tab = WebTab(id = 1L),
                savedState = null,
                onPageStarted = { _, _, _, _ -> },
                onPageFinished = { _, _, _, _ -> },
                onPageChanged = { _, _, _ -> },
                onMediaRequest = { _, _, _ -> },
                onProgress = {},
                onNavigationFailure = { failure = it },
                onRendererGone = {},
            )
        val url = Uri.parse("https://example.com/status/500")

        webView.webViewClient.onReceivedHttpError(
            webView,
            MainFrameRequest(url),
            WebResourceResponse(
                "text/plain",
                "UTF-8",
                500,
                "Server error",
                emptyMap(),
                ByteArrayInputStream(byteArrayOf()),
            ),
        )
        webView.webViewClient.onPageStarted(webView, url.toString(), null)

        assertEquals(WebNavigationFailure.HTTP_SERVER, failure)
        webView.destroy()
    }

    private fun showError(failure: WebNavigationFailure) {
        composeRule.setContent {
            DambomTheme {
                WebNavigationErrorContent(
                    failure = failure,
                    onRetry = {},
                    onOpenExternal = {},
                )
            }
        }
    }
}

private class MainFrameRequest(
    private val requestUrl: Uri,
) : WebResourceRequest {
    override fun getUrl(): Uri = requestUrl

    override fun isForMainFrame(): Boolean = true

    override fun isRedirect(): Boolean = false

    override fun hasGesture(): Boolean = false

    override fun getMethod(): String = "GET"

    override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
}
