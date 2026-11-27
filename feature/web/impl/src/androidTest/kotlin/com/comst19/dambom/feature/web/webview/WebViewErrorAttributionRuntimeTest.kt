package com.comst19.dambom.feature.web.webview

import android.graphics.Bitmap
import android.net.http.SslError
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.webkit.SafeBrowsingResponse
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.comst19.dambom.feature.web.contract.WebTab
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStreamReader
import java.math.BigInteger
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedKeyManager
import javax.security.auth.x500.X500Principal

@RunWith(AndroidJUnit4::class)
class WebViewErrorAttributionRuntimeTest {
    private lateinit var httpsServer: LocalSelfSignedHttpsFixtureServer
    private lateinit var httpServer: LocalHttpErrorFixtureServer

    @Before
    fun setUp() {
        httpsServer = LocalSelfSignedHttpsFixtureServer().apply { start() }
        httpServer = LocalHttpErrorFixtureServer(httpsServer.port).apply { start() }
    }

    @After
    fun tearDown() {
        httpServer.close()
        httpsServer.close()
    }

    @Test
    fun subresourceTlsFailureDoesNotCoverNormalPageAndBlocksConnection() {
        val harness = RuntimeWebViewHarness()
        try {
            val pageUrl = httpServer.url("/page-with-bad-subresource")
            harness.loadUrl(pageUrl)

            assertTrue(
                "Expected subresource SSL error callback from real Chromium WebView; " +
                    "finished=${harness.finishedUrls}, ssl=${harness.sslErrorUrls}, " +
                    "errors=${harness.receivedErrorCodes}, serverErrors=${httpsServer.handshakeErrors}",
                harness.waitUntil {
                    harness.finishedUrls.contains(pageUrl) &&
                        harness.sslErrorUrls.any { it.startsWith("https://127.0.0.1:${httpsServer.port}/") }
                },
            )
            harness.settleMainThread()

            assertNull(
                "Subresource TLS error must not overwrite normal main page",
                harness.currentFailure.get(),
            )
            assertTrue(
                "Observed failures must not contain SECURITY for subresource TLS error: ${harness.observedFailures}",
                harness.observedFailures.none { it == WebNavigationFailure.SECURITY },
            )
            assertEquals(
                "handler.cancel() must prevent HTTP payload exchange over untrusted TLS",
                0,
                httpsServer.httpRequestCount.get(),
            )
        } finally {
            harness.destroy()
        }
    }

    @Test
    fun mainFrameSelfSignedTlsShowsSecurityFailureAndNextNavigationClearsIt() {
        val harness = RuntimeWebViewHarness()
        try {
            val badMainUrl = httpsServer.url("/main")
            harness.loadUrl(badMainUrl)

            assertTrue(
                "Expected main-frame SSL error to report SECURITY failure; " +
                    "ssl=${harness.sslErrorUrls}, failure=${harness.currentFailure.get()}, " +
                    "started=${harness.startedUrls}, intercepted=${harness.interceptedUrls}, " +
                    "errors=${harness.receivedErrorCodes}, accepted=${httpsServer.acceptedCount.get()}, " +
                    "serverErrors=${httpsServer.handshakeErrors}",
                harness.waitUntil {
                    harness.sslErrorUrls.contains(badMainUrl) &&
                        harness.currentFailure.get() == WebNavigationFailure.SECURITY
                },
            )
            harness.settleMainThread()

            assertEquals(WebNavigationFailure.SECURITY, harness.currentFailure.get())
            assertEquals(
                "handler.cancel() must block main-frame insecure TLS request",
                0,
                httpsServer.httpRequestCount.get(),
            )

            val okUrl = httpServer.url("/ok")
            harness.loadUrl(okUrl)

            assertTrue(
                "Navigating away from failed TLS page must finish and clear SECURITY failure",
                harness.waitUntil {
                    harness.finishedUrls.contains(okUrl) && harness.currentFailure.get() == null
                },
            )
        } finally {
            harness.destroy()
        }
    }

