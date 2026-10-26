package com.comst19.dambom.feature.web.webview

import android.content.Context
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.feature.web.contract.WebTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WebViewSiteUserAgentTest {
    @Test
    fun `navigation switches desktop agent then restores the original agent`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val starts = mutableListOf<String?>()
        val view =
            createWebView(
                context = context,
                tab = WebTab(id = 1L),
                savedState = null,
                onPageStarted = { _, url, _, _ -> starts.add(url) },
                onPageFinished = { _, _, _, _ -> },
                onPageChanged = { _, _, _ -> },
                onMediaRequest = { _, _, _ -> },
                onProgress = {},
                onNavigationFailure = {},
                onRendererGone = {},
            )
        try {
            val original = view.settings.userAgentString
            val desktop = "https://www.viki.com/watch"
            view.webViewClient.onPageStarted(view, desktop, null)
            val desktopAgent = view.settings.userAgentString
            assertFalse(desktopAgent.contains("Mobile"))
            assertNull(shadowOf(view).lastLoadedUrl)
            assertEquals(listOf(desktop), starts)
            val ordinary = "https://example.org/watch"
            view.webViewClient.onPageStarted(view, ordinary, null)
            assertEquals(original, view.settings.userAgentString)
            assertNull(shadowOf(view).lastLoadedUrl)
            assertEquals(listOf(desktop, ordinary), starts)
        } finally {
            view.destroy()
        }
    }
}
