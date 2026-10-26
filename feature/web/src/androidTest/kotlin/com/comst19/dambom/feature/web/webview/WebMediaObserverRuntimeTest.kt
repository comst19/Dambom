package com.comst19.dambom.feature.web.webview

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.comst19.dambom.core.domain.model.MediaResourceRequest
import com.comst19.dambom.feature.web.contract.WebTab
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class WebMediaObserverRuntimeTest {
    @Test
    fun posterUpdatesReachNativeWithoutPlayback() {
        val baseUrl = InstrumentationRegistry.getArguments().getString("streamFixtureBaseUrl")
        assumeTrue(baseUrl != null)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val found = CopyOnWriteArrayList<MediaResourceRequest>()
        val ready = CountDownLatch(4)
        val completed = mutableSetOf<String>()
        lateinit var view: WebView
        instrumentation.runOnMainSync {
            view = WebView(instrumentation.targetContext)
            view.settings.javaScriptEnabled = true
            installWebMediaObserver(view, 1L, AtomicLong(7L)) { _, _, request ->
                found.add(request)
                val expected =
                    (request.thumbnailUrl == "$baseUrl/late.jpg" && request.title == "Updated video title") ||
                        (request.url == "$baseUrl/invalid.mp4" && request.thumbnailUrl == null && request.title == "Valid video") ||
                        (request.url == "$baseUrl/nested.mp4" && request.title == "Nested source") ||
                        (request.url == "$baseUrl/bare.mp4" && request.title == null)
                if (expected && completed.add(request.url)) ready.countDown()
            }
            view.webViewClient =
                object : WebViewClient() {
                    override fun onPageFinished(
                        view: WebView,
                        url: String?,
                    ) {
                        view.evaluateJavascript(
                            """
                            $WEB_MEDIA_OBSERVER_SCRIPT
                            const video = document.createElement('video');
                            video.preload = 'none';
                            video.src = '/source.mp4';
                            video.poster = '/first.jpg';
                            video.title = 'First video title';
                            document.body.append(video);
                            setTimeout(() => { video.poster = '/late.jpg'; video.title = 'Updated video title'; }, 50);
                            const invalidPoster = document.createElement('video');
                            invalidPoster.preload = 'none';
                            invalidPoster.src = '/invalid.mp4';
                            invalidPoster.poster = 'https://[';
                            invalidPoster.title = 'Valid video';
                            document.body.append(invalidPoster);
                            const bare = document.createElement('video');
                            bare.preload = 'none';
                            bare.src = '/bare.mp4';
                            document.body.append(bare);
                            const nested = document.createElement('video');
                            nested.preload = 'none';
                            nested.setAttribute('aria-label', 'Nested source');
                            document.body.append(nested);
                            setTimeout(() => {
                              const source = document.createElement('source');
                              source.src = '/nested.mp4';
                              nested.append(source);
                            }, 10);
                            """.trimIndent(),
                            null,
                        )
                    }
                }
            view.loadUrl("$baseUrl/poster.html")
        }
        try {
            assertTrue("Observed: $found", ready.await(10, TimeUnit.SECONDS))
            assertTrue(found.any { it.url == "$baseUrl/source.mp4" && it.thumbnailUrl?.endsWith("first.jpg") == true })
            assertTrue(found.any { it.title == "First video title" })
            val paused = AtomicReference("")
            val checked = CountDownLatch(1)
            instrumentation.runOnMainSync {
                view.evaluateJavascript("Array.from(document.querySelectorAll('video')).every(video => video.paused)") {
                    paused.set(it)
                    checked.countDown()
                }
            }
            assertTrue(checked.await(5, TimeUnit.SECONDS))
            org.junit.Assert.assertEquals("true", paused.get())
        } finally {
            instrumentation.runOnMainSync { view.destroy() }
        }
    }

    @Test
    fun reusedXhrDoesNotReportPostResponsesAsGetMedia() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val ready = CountDownLatch(1)
        val observed = AtomicReference("")
        lateinit var view: WebView
        instrumentation.runOnMainSync {
            view = WebView(instrumentation.targetContext)
            view.settings.javaScriptEnabled = true
            view.webViewClient =
                object : WebViewClient() {
                    override fun onPageFinished(
                        view: WebView,
                        url: String?,
                    ) {
                        view.evaluateJavascript("JSON.stringify(window.fixtureFound)") {
                            observed.set(it)
                            ready.countDown()
                        }
                    }
                }
            val html =
                """
                <script>
                window.fixtureFound = [];
                window.dambomMedia = {postMessage: (message) => fixtureFound.push(JSON.parse(message).url)};
                class FixtureXHR extends EventTarget {
                  open() {}
                  getResponseHeader() { return 'video/mp4'; }
                  complete(path) {
                    this.responseURL = new URL(path, location.href).href;
                    this.dispatchEvent(new Event('load'));
                  }
                }
                window.XMLHttpRequest = FixtureXHR;
                $WEB_MEDIA_OBSERVER_SCRIPT
                const request = new XMLHttpRequest();
                request.open('GET', '/unused.mp4');
                request.open('POST', '/post.mp4');
                request.complete('/post.mp4');
                request.open('GET', '/get.mp4');
                request.complete('/get.mp4');
                </script>
                """.trimIndent()
            view.loadDataWithBaseURL("https://fixture.example/", html, "text/html", "UTF-8", null)
        }
        try {
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            assertTrue(observed.get(), observed.get().contains("/get.mp4"))
            assertFalse(observed.get(), observed.get().contains("/post.mp4"))
        } finally {
            instrumentation.runOnMainSync { view.destroy() }
        }
    }

    @Test
    fun factoryAddsBrowserContextToDynamicMedia() {
        val baseUrl = InstrumentationRegistry.getArguments().getString("streamFixtureBaseUrl")
        assumeTrue(baseUrl != null)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val found = CopyOnWriteArrayList<MediaResourceRequest>()
        val ready = CountDownLatch(1)
        lateinit var view: WebView
        instrumentation.runOnMainSync {
            CookieManager.getInstance().setCookie(baseUrl, "fixture=session")
            view =
                createWebView(
                    context = instrumentation.targetContext,
                    tab = WebTab(1L),
                    savedState = null,
                    onPageStarted = { _, _, _, _ -> },
                    onPageFinished = { _, _, _, _ -> },
                    onPageChanged = { _, _, _ -> },
                    onMediaRequest = { _, _, request ->
                        if (request.url == "$baseUrl/video/playlist.m3u8") {
                            found.add(request)
                            ready.countDown()
                        }
                    },
                    onProgress = {},
                    onNavigationFailure = {},
                    onRendererGone = {},
                )
            view.loadUrl("$baseUrl/observer.html")
        }
        try {
            assertTrue(ready.await(15, TimeUnit.SECONDS))
            assertTrue(found.isNotEmpty())
            assertTrue(found.all { it.headers["Cookie"] == "fixture=session" })
            assertTrue(found.all { !it.headers["User-Agent"].isNullOrBlank() })
        } finally {
            instrumentation.runOnMainSync { view.destroy() }
        }
    }

    @Test
    fun dynamicDomAndFetchResponsesReachNativeWithoutRequestInterception() {
        val baseUrl = InstrumentationRegistry.getArguments().getString("streamFixtureBaseUrl")
        assumeTrue(baseUrl != null)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val found = CopyOnWriteArrayList<MediaResourceRequest>()
        val ready = CountDownLatch(2)
        lateinit var view: WebView
        instrumentation.runOnMainSync {
            CookieManager.getInstance().setCookie(baseUrl, "fixture=session")
            view = WebView(instrumentation.targetContext)
            view.settings.javaScriptEnabled = true
            installWebMediaObserver(view, 1L, AtomicLong(7L)) { _, _, request ->
                if (found.none { it.url == request.url }) {
                    found.add(request)
                    ready.countDown()
                }
            }
            view.loadUrl("$baseUrl/observer.html")
        }
        try {
            assertTrue("Observer results: ${found.map { it.url }}", ready.await(15, TimeUnit.SECONDS))
            assertTrue(found.any { it.url == "$baseUrl/video/playlist.m3u8" })
            assertTrue(found.any { it.url == "$baseUrl/source.mp4" })
        } finally {
            instrumentation.runOnMainSync { view.destroy() }
        }
    }
}