    @Test
    fun redirectToSelfSignedTlsShowsSecurityFailure() {
        val harness = RuntimeWebViewHarness()
        try {
            val redirectUrl = httpServer.url("/redirect-to-bad-tls")
            val targetUrl = httpsServer.url("/redirected")
            harness.loadUrl(redirectUrl)

            assertTrue(
                "Redirect to untrusted TLS target must report SECURITY failure; " +
                    "ssl=${harness.sslErrorUrls}, failure=${harness.currentFailure.get()}, " +
                    "errors=${harness.receivedErrorCodes}, serverErrors=${httpsServer.handshakeErrors}",
                harness.waitUntil {
                    harness.sslErrorUrls.contains(targetUrl) &&
                        harness.currentFailure.get() == WebNavigationFailure.SECURITY
                },
            )
            harness.settleMainThread()

            assertEquals(WebNavigationFailure.SECURITY, harness.currentFailure.get())
            assertEquals(0, httpsServer.httpRequestCount.get())
        } finally {
            harness.destroy()
        }
    }

    @Test
    fun sameUrlReloadRecoversFromHttpServerError() {
        val harness = RuntimeWebViewHarness()
        try {
            val flakyUrl = httpServer.url("/flaky")
            harness.loadUrl(flakyUrl)

            assertTrue(
                "Initial HTTP 500 response must report HTTP_SERVER failure",
                harness.waitUntil {
                    harness.finishedUrls.contains(flakyUrl) &&
                        harness.currentFailure.get() == WebNavigationFailure.HTTP_SERVER
                },
            )

            harness.finishedUrls.clear()
            harness.reload()

            assertTrue(
                "Same-URL reload succeeding with HTTP 200 must clear HTTP_SERVER failure",
                harness.waitUntil {
                    harness.finishedUrls.contains(flakyUrl) &&
                        httpServer.flakyRequests.get() >= 2 &&
                        harness.currentFailure.get() == null
                },
            )
            harness.settleMainThread()

            assertNull(harness.currentFailure.get())
        } finally {
            harness.destroy()
        }
    }

    @Test
    fun sameUrlReloadOnSelfSignedTlsStillShowsSecurityFailure() {
        val harness = RuntimeWebViewHarness()
        try {
            val badMainUrl = httpsServer.url("/reload-tls")
            harness.loadUrl(badMainUrl)

            assertTrue(
                "Initial self-signed TLS load must report SECURITY failure",
                harness.waitUntil {
                    harness.sslErrorUrls.contains(badMainUrl) &&
                        harness.currentFailure.get() == WebNavigationFailure.SECURITY
                },
            )

            val initialSslCount = harness.sslErrorUrls.size
            harness.reload()

            assertTrue(
                "Reloading self-signed TLS URL must report SECURITY failure again",
                harness.waitUntil {
                    harness.sslErrorUrls.size > initialSslCount &&
                        harness.currentFailure.get() == WebNavigationFailure.SECURITY
                },
            )
            harness.settleMainThread()

            assertEquals(WebNavigationFailure.SECURITY, harness.currentFailure.get())
            assertEquals(0, httpsServer.httpRequestCount.get())
        } finally {
            harness.destroy()
        }
    }

    @Test
    fun inLoadReplaceStateStillDeliversPageFinished() {
        val harness = RuntimeWebViewHarness()
        try {
            val initialUrl = httpServer.url("/replace-during-load")
            val replacedUrl = httpServer.url("/replaced-during-load")
            harness.loadUrl(initialUrl)

            assertTrue(
                "In-load same-origin replaceState must still deliver onPageFinished for replaced URL; " +
                    "started=${harness.startedUrls}, finished=${harness.finishedUrls}",
                harness.waitUntil {
                    harness.startedUrls.contains(initialUrl) &&
                        harness.finishedUrls.contains(replacedUrl)
                },
            )
            harness.settleMainThread()

            assertNull(harness.currentFailure.get())
        } finally {
            harness.destroy()
        }
    }

    @Test
    fun inLoadReplaceStateToPreviousVisitedUrlStillDeliversPageFinished() {
        val harness = RuntimeWebViewHarness()
        try {
            val urlA = httpServer.url("/a")
            val urlB = httpServer.url("/b-replace-to-a")
            harness.loadUrl(urlA)

            assertTrue(
                "Initial /a page must finish loading; finished=${harness.finishedUrls}",
                harness.waitUntil { harness.finishedUrls.contains(urlA) },
            )

            harness.startedUrls.clear()
            harness.finishedUrls.clear()
            harness.loadUrl(urlB)

            assertTrue(
                "Loading /b that replaceStates back to /a must still deliver onPageFinished(/a); " +
                    "started=${harness.startedUrls}, finished=${harness.finishedUrls}",
                harness.waitUntil {
                    harness.startedUrls.contains(urlB) &&
                        harness.finishedUrls.contains(urlA)
                },
            )
            harness.settleMainThread()

            assertNull(harness.currentFailure.get())
        } finally {
            harness.destroy()
        }
    }
}

