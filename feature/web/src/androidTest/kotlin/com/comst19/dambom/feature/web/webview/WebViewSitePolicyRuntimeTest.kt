package com.comst19.dambom.feature.web.webview

import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.comst19.dambom.core.domain.model.MediaResourceRequest
import com.comst19.dambom.feature.web.contract.WebTab
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class WebViewSitePolicyRuntimeTest {
    @Test
    fun desktopNavigationAndNormalNavigationUseTheCurrentBrowserAgent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val requests = CopyOnWriteArrayList<MediaResourceRequest>()
        val target = AtomicReference("")
        val observedAgent = AtomicReference("")
        val ready = AtomicReference(CountDownLatch(1))
        lateinit var view: WebView
        lateinit var defaultAgent: String
        instrumentation.runOnMainSync {
            view =
                createWebView(
                    context = instrumentation.targetContext,
                    tab = WebTab(1L),
                    savedState = null,
                    onPageStarted = { _, _, _, _ -> },
                    onPageFinished = { _, _, _, _ -> },
                    onPageChanged = { _, _, _ -> },
                    onMediaRequest = { _, _, request -> requests.add(request) },
                    onProgress = {},
                    onNavigationFailure = {},
                    onRendererGone = {},
                )
            defaultAgent = view.settings.userAgentString
            val delegate = view.webViewClient
            view.webViewClient =
                object : WebViewClient() {
                    override fun onPageStarted(
                        view: WebView,
                        url: String?,
                        favicon: Bitmap?,
                    ) {
                        delegate.onPageStarted(view, url, favicon)
                    }

                    override fun onPageFinished(
                        view: WebView,
                        url: String?,
                    ) {
                        delegate.onPageFinished(view, url)
                        if (url == target.get()) {
                            view.evaluateJavascript("navigator.userAgent") { value ->
                                observedAgent.set(JSONArray("[$value]").getString(0))
                                ready.get().countDown()
                            }
                        }
                    }

                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse {
                        delegate.shouldInterceptRequest(view, request)
                        return WebResourceResponse(
                            "text/html",
                            "UTF-8",
                            ByteArrayInputStream("<html><title>Local UA fixture</title></html>".toByteArray()),
                        )
                    }
                }
        }
        try {
            val desktopUrl = "https://www.viki.com/dambom-ua-fixture"
            target.set(desktopUrl)
            instrumentation.runOnMainSync { view.loadUrl(desktopUrl) }
            assertTrue("Desktop fixture did not finish", ready.get().await(15, TimeUnit.SECONDS))
            val desktopAgent = observedAgent.get()
            assertFalse(desktopAgent.contains("Mobile"))
            assertTrue(desktopAgent.contains("X11; Linux x86_64"))
            assertTrue(
                requests.any { request ->
                    request.headers.entries.any { it.key.equals("User-Agent", true) && it.value == desktopAgent }
                },
            )

            val normalUrl = "https://fixture.example/dambom-ua-fixture"
            ready.set(CountDownLatch(1))
            target.set(normalUrl)
            instrumentation.runOnMainSync { view.loadUrl(normalUrl) }
            assertTrue("Normal fixture did not finish", ready.get().await(15, TimeUnit.SECONDS))
            assertEquals(defaultAgent, observedAgent.get())
            assertTrue(
                requests.any { request ->
                    request.url == normalUrl &&
                        request.headers.entries.any {
                            it.key.equals("User-Agent", true) && it.value == defaultAgent
                        }
                },
            )
        } finally {
            instrumentation.runOnMainSync { view.destroy() }
        }
    }
}
