@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.comst19.dambom.core.data.download

import android.media.MediaMetadataRetriever
import android.os.SystemClock
import android.util.Log
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.comst19.dambom.core.network.header.mediaRequestClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class StreamDownloadRuntimeTest {
    @Test
    fun cancelledPreparationCanResumeAndCleanCache() =
        runBlocking {
            val baseUrl = InstrumentationRegistry.getArguments().getString("streamFixtureBaseUrl")
            assumeTrue(baseUrl != null)
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val store = DownloadFileStore(context)
            val id = "prepare-cancel-${UUID.randomUUID()}"
            val output = store.partialFile(id)
            val requested = CompletableDeferred<Unit>()
            val releaseRequest = CountDownLatch(1)
            val slowClient =
                OkHttpClient
                    .Builder()
                    .addInterceptor {
                        requested.complete(Unit)
                        releaseRequest.await(15, TimeUnit.SECONDS)
                        it.proceed(it.request())
                    }.build()
            try {
                val preparing =
                    launch {
                        StreamDownloadEngine(context, slowClient, store).download(
                            id,
                            "$baseUrl/master.m3u8",
                            "application/x-mpegURL",
                            mapOf("Cookie" to "fixture=session"),
                            output,
                        ) { _, _ -> }
                    }
                try {
                    withTimeout(10_000) { requested.await() }
                } finally {
                    preparing.cancel()
                    releaseRequest.countDown()
                    preparing.cancelAndJoin()
                }
                withTimeout(30_000) {
                    StreamDownloadEngine(context, OkHttpClient(), store).download(
                        id,
                        "$baseUrl/master.m3u8",
                        "application/x-mpegURL",
                        mapOf("Cookie" to "fixture=session"),
                        output,
                    ) { _, _ -> }
                }
                assertTrue(output.isFile && output.length() > 0)
            } finally {
                releaseRequest.countDown()
                assertTrue(store.delete(id, null))
            }
        }

    @Test
    fun standardAes128HlsBecomesPlayableMp4() = verifyStream("encrypted/playlist.m3u8", "application/vnd.apple.mpegurl")

    @Test
    fun hlsWithSeparateAudioBecomesPlayableMp4() = verifyStream("master.m3u8", "application/vnd.apple.mpegurl")

    @Test
    fun dashWithSeparateAudioBecomesPlayableMp4() = verifyStream("manifest.mpd", "application/dash+xml")

    @Test
    fun hlsSelectedResolutionPreservesAudio() = verifyStream("master.m3u8", "application/vnd.apple.mpegurl", 90)

    @Test
    fun delayedHlsAndDashSegmentsDownloadFasterInParallel() =
        runBlocking {
            for ((path, mimeType) in listOf("master.m3u8" to "application/x-mpegURL", "manifest.mpd" to "application/dash+xml")) {
                val sequentialMillis = measureSegmentDownload(path, mimeType, 1)
                val parallelMillis = measureSegmentDownload(path, mimeType, 3)
                Log.i(
                    "StreamDownloadRuntime",
                    "path=$path sequentialMillis=$sequentialMillis parallelMillis=$parallelMillis",
                )
                assertTrue("$path sequential=$sequentialMillis parallel=$parallelMillis", parallelMillis < sequentialMillis)
            }
        }

    @Test
    fun interruptedHlsReusesCachedMediaWithNetworkDisabled() =
        runBlocking {
            val baseUrl = InstrumentationRegistry.getArguments().getString("streamFixtureBaseUrl")
            assumeTrue(baseUrl != null)
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val store = DownloadFileStore(context)
            val id = "resume-test-${UUID.randomUUID()}"
            val output = store.partialFile(id)
            try {
                var interrupted = false
                try {
                    withTimeout(30_000) {
                        StreamDownloadEngine(context, OkHttpClient(), store).download(
                            id,
                            "$baseUrl/master.m3u8",
                            "application/vnd.apple.mpegurl",
                            mapOf("Cookie" to "fixture=session"),
                            output,
                        ) { bytes, _ -> if (bytes > 0) throw CancellationException("Simulate pause") }
                    }
                } catch (_: CancellationException) {
                    interrupted = true
                }
                assertTrue(interrupted)
                val offlineClient =
                    OkHttpClient
                        .Builder()
                        .addInterceptor {
                            throw java.io.IOException(
                                "Network must not be used on cached resume",
                            )
                        }.build()
                withTimeout(30_000) {
                    StreamDownloadEngine(context, offlineClient, store).download(
                        id,
                        "$baseUrl/master.m3u8",
                        "application/vnd.apple.mpegurl",
                        mapOf("Cookie" to "fixture=session"),
                        output,
                    ) { _, _ -> }
                }
                assertTrue(output.length() > 0)
            } finally {
                assertTrue(store.delete(id, null))
            }
        }

    private suspend fun measureSegmentDownload(
        path: String,
        mimeType: String,
        parallelism: Int,
    ): Long {
        val baseUrl = InstrumentationRegistry.getArguments().getString("streamFixtureBaseUrl")
        assumeTrue(baseUrl != null)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = DownloadFileStore(context)
        val id = "segment-timing-${UUID.randomUUID()}"
        val cache = SimpleCache(store.streamDirectory(id), NoOpCacheEvictor(), store.streamDatabaseProvider)
        val url = "$baseUrl/$path"
        val delayedClient =
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    val requestPath = chain.request().url.encodedPath
                    if (!requestPath.endsWith(".m3u8") && !requestPath.endsWith(".mpd")) Thread.sleep(300)
                    chain.proceed(chain.request())
                }.build()
        val upstream = OkHttpDataSource.Factory(mediaRequestClient(delayedClient, url, mapOf("Cookie" to "fixture=session")))
        val source = CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(upstream)
        try {
            val request = DownloadRequest.Builder(id, android.net.Uri.parse(url)).setMimeType(mimeType).build()
            val startedAt = SystemClock.elapsedRealtime()
            withTimeout(90_000) {
                downloadStreamSegments(request, source, {}, parallelism) { _, _ -> }
            }
            assertTrue(cache.cacheSpace > 0)
            return SystemClock.elapsedRealtime() - startedAt
        } finally {
            cache.release()
            assertTrue(store.delete(id, null))
            store.streamDatabaseProvider.close()
        }
    }

    private fun verifyStream(
        path: String,
        mimeType: String,
        height: Int? = null,
    ) = runBlocking {
        val baseUrl = InstrumentationRegistry.getArguments().getString("streamFixtureBaseUrl")
        assumeTrue("Pass streamFixtureBaseUrl for a synthetic authenticated VOD fixture server", baseUrl != null)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = DownloadFileStore(context)
        val id = "stream-test-${UUID.randomUUID()}"
        val output = store.partialFile(id)
        val delayMillis =
            InstrumentationRegistry.getArguments().getString("segmentRequestDelayMillis")?.toLong() ?: 0L
        val activeRequests = AtomicInteger()
        val maximumRequests = AtomicInteger()
        val client =
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    val path = chain.request().url.encodedPath
                    if (delayMillis == 0L || path.endsWith(".m3u8") || path.endsWith(".mpd")) {
                        return@addInterceptor chain.proceed(chain.request())
                    }
                    maximumRequests.accumulateAndGet(activeRequests.incrementAndGet(), ::maxOf)
                    try {
                        Thread.sleep(delayMillis)
                        chain.proceed(chain.request())
                    } finally {
                        activeRequests.decrementAndGet()
                    }
                }.build()
        try {
            var receivedBytes = 0L
            val startedAt = SystemClock.elapsedRealtime()
            withTimeout(90_000) {
                StreamDownloadEngine(context, client, store).download(
                    id,
                    "$baseUrl/$path",
                    mimeType,
                    mapOf("Cookie" to "fixture=session"),
                    output,
                    videoHeight = height,
                ) { bytes, _ -> receivedBytes = maxOf(receivedBytes, bytes) }
            }
            Log.i(
                "StreamDownloadRuntime",
                "path=$path delayMillis=$delayMillis elapsedMillis=${SystemClock.elapsedRealtime() - startedAt} " +
                    "maximumRequests=${maximumRequests.get()} outputBytes=${output.length()}",
            )
            if (delayMillis > 0L) assertTrue(maximumRequests.get() in 2..3)
            assertEquals(0, activeRequests.get())
            assertTrue(receivedBytes > 0L)
            assertTrue(output.isFile && output.length() > 0L)
            val metadata = MediaMetadataRetriever()
            try {
                metadata.setDataSource(output.absolutePath)
                assertEquals("yes", metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO))
                assertEquals("yes", metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
                if (height !=
                    null
                ) {
                    assertEquals(height.toString(), metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT))
                }
                assertTrue(checkNotNull(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)).toLong() >= 3_000)
            } finally {
                metadata.release()
            }
        } finally {
            assertTrue(store.delete(id, null))
        }
    }
}