private class RuntimeWebViewHarness {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    val currentFailure = AtomicReference<WebNavigationFailure?>(null)
    val observedFailures = CopyOnWriteArrayList<WebNavigationFailure>()
    val startedUrls = CopyOnWriteArrayList<String>()
    val finishedUrls = CopyOnWriteArrayList<String>()
    val interceptedUrls = CopyOnWriteArrayList<String>()
    val sslErrorUrls = CopyOnWriteArrayList<String>()
    val receivedErrorCodes = CopyOnWriteArrayList<String>()
    private val webView: WebView

    init {
        lateinit var created: WebView
        instrumentation.runOnMainSync {
            created =
                createWebView(
                    context = instrumentation.targetContext,
                    tab = WebTab(id = 1L),
                    savedState = null,
                    onPageStarted = { _, _, _, _ -> },
                    onPageFinished = { _, url, _, _ -> if (url != null) finishedUrls.add(url) },
                    onPageChanged = { _, _, _ -> },
                    onMediaRequest = { _, _, _ -> },
                    onProgress = {},
                    onNavigationFailure = { failure ->
                        currentFailure.set(failure)
                        if (failure != null) observedFailures.add(failure)
                    },
                    onRendererGone = {},
                )
            val delegate = created.webViewClient
            created.webViewClient =
                object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean = delegate.shouldOverrideUrlLoading(view, request)

                    override fun onPageStarted(
                        view: WebView?,
                        url: String?,
                        favicon: Bitmap?,
                    ) {
                        if (url != null) startedUrls.add(url)
                        delegate.onPageStarted(view, url, favicon)
                    }

                    override fun onPageFinished(
                        view: WebView?,
                        url: String?,
                    ) {
                        delegate.onPageFinished(view, url)
                    }

                    override fun doUpdateVisitedHistory(
                        view: WebView?,
                        url: String?,
                        isReload: Boolean,
                    ) {
                        delegate.doUpdateVisitedHistory(view, url, isReload)
                    }

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): WebResourceResponse? {
                        request?.url?.toString()?.let(interceptedUrls::add)
                        return delegate.shouldInterceptRequest(view, request)
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?,
                    ) {
                        receivedErrorCodes.add("${request?.url}:${error?.errorCode}")
                        delegate.onReceivedError(view, request, error)
                    }

                    override fun onReceivedHttpError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        errorResponse: WebResourceResponse?,
                    ) {
                        delegate.onReceivedHttpError(view, request, errorResponse)
                    }

                    override fun onReceivedSslError(
                        view: WebView?,
                        handler: SslErrorHandler?,
                        error: SslError?,
                    ) {
                        error?.url?.let(sslErrorUrls::add)
                        delegate.onReceivedSslError(view, handler, error)
                    }

                    override fun onSafeBrowsingHit(
                        view: WebView?,
                        request: WebResourceRequest?,
                        threatType: Int,
                        callback: SafeBrowsingResponse?,
                    ) {
                        delegate.onSafeBrowsingHit(view, request, threatType, callback)
                    }
                }
        }
        webView = created
    }

    fun loadUrl(url: String) {
        instrumentation.runOnMainSync { webView.loadUrl(url) }
    }

    fun reload() {
        instrumentation.runOnMainSync { webView.reload() }
    }

    fun waitUntil(
        timeoutMs: Long = WAIT_TIMEOUT_MS,
        condition: () -> Boolean,
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(POLL_INTERVAL_MS)
        }
        return condition()
    }

    fun settleMainThread() {
        val latch = CountDownLatch(1)
        instrumentation.runOnMainSync {
            webView.postDelayed({ latch.countDown() }, SETTLE_DELAY_MS)
        }
        latch.await(WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    }

    fun destroy() {
        instrumentation.runOnMainSync { webView.destroy() }
    }
}

