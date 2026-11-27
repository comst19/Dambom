package com.comst19.dambom.feature.web.webview

import android.webkit.RecordingSslErrorHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en")
class WebNavigationCallbackGatingTest {
    @Test
    fun `rejected stale onPageStarted before new page start does not emit callbacks or change ua`() {
        val firstUrl = "https://www.youtube.com/watch?v=stale"
        val secondUrl = "https://second.example/page"
        val harness = createWebViewHarness(initialUrl = firstUrl)
        try {
            harness.webView.loadUrl(secondUrl)
            val userAgentForSecond = harness.webView.settings.userAgentString
            harness.webView.webViewClient.onPageStarted(harness.webView, firstUrl, null)

            assertTrue(harness.startedEvents.isEmpty())
            assertEquals(0, harness.historyChangeCount)
            assertEquals(userAgentForSecond, harness.webView.settings.userAgentString)

            harness.webView.webViewClient.onPageStarted(harness.webView, secondUrl, null)
            assertEquals(1, harness.startedEvents.size)
            assertEquals(secondUrl, harness.startedEvents.single().first)
            assertEquals(1, harness.historyChangeCount)
        } finally {
            harness.webView.destroy()
        }
    }

    @Test
    fun `late onPageStarted from previous navigation after new page started is ignored by factory and tracker`() {
        val firstUrl = "https://first.example/page"
        val secondUrl = "https://second.example/page"
        val harness = createWebViewHarness(initialUrl = firstUrl)
        try {
            harness.webView.loadUrl(secondUrl)
            harness.webView.webViewClient.onPageStarted(harness.webView, secondUrl, null)
            val startedAfterSecond = harness.startedEvents.toList()
            val historyAfterSecond = harness.historyChangeCount

            harness.webView.webViewClient.onPageStarted(harness.webView, firstUrl, null)

            assertEquals(startedAfterSecond, harness.startedEvents)
            assertEquals(historyAfterSecond, harness.historyChangeCount)

            val staleSsl = RecordingSslErrorHandler()
            harness.webView.webViewClient.onReceivedSslError(
                harness.webView,
                staleSsl,
                sslError(firstUrl),
            )
            assertEquals(1, staleSsl.cancelCount)
            assertNull(harness.currentFailure)

            harness.webView.webViewClient.onReceivedHttpError(
                harness.webView,
                FakeWebResourceRequest(secondUrl, isForMainFrame = true),
                httpResponse(500),
            )
            assertEquals(WebNavigationFailure.HTTP_SERVER, harness.currentFailure)
        } finally {
            harness.webView.destroy()
        }
    }

    @Test
    fun `late onPageFinished from previous navigation after new page started is ignored and keeps attribution`() {
        val firstUrl = "https://first.example/page"
        val secondUrl = "https://second.example/page"
        val harness = createWebViewHarness(initialUrl = firstUrl)
        try {
            harness.webView.loadUrl(secondUrl)
            harness.webView.webViewClient.onPageStarted(harness.webView, secondUrl, null)
            val historyAfterSecondStart = harness.historyChangeCount

            harness.webView.webViewClient.onPageFinished(harness.webView, firstUrl)

            assertTrue(harness.finishedEvents.isEmpty())
            assertEquals(historyAfterSecondStart, harness.historyChangeCount)

            harness.webView.webViewClient.onReceivedHttpError(
                harness.webView,
                FakeWebResourceRequest(secondUrl, isForMainFrame = true),
                httpResponse(500),
            )
            assertEquals(WebNavigationFailure.HTTP_SERVER, harness.currentFailure)

            harness.webView.webViewClient.onPageFinished(harness.webView, secondUrl)
            assertEquals(1, harness.finishedEvents.size)
            assertEquals(secondUrl, harness.finishedEvents.single().first)
        } finally {
            harness.webView.destroy()
        }
    }

    @Test
    fun `in load same origin replaceState updates finish url while cross origin history update is ignored`() {
        val initialUrl = "https://site.example/start"
        val replacedUrl = "https://site.example/replaced"
        val crossOriginUrl = "https://other.example/stale"
        val harness = createWebViewHarness(initialUrl = initialUrl)
        try {
            harness.webView.webViewClient.onPageStarted(harness.webView, initialUrl, null)
            harness.webView.webViewClient.doUpdateVisitedHistory(harness.webView, crossOriginUrl, false)
            harness.webView.webViewClient.onPageFinished(harness.webView, crossOriginUrl)
            assertTrue(harness.finishedEvents.isEmpty())

            harness.webView.webViewClient.doUpdateVisitedHistory(harness.webView, replacedUrl, false)
            harness.webView.webViewClient.onPageFinished(harness.webView, replacedUrl)

            assertEquals(1, harness.finishedEvents.size)
            assertEquals(replacedUrl, harness.finishedEvents.single().first)
        } finally {
            harness.webView.destroy()
        }
    }

    @Test
    fun `in load replaceState back to previous visited url still delivers onPageFinished`() {
        val urlA = "https://site.example/a"
        val urlB = "https://site.example/b"
        val harness = createWebViewHarness(initialUrl = urlA)
        try {
            harness.webView.webViewClient.onPageStarted(harness.webView, urlA, null)
            harness.webView.webViewClient.onPageFinished(harness.webView, urlA)
            assertEquals(1, harness.finishedEvents.size)

            harness.webView.loadUrl(urlB)
            harness.webView.webViewClient.onPageStarted(harness.webView, urlB, null)
            harness.webView.webViewClient.doUpdateVisitedHistory(harness.webView, urlA, false)
            harness.webView.webViewClient.onPageFinished(harness.webView, urlA)

            assertEquals(2, harness.finishedEvents.size)
            assertEquals(urlA, harness.finishedEvents.last().first)
        } finally {
            harness.webView.destroy()
        }
    }

    @Test
    fun `replaceState to interrupted url delivers finish while stale finish without history update is ignored`() {
        val urlA = "https://site.example/a"
        val urlB = "https://site.example/b"
        val harness = createWebViewHarness(initialUrl = urlA)
        try {
            harness.webView.webViewClient.onPageStarted(harness.webView, urlA, null)
            harness.webView.loadUrl(urlB)
            harness.webView.webViewClient.onPageStarted(harness.webView, urlB, null)

            harness.webView.webViewClient.onPageFinished(harness.webView, urlA)
            assertTrue(harness.finishedEvents.isEmpty())

            harness.webView.webViewClient.doUpdateVisitedHistory(harness.webView, urlA, false)
            harness.webView.webViewClient.onPageFinished(harness.webView, urlA)
            assertEquals(1, harness.finishedEvents.size)
            assertEquals(urlA, harness.finishedEvents.single().first)
        } finally {
            harness.webView.destroy()
        }
    }
}
