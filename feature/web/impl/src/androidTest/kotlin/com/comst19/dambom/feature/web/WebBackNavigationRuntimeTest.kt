package com.comst19.dambom.feature.web

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.core.domain.model.MediaDetectionResult
import com.comst19.dambom.core.domain.model.UnsupportedReason
import com.comst19.dambom.core.domain.repository.MediaDetectionRepository
import com.comst19.dambom.core.domain.repository.MediaDetectionSnapshots
import com.comst19.dambom.core.navigation.NavigationDispatcher
import com.comst19.dambom.core.navigation.NavigationEvent
import com.comst19.dambom.feature.web.contract.WebDetectionState
import com.comst19.dambom.feature.web.contract.WebTab
import com.comst19.dambom.feature.web.contract.WebUiState
import com.comst19.dambom.feature.web.webview.WebViewStateHost
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class WebBackNavigationRuntimeTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var server: LocalSpaFixtureServer

    @Before
    fun setUp() {
        server = LocalSpaFixtureServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    @SuppressLint("VisibleForTests")
    fun pushStateSystemBackAndPredictiveBackFlow() {
        val parentBack = RuntimePredictiveBackCallback()
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.addCallback(composeRule.activity, parentBack)
        }
        val urlA = server.url("/a")
        val urlB = server.url("/b")
        val finishedUrls = CopyOnWriteArrayList<String>()
        val uiState =
            mutableStateOf(
                WebUiState(
                    tabs = persistentListOf(WebTab(id = 1L, url = urlA)),
                    currentTabId = 1L,
                ),
            )
        setWebScreenContent(uiState, mutableMapOf(), finishedUrls)

        composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
            finishedUrls.contains(urlA) && currentWebViewUrl() == urlA
        }
        composeRule.waitForIdle()
        backButton().assertIsNotEnabled()
        forwardButton().assertIsNotEnabled()

        // Tap the full-screen button on /a to run user-activated history.pushState({}, '', '/b').
        tapWebViewCenter()
        composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
            uiState.value.currentTab?.url == urlB && currentWebViewUrl() == urlB
        }
        composeRule.waitForIdle()
        backButton().assertIsEnabled()
        forwardButton().assertIsNotEnabled()
        assertEquals("pushState must not trigger a reload of /a", 1, server.requestCount("/a"))
        assertEquals("pushState must not fetch /b over the network", 0, server.requestCount("/b"))

        // First system Back returns from B -> A and enables Forward button.
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
            uiState.value.currentTab?.url == urlA && currentWebViewUrl() == urlA
        }
        composeRule.waitForIdle()
        backButton().assertIsNotEnabled()
        forwardButton().assertIsEnabled()
        assertEquals(0, parentBack.startedCount)
        assertEquals(0, parentBack.pressedCount)
        assertEquals("goBack to /a must not reload /a", 1, server.requestCount("/a"))

        // Second system Back starts predictive back; canceling stays on Page A.
        dispatchPredictiveBack(cancel = true)
        assertEquals(1, parentBack.startedCount)
        assertTrue(parentBack.progressedCount >= 1)
        assertEquals(1, parentBack.cancelledCount)
        assertEquals(0, parentBack.pressedCount)
        assertEquals(urlA, uiState.value.currentTab?.url)
        assertEquals(urlA, currentWebViewUrl())
        backButton().assertIsNotEnabled()
        forwardButton().assertIsEnabled()

        // Completing predictive back exits WebScreen to parent.
        dispatchPredictiveBack(cancel = false)
        assertEquals(1, parentBack.pressedCount)
    }

    @Test
    fun tabSwitchAndRestorePreservesHistoryState() {
        val urlA = server.url("/a")
        val urlB = server.url("/b")
        val urlC = server.url("/c")
        val savedStates = mutableMapOf<Long, Bundle>()
        val finishedUrls = CopyOnWriteArrayList<String>()
        val uiState =
            mutableStateOf(
                WebUiState(
                    tabs =
                        persistentListOf(
                            WebTab(id = 1L, url = urlA),
                            WebTab(id = 2L, url = urlC),
                        ),
                    currentTabId = 1L,
                ),
            )
        setWebScreenContent(uiState, savedStates, finishedUrls)

        composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
            finishedUrls.contains(urlA) && currentWebViewUrl() == urlA
        }
        val tab1InitialWebView = requireWebView()
        tapWebViewCenter()
        composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
            uiState.value.currentTab?.url == urlB && currentWebViewUrl() == urlB
        }
        composeRule.waitForIdle()
        backButton().assertIsEnabled()

        // Switch to Tab 2 (/c): Back must be disabled.
        composeRule.runOnUiThread { uiState.value = uiState.value.copy(currentTabId = 2L) }
        composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
            finishedUrls.contains(urlC) && currentWebViewUrl() == urlC
        }
        composeRule.waitForIdle()
        val tab2WebView = requireWebView()
        assertNotSame(tab1InitialWebView, tab2WebView)
        assertTrue(savedStates.containsKey(1L))
        backButton().assertIsNotEnabled()
        forwardButton().assertIsNotEnabled()

        // Switch back to Tab 1: saved history [A, B] must be restored with Back enabled.
        composeRule.runOnUiThread { uiState.value = uiState.value.copy(currentTabId = 1L) }
        composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
            val view = findWebViewOnUiThread()
            view != null && view !== tab2WebView && currentWebViewCanGoBack()
        }
        composeRule.waitForIdle()
        backButton().assertIsEnabled()
        forwardButton().assertIsNotEnabled()

        // System back on restored Tab 1 navigates back to /a and enables Forward.
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) { currentWebViewUrl() == urlA }
        composeRule.waitForIdle()
        backButton().assertIsNotEnabled()
        forwardButton().assertIsEnabled()
    }

    @Test
    fun pushStateWithRealViewModelDetectsNewMediaAndAllowsExplicitNavigation() {
        val urlA = server.url("/a")
        val urlB = server.url("/b")
        val urlC = server.url("/c")
        val viewModel =
            WebViewModel(
                mediaDetectionRepository = NoOpRuntimeMediaDetectionRepository,
                navigation = NoOpRuntimeNavigationDispatcher,
                savedStateHandle = SavedStateHandle(),
                snapshots = MediaDetectionSnapshots(),
            )
        viewModel.navigateCurrentTab(urlA)
        val finishedUrls = CopyOnWriteArrayList<String>()
        setViewModelWebScreenContent(viewModel, finishedUrls)

        composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
            finishedUrls.contains(urlA) && currentWebViewUrl() == urlA
        }

        // Real user tap triggers history.pushState -> /b without reloading /a.
        tapWebViewCenter()
        composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
            val currentTab = viewModel.uiState.value.currentTab
            currentTab?.url == urlB && currentWebViewUrl() == urlB
        }
        assertEquals(1, server.requestCount("/a"))

        // Trigger a new MP4 request on /b and verify real WebViewModel detects it under the new generation.
        composeRule.runOnUiThread {
            requireWebView().evaluateJavascript("fetch('/video-b.mp4');", null)
        }
        composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
            val currentTab = viewModel.uiState.value.currentTab
            currentTab?.detectionState == WebDetectionState.Found(1)
        }

        // Explicit navigation via ViewModel still loads /c in the active WebView.
        composeRule.runOnUiThread { viewModel.navigateCurrentTab(urlC) }
        composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
            finishedUrls.contains(urlC) && currentWebViewUrl() == urlC
        }
        assertEquals(1, server.requestCount("/c"))
    }

    @Test
    fun tabSwitchWithRealViewModelAndStateHostPreservesBackAndForwardHistory() {
        val urlA = server.url("/a")
        val urlB = server.url("/b")
        val urlC = server.url("/c")
        val urlD = server.url("/d")
        val viewModel =
            WebViewModel(
                mediaDetectionRepository = NoOpRuntimeMediaDetectionRepository,
                navigation = NoOpRuntimeNavigationDispatcher,
                savedStateHandle = SavedStateHandle(),
                snapshots = MediaDetectionSnapshots(),
            )
        val stateHost = WebViewStateHost()
        val tab1Id = viewModel.uiState.value.currentTabId
        viewModel.navigateCurrentTab(urlA)
        val finishedUrls = CopyOnWriteArrayList<String>()
        setViewModelWebScreenContent(viewModel, finishedUrls, stateHost)

        composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
            finishedUrls.contains(urlA) && currentWebViewUrl() == urlA
        }

        composeRule.runOnUiThread { viewModel.navigateCurrentTab(urlB) }
        composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
            finishedUrls.contains(urlB) && currentWebViewUrl() == urlB
        }

        composeRule.runOnUiThread { viewModel.navigateCurrentTab(urlC) }
        composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
            finishedUrls.contains(urlC) && currentWebViewUrl() == urlC
        }

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) { currentWebViewUrl() == urlB }
        composeRule.waitForIdle()
        backButton().assertIsEnabled()
        forwardButton().assertIsEnabled()

        val tab1BeforeSwitch = requireWebView()
        composeRule.runOnUiThread { viewModel.createTab(urlD) }
        composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
            finishedUrls.contains(urlD) && currentWebViewUrl() == urlD
        }
        composeRule.waitForIdle()
        val tab2WebView = requireWebView()
        assertNotSame(tab1BeforeSwitch, tab2WebView)
        assertTrue(stateHost.savedWebState(tab1Id) != null)

        composeRule.runOnUiThread { viewModel.selectTab(tab1Id) }
        composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
            val view = findWebViewOnUiThread()
            view != null && view !== tab2WebView && currentWebViewUrl() == urlB && currentWebViewCanGoBack()
        }
        composeRule.waitForIdle()
        backButton().assertIsEnabled()
        forwardButton().assertIsEnabled()

        composeRule.runOnUiThread { requireWebView().goBack() }
        composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) { currentWebViewUrl() == urlA }
        composeRule.waitForIdle()
        backButton().assertIsNotEnabled()
        forwardButton().assertIsEnabled()

        composeRule.runOnUiThread { requireWebView().goForward() }
        composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) { currentWebViewUrl() == urlB }
        composeRule.runOnUiThread { requireWebView().goForward() }
        composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) { currentWebViewUrl() == urlC }
        composeRule.waitForIdle()
        backButton().assertIsEnabled()
        forwardButton().assertIsNotEnabled()
    }

    private fun setViewModelWebScreenContent(
        viewModel: WebViewModel,
        finishedUrls: MutableList<String>,
        stateHost: WebViewStateHost = WebViewStateHost(),
    ) {
        composeRule.setContent {
            DambomTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                WebScreen(
                    uiState = uiState,
                    callbacks =
                        WebScreenCallbacks(
                            onBack = viewModel::goBack,
                            onNavigate = viewModel::navigateCurrentTab,
                            onPageStarted = viewModel::onPageStarted,
                            onPageFinished = { tabId, url, title, generation ->
                                if (url != null) finishedUrls.add(url)
                                viewModel.onPageFinished(tabId, url, title, generation)
                            },
                            onPageChanged = viewModel::updatePage,
                            onMediaRequest = viewModel::onMediaRequest,
                            onScan = viewModel::scanCurrentTab,
                            onOpenDetectedMedia = viewModel::openDetectedMedia,
                            onCreateTab = viewModel::createTab,
                            onSelectTab = viewModel::selectTab,
                            onCloseTab = { tabId ->
                                stateHost.removeWebState(tabId)
                                viewModel.closeTab(tabId)
                            },
                            savedWebState = stateHost::savedWebState,
                            onSaveWebState = { tabId, state ->
                                val openTabs = viewModel.uiState.value.tabs
                                if (openTabs.any { it.id == tabId }) {
                                    stateHost.saveWebState(tabId, state)
                                }
                            },
                            consumedNavigationRequestId = viewModel::consumedNavigationRequestId,
                            onNavigationRequestConsumed = viewModel::markNavigationRequestConsumed,
                        ),
                )
            }
        }
    }

    private fun setWebScreenContent(
        uiState: MutableState<WebUiState>,
        savedStates: MutableMap<Long, Bundle>,
        finishedUrls: MutableList<String>,
    ) {
        composeRule.setContent {
            DambomTheme {
                WebScreen(
                    uiState = uiState.value,
                    callbacks =
                        WebScreenCallbacks(
                            onPageStarted = { tabId, url, _, _ ->
                                if (url != null) uiState.value = uiState.value.replaceTabUrl(tabId, url)
                            },
                            onPageFinished = { tabId, url, _, _ ->
                                if (url != null) {
                                    finishedUrls.add(url)
                                    uiState.value = uiState.value.replaceTabUrl(tabId, url)
                                }
                            },
                            onPageChanged = { tabId, url, _ ->
                                if (url != null) uiState.value = uiState.value.replaceTabUrl(tabId, url)
                            },
                            savedWebState = savedStates::get,
                            onSaveWebState = { id, bundle -> savedStates[id] = bundle },
                        ),
                )
            }
        }
    }

    private fun tapWebViewCenter() {
        composeRule.runOnUiThread {
            val webView = requireWebView()
            val x = webView.width / 2f
            val y = webView.height / 2f
            val downTime = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
            val up = MotionEvent.obtain(downTime, downTime + 40L, MotionEvent.ACTION_UP, x, y, 0)
            webView.dispatchTouchEvent(down)
            webView.dispatchTouchEvent(up)
            down.recycle()
            up.recycle()
        }
    }

    @SuppressLint("VisibleForTests")
    private fun dispatchPredictiveBack(cancel: Boolean) {
        composeRule.runOnUiThread {
            val dispatcher = composeRule.activity.onBackPressedDispatcher
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(48f, 0f, 0.5f, BackEventCompat.EDGE_LEFT))
            if (cancel) {
                dispatcher.dispatchOnBackCancelled()
            } else {
                dispatcher.onBackPressed()
            }
        }
        composeRule.waitForIdle()
    }

    private fun backButton() =
        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.web_go_back),
        )

    private fun forwardButton() =
        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.web_go_forward),
        )

    private fun currentWebViewUrl(): String? {
        var url: String? = null
        composeRule.runOnUiThread { url = findWebView(composeRule.activity.window.decorView)?.url }
        return url
    }

    private fun currentWebViewCanGoBack(): Boolean {
        var canGoBack = false
        composeRule.runOnUiThread {
            canGoBack = findWebView(composeRule.activity.window.decorView)?.canGoBack() == true
        }
        return canGoBack
    }

    private fun findWebViewOnUiThread(): WebView? {
        var found: WebView? = null
        composeRule.runOnUiThread { found = findWebView(composeRule.activity.window.decorView) }
        return found
    }

    private fun requireWebView(): WebView = requireNotNull(findWebViewOnUiThread())

    private fun findWebView(view: View): WebView? =
        when (view) {
            is WebView -> view
            is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { findWebView(view.getChildAt(it)) }
            else -> null
        }

    companion object {
        private const val WAIT_TIMEOUT_MS = 15_000L
    }
}