private class LocalHttpErrorFixtureServer(
    private val httpsPort: Int,
) : Closeable {
    private val serverSocket = ServerSocket(0, 16, InetAddress.getByName(LOOPBACK_HOST))
    val flakyRequests = AtomicInteger(0)

    @Volatile private var running = false
    private var worker: Thread? = null

    fun start() {
        running = true
        worker =
            Thread(
                {
                    while (running) {
                        val socket =
                            try {
                                serverSocket.accept()
                            } catch (_: SocketException) {
                                break
                            }
                        Thread { handleClient(socket) }.start()
                    }
                },
                "LocalHttpErrorFixtureServer",
            ).apply {
                isDaemon = true
                start()
            }
    }

    fun url(path: String): String = "http://$LOOPBACK_HOST:${serverSocket.localPort}$path"

    private fun handleClient(socket: Socket) {
        socket.use { client ->
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val requestLine = reader.readLine() ?: return
            val path = requestLine.split(' ').getOrNull(1).orEmpty()
            while (true) {
                val header = reader.readLine()
                if (header.isNullOrEmpty()) break
            }
            val response =
                when (path) {
                    "/page-with-bad-subresource" -> {
                        val body =
                            """
                            <!doctype html>
                            <html>
                            <head><title>Healthy Page</title></head>
                            <body>
                              <h1>Healthy Page</h1>
                              <img src="https://$LOOPBACK_HOST:$httpsPort/bad.png" />
                              <iframe src="https://$LOOPBACK_HOST:$httpsPort/bad-frame"></iframe>
                            </body>
                            </html>
                            """.trimIndent()
                        httpResponse("200 OK", body)
                    }

                    "/redirect-to-bad-tls" -> {
                        "HTTP/1.1 302 Found\r\n" +
                            "Location: https://$LOOPBACK_HOST:$httpsPort/redirected\r\n" +
                            "Content-Length: 0\r\n" +
                            "Connection: close\r\n\r\n"
                    }

                    "/replace-during-load" -> {
                        val body =
                            """
                            <!doctype html>
                            <html>
                            <head>
                              <title>Replace During Load</title>
                              <script>history.replaceState(null, "", "/replaced-during-load");</script>
                            </head>
                            <body>
                              <h1>Replaced</h1>
                              <img src="/slow-asset.png" />
                            </body>
                            </html>
                            """.trimIndent()
                        httpResponse("200 OK", body)
                    }

                    "/b-replace-to-a" -> {
                        val body =
                            """
                            <!doctype html>
                            <html>
                            <head>
                              <title>Replace To A</title>
                              <script>history.replaceState(null, "", "/a");</script>
                            </head>
                            <body>
                              <h1>Replaced Back To A</h1>
                              <img src="/slow-asset.png" />
                            </body>
                            </html>
                            """.trimIndent()
                        httpResponse("200 OK", body)
                    }

                    "/slow-asset.png" -> {
                        Thread.sleep(150L)
                        "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: image/png\r\n" +
                            "Content-Length: 0\r\n" +
                            "Connection: close\r\n\r\n"
                    }

                    "/flaky" -> {
                        if (flakyRequests.incrementAndGet() == 1) {
                            httpResponse("500 Internal Server Error", "<html><body>Temporary Error</body></html>")
                        } else {
                            httpResponse("200 OK", "<html><body>Recovered</body></html>")
                        }
                    }

                    else -> {
                        httpResponse("200 OK", "<html><head><title>OK</title></head><body>OK</body></html>")
                    }
                }
            client.getOutputStream().write(response.toByteArray(Charsets.UTF_8))
            client.getOutputStream().flush()
        }
    }

    private fun httpResponse(
        status: String,
        body: String,
    ): String {
        val bytes = body.toByteArray(Charsets.UTF_8)
        return "HTTP/1.1 $status\r\n" +
            "Content-Type: text/html; charset=utf-8\r\n" +
            "Cache-Control: no-store\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n\r\n" +
            body
    }

    override fun close() {
        running = false
        runCatching { serverSocket.close() }
        worker?.interrupt()
    }
}

private class LocalSelfSignedHttpsFixtureServer : Closeable {
    private val alias = "dambom_webview_tls_fixture_${System.nanoTime()}"
    private val keyStore: KeyStore
    private val serverSocket: SSLServerSocket
    val acceptedCount = AtomicInteger(0)
    val httpRequestCount = AtomicInteger(0)
    val handshakeErrors = CopyOnWriteArrayList<String>()

