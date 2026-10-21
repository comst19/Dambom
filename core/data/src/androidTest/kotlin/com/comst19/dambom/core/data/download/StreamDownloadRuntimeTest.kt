package com.comst19.dambom.core.data.download

import android.media.MediaMetadataRetriever
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
        try {
            var receivedBytes = 0L
            withTimeout(90_000) {
                StreamDownloadEngine(context, OkHttpClient(), store).download(
                    id,
                    "$baseUrl/$path",
                    mimeType,
                    mapOf("Cookie" to "fixture=session"),
                    output,
                    videoHeight = height,
                ) { bytes, _ -> receivedBytes = maxOf(receivedBytes, bytes) }
            }
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