private fun WebUiState.replaceTabUrl(
    tabId: Long,
    url: String,
): WebUiState {
    val index = tabs.indexOfFirst { it.id == tabId }
    if (index < 0) return this
    return copy(tabs = tabs.mutate { it[index] = it[index].copy(url = url) })
}

private class RuntimePredictiveBackCallback : OnBackPressedCallback(true) {
    var startedCount = 0
        private set
    var progressedCount = 0
        private set
    var cancelledCount = 0
        private set
    var pressedCount = 0
        private set

    override fun handleOnBackStarted(backEvent: BackEventCompat) {
        startedCount += 1
    }

    override fun handleOnBackProgressed(backEvent: BackEventCompat) {
        progressedCount += 1
    }

    override fun handleOnBackCancelled() {
        cancelledCount += 1
    }

    override fun handleOnBackPressed() {
        pressedCount += 1
    }
}

private class LocalSpaFixtureServer : Closeable {
    private val socket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
    private val requestCounts = ConcurrentHashMap<String, AtomicInteger>()

    @Volatile
    private var running = true

    private val thread =
        Thread {
            while (running) {
                val client =
                    try {
                        socket.accept()
                    } catch (_: SocketException) {
                        break
                    }
                try {
                    client.soTimeout = 3_000
                    val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                    val requestLine = reader.readLine().orEmpty()
                    val path = requestLine.split(' ').getOrNull(1).orEmpty()
                    while (true) {
                        val header = reader.readLine()
                        if (header.isNullOrEmpty()) break
                    }
                    if (path.isNotEmpty()) {
                        requestCounts.computeIfAbsent(path) { AtomicInteger(0) }.incrementAndGet()
                    }
                    val isVideo = path.endsWith(".mp4")
                    val contentType = if (isVideo) "video/mp4" else "text/html; charset=utf-8"
                    val bodyBytes =
                        if (isVideo) {
                            byteArrayOf(0, 0, 0, 0)
                        } else {
                            (
                                "<!doctype html><html><head><title>Page $path</title></head>" +
                                    "<body style='margin:0'>" +
                                    "<button id='go' style='position:fixed;inset:0;width:100%;height:100%' " +
                                    "onclick=\"history.pushState({path:'/b'},'','/b')\">Page $path</button>" +
                                    "</body></html>"
                            ).toByteArray(Charsets.UTF_8)
                        }
                    val response =
                        "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: $contentType\r\n" +
                            "Content-Length: ${bodyBytes.size}\r\n" +
                            "Connection: close\r\n\r\n"
                    client.getOutputStream().apply {
                        write(response.toByteArray(Charsets.US_ASCII))
                        write(bodyBytes)
                        flush()
                    }
                } catch (_: Exception) {
                    // Ignore transient client disconnects during teardown.
                } finally {
                    client.close()
                }
            }
        }

    fun start() {
        thread.isDaemon = true
        thread.start()
    }

    fun url(path: String): String = "http://127.0.0.1:${socket.localPort}$path"

    fun requestCount(path: String): Int = requestCounts[path]?.get() ?: 0

    override fun close() {
        running = false
        socket.close()
        thread.join(2_000)
    }
}

private object NoOpRuntimeMediaDetectionRepository : MediaDetectionRepository {
    override suspend fun detect(url: String): MediaDetectionResult = noMedia()

    override suspend fun detectRequest(
        url: String,
        headers: Map<String, String>,
    ): MediaDetectionResult = noMedia()

    private fun noMedia(): MediaDetectionResult = MediaDetectionResult.Unsupported(UnsupportedReason.NO_MEDIA)
}

private object NoOpRuntimeNavigationDispatcher : NavigationDispatcher {
    override val events: Flow<NavigationEvent> = emptyFlow()

    override suspend fun dispatch(event: NavigationEvent) = Unit
}
