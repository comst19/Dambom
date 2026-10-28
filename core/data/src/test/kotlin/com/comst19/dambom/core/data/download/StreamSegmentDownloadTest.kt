@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.comst19.dambom.core.data.download

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.core.domain.model.DownloadFailureReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.io.InterruptedIOException
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StreamSegmentDownloadTest {
    private val started = CountDownLatch(3)
    private val release = CountDownLatch(1)
    private val active = AtomicInteger()
    private val maximum = AtomicInteger()
    private val requests = AtomicInteger()
    private val openSources = AtomicInteger()
    private lateinit var server: MockWebServer
    private lateinit var store: DownloadFileStore
    private lateinit var cache: SimpleCache
    private lateinit var source: CacheDataSource.Factory
    private val id = "segment-test-${UUID.randomUUID()}"

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    when (request.path) {
                        "/playlist.m3u8" -> MockResponse().setBody(hlsManifest())
                        "/manifest.mpd" -> MockResponse().setBody(dashManifest())
                        else -> MockResponse().setBody("segment-payload")
                    }
            }
        server.start()
        store = DownloadFileStore(ApplicationProvider.getApplicationContext<Context>())
        cache = SimpleCache(store.streamDirectory(id), NoOpCacheEvictor(), store.streamDatabaseProvider)
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    if (!chain
                            .request()
                            .url.encodedPath
                            .endsWith(".bin")
                    ) {
                        return@addInterceptor chain.proceed(chain.request())
                    }
                    requests.incrementAndGet()
                    maximum.accumulateAndGet(active.incrementAndGet(), ::maxOf)
                    started.countDown()
                    try {
                        try {
                            check(release.await(10, TimeUnit.SECONDS))
                        } catch (failure: InterruptedException) {
                            throw InterruptedIOException("Segment canceled").apply { initCause(failure) }
                        }
                        chain.proceed(chain.request())
                    } finally {
                        active.decrementAndGet()
                    }
                }.build()
        val upstream = OkHttpDataSource.Factory(client)
        source =
            CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(
                DataSource.Factory {
                    val delegate = upstream.createDataSource()
                    object : DataSource by delegate {
                        private var segmentOpen = false

                        override fun open(dataSpec: DataSpec): Long {
                            segmentOpen = dataSpec.uri.path?.endsWith(".bin") == true
                            if (segmentOpen) openSources.incrementAndGet()
                            return delegate.open(dataSpec)
                        }

                        override fun close() {
                            try {
                                delegate.close()
                            } finally {
                                if (segmentOpen) openSources.decrementAndGet()
                                segmentOpen = false
                            }
                        }
                    }
                },
            )
    }

    @After
    fun tearDown() {
        release.countDown()
        cache.release()
        assertTrue(store.delete(id, null))
        store.streamDatabaseProvider.close()
        server.shutdown()
    }

    @Test
    fun `HLS limits concurrent segment requests to three`() {
        verifyParallelDownload("playlist.m3u8", MimeTypes.APPLICATION_M3U8)
    }

    @Test
    fun `DASH limits concurrent segment requests to three`() {
        verifyParallelDownload("manifest.mpd", MimeTypes.APPLICATION_MPD)
    }

    @Test
    fun `cancel waits for active segments and prevents queued segments from starting`() =
        runBlocking {
            val transfer =
                async(Dispatchers.IO) {
                    downloadStreamSegments(request("playlist.m3u8", MimeTypes.APPLICATION_M3U8), source, {}) { _, _ -> }
                }
            try {
                assertTrue(withContext(Dispatchers.IO) { started.await(10, TimeUnit.SECONDS) })
                withTimeout(10_000) { transfer.cancelAndJoin() }
                assertEquals(0, openSources.get())
                assertEquals(3, requests.get())
                cache.release()
                assertFalse(SimpleCache.isCacheFolderLocked(store.streamDirectory(id)))
            } finally {
                release.countDown()
                transfer.cancelAndJoin()
            }
        }

    @Test
    fun `progress failure joins every active segment before returning`() =
        runBlocking {
            val expected = StreamDownloadException(DownloadFailureReason.INSUFFICIENT_STORAGE)
            val transfer =
                async(Dispatchers.IO) {
                    try {
                        downloadStreamSegments(
                            request("playlist.m3u8", MimeTypes.APPLICATION_M3U8),
                            source,
                            { if (release.count == 0L) throw expected },
                        ) { _, _ -> }
                        null
                    } catch (failure: StreamDownloadException) {
                        failure
                    }
                }
            try {
                assertTrue(withContext(Dispatchers.IO) { started.await(10, TimeUnit.SECONDS) })
                release.countDown()
                assertEquals(expected, withTimeout(10_000) { transfer.await() })
                assertEquals(0, openSources.get())
            } finally {
                release.countDown()
                transfer.cancelAndJoin()
            }
        }

    private fun verifyParallelDownload(
        path: String,
        mimeType: String,
    ) = runBlocking {
        val progressBytes = mutableListOf<Long>()
        val callbacks = AtomicInteger()
        val transfer =
            async(Dispatchers.IO) {
                downloadStreamSegments(
                    request(path, mimeType),
                    source,
                    { ShadowSystemClock.advanceBy(Duration.ofMillis(500)) },
                ) { bytes, _ ->
                    assertEquals(1, callbacks.incrementAndGet())
                    progressBytes.add(bytes)
                    callbacks.decrementAndGet()
                }
            }
        try {
            assertTrue(withContext(Dispatchers.IO) { started.await(10, TimeUnit.SECONDS) })
            release.countDown()
            withTimeout(10_000) { transfer.await() }
            assertEquals(3, maximum.get())
            assertEquals(12, requests.get())
            assertEquals(0, active.get())
            assertTrue(progressBytes.isNotEmpty())
            assertTrue(progressBytes.zipWithNext().all { (before, after) -> after >= before })
            assertTrue(cache.cacheSpace >= 12 * "segment-payload".length)
            assertTrue(SystemClock.elapsedRealtime() >= 500L)
        } finally {
            release.countDown()
            transfer.cancelAndJoin()
        }
    }

    private fun request(
        path: String,
        mimeType: String,
    ): DownloadRequest =
        DownloadRequest
            .Builder(id, android.net.Uri.parse(server.url("/$path").toString()))
            .setMimeType(mimeType)
            .build()

    private fun hlsManifest(): String =
        "#EXTM3U\n#EXT-X-TARGETDURATION:1\n#EXT-X-MEDIA-SEQUENCE:0\n" +
            (1..12).joinToString("") { "#EXTINF:1.0,\nsegment-$it.bin\n" } + "#EXT-X-ENDLIST\n"

    private fun dashManifest(): String =
        """
        <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static" mediaPresentationDuration="PT12S" minBufferTime="PT1S">
          <Period duration="PT12S">
            <AdaptationSet mimeType="video/mp4" codecs="avc1.42E01E">
              <Representation id="video" bandwidth="100000" width="160" height="90">
                <SegmentTemplate timescale="1" duration="1" startNumber="1" media="segment-${'$'}Number${'$'}.bin"/>
              </Representation>
            </AdaptationSet>
          </Period>
        </MPD>
        """.trimIndent()
}