    @Volatile private var running = false
    private var worker: Thread? = null

    init {
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
        val now = System.currentTimeMillis()
        val spec =
            KeyGenParameterSpec
                .Builder(alias, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256)
                .setCertificateSubject(X500Principal("CN=$LOOPBACK_HOST"))
                .setCertificateSerialNumber(BigInteger.ONE)
                .setCertificateNotBefore(Date(now - DAY_MS))
                .setCertificateNotAfter(Date(now + DAY_MS))
                .build()
        generator.initialize(spec)
        generator.generateKeyPair()

        keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val certChain =
            keyStore
                .getCertificateChain(alias)
                .map { it as X509Certificate }
                .toTypedArray()
        val privateKey = keyStore.getKey(alias, null) as PrivateKey
        val keyManager = SingleAliasKeyManager(alias, certChain, privateKey)
        val sslContext =
            SSLContext.getInstance("TLS").apply {
                init(arrayOf(keyManager), null, null)
            }
        serverSocket =
            sslContext.serverSocketFactory
                .createServerSocket(0, 16, InetAddress.getByName(LOOPBACK_HOST)) as SSLServerSocket
    }

    val port: Int
        get() = serverSocket.localPort

    fun url(path: String): String = "https://$LOOPBACK_HOST:$port$path"

    fun start() {
        running = true
        worker =
            Thread(
                {
                    while (running) {
                        val socket =
                            try {
                                serverSocket.accept() as SSLSocket
                            } catch (_: SocketException) {
                                break
                            }
                        acceptedCount.incrementAndGet()
                        Thread { handleClient(socket) }.start()
                    }
                },
                "LocalSelfSignedHttpsFixtureServer",
            ).apply {
                isDaemon = true
                start()
            }
    }

    private fun handleClient(socket: SSLSocket) {
        runCatching {
            socket.use { tlsClient ->
                tlsClient.startHandshake()
                val reader = BufferedReader(InputStreamReader(tlsClient.inputStream))
                if (reader.readLine() != null) {
                    httpRequestCount.incrementAndGet()
                    val body = "<html><body>Should never be trusted</body></html>"
                    val response =
                        "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/html; charset=utf-8\r\n" +
                            "Content-Length: ${body.toByteArray().size}\r\n" +
                            "Connection: close\r\n\r\n$body"
                    tlsClient.outputStream.write(response.toByteArray(Charsets.UTF_8))
                    tlsClient.outputStream.flush()
                }
            }
        }.onFailure { error ->
            handshakeErrors.add("${error::class.simpleName}:${error.message}")
        }
    }

    override fun close() {
        running = false
        runCatching { serverSocket.close() }
        worker?.interrupt()
        runCatching { keyStore.deleteEntry(alias) }
    }
}

private class SingleAliasKeyManager(
    private val alias: String,
    private val certChain: Array<X509Certificate>,
    private val key: PrivateKey,
) : X509ExtendedKeyManager() {
    override fun getClientAliases(
        keyType: String?,
        issuers: Array<out Principal>?,
    ): Array<String> = emptyArray()

    override fun chooseClientAlias(
        keyType: Array<out String>?,
        issuers: Array<out Principal>?,
        socket: Socket?,
    ): String? = null

    override fun getServerAliases(
        keyType: String?,
        issuers: Array<out Principal>?,
    ): Array<String> = arrayOf(alias)

    override fun chooseServerAlias(
        keyType: String?,
        issuers: Array<out Principal>?,
        socket: Socket?,
    ): String = alias

    override fun chooseEngineServerAlias(
        keyType: String?,
        issuers: Array<out Principal>?,
        engine: SSLEngine?,
    ): String = alias

    override fun getCertificateChain(alias: String?): Array<X509Certificate>? = if (alias == this.alias) certChain else null

    override fun getPrivateKey(alias: String?): PrivateKey? = if (alias == this.alias) key else null
}

private const val LOOPBACK_HOST = "127.0.0.1"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val DAY_MS = 86_400_000L
private const val WAIT_TIMEOUT_MS = 15_000L
private const val POLL_INTERVAL_MS = 50L
private const val SETTLE_DELAY_MS = 400L
