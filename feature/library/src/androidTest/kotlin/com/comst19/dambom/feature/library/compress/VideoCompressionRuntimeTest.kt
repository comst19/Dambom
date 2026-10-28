package com.comst19.dambom.feature.library.compress

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.comst19.dambom.feature.library.export.VideoExportFiles
import com.comst19.dambom.feature.library.trim.contract.TrimSelection
import com.comst19.dambom.feature.library.trim.export.VideoClipExporter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
class VideoCompressionRuntimeTest {
    private val context = InstrumentationRegistry.getInstrumentation().context
    private val input get() = File(checkNotNull(InstrumentationRegistry.getArguments().getString("compressionFixturePath")))
    private val store = VideoExportFiles(context, Dispatchers.IO)
    private val compressor = Media3VideoCompressor(context, Dispatchers.IO, store)

    @Test
    fun compressionWritesSmallerPlayableAudioVideoWithoutChangingOriginal() =
        runBlocking {
            withTimeout(180_000) {
                val before = sha(input)
                for (profile in CompressionProfile.entries) {
                    val name = "compress-${profile.name.lowercase()}.mp4"
                    val uri = destination(name)
                    try {
                        val source = compressor.inspect(input.absolutePath)
                        println("COMPRESSION SOURCE $profile $source settings=${source.settings(profile)}")
                        val bytes = compressor.compress(input.absolutePath, profile, uri.toString()) {}
                        val output = File(context.cacheDir, name)
                        assertEquals(output.length(), bytes)
                        assertTrue("$profile did not shrink", bytes in 1 until source.bytes)
                        verifyTracks(output, source.durationMillis)
                        val result = compressor.inspect(output.absolutePath)
                        val settings = source.settings(profile)
                        assertTrue(result.width <= source.width && result.height <= source.height)
                        assertTrue(result.width <= settings.width && result.height <= settings.height)
                        assertEquals(before, sha(input))
                        println("COMPRESSION $profile input=${source.bytes} output=$bytes dimensions=${result.width}x${result.height}")
                    } finally {
                        compressor.discard(uri.toString())
                    }
                }
            }
        }

    @Test
    fun cancellationDeletesTemporaryFileAndCreatedDocument() =
        runBlocking {
            val name = "cancel-compression.mp4"
            val uri = destination(name)
            context.contentResolver.openOutputStream(uri)?.use { it.write(byteArrayOf(1)) }
            val job =
                async {
                    store.save(uri.toString(), "cancel-compress") { file ->
                        file.writeBytes(byteArrayOf(1, 2, 3))
                        awaitCancellation()
                    }
                }
            try {
                withTimeout(10_000) {
                    while (context.cacheDir
                            .listFiles()
                            .orEmpty()
                            .none { it.name.startsWith("cancel-compress-") }
                    ) {
                        kotlinx.coroutines.delay(10)
                    }
                }
            } finally {
                job.cancelAndJoin()
            }
            assertFalse(File(context.cacheDir, name).exists())
            assertTrue(
                context.cacheDir
                    .listFiles()
                    .orEmpty()
                    .none { it.name.startsWith("cancel-compress-") },
            )
        }

    @Test
    fun cancellingActiveEncoderDeletesDocumentAndKeepsOriginal() =
        runBlocking {
            val path = InstrumentationRegistry.getArguments().getString("compressionLongFixturePath")
            org.junit.Assume.assumeTrue("Pass compressionLongFixturePath for active encoder cancellation", path != null)
            val source = File(checkNotNull(path))
            val before = sha(source)
            val uri = destination("cancel-active-encoder.mp4")
            context.contentResolver.openOutputStream(uri)?.close()
            val started = CompletableDeferred<Unit>()
            val job =
                async {
                    compressor.compress(source.absolutePath, CompressionProfile.QUALITY, uri.toString()) { started.complete(Unit) }
                }
            try {
                withTimeout(30_000) { started.await() }
                assertTrue("Encoder completed before cancellation", job.isActive)
            } finally {
                job.cancelAndJoin()
            }
            assertFalse(File(context.cacheDir, "cancel-active-encoder.mp4").exists())
            assertTrue(
                context.cacheDir
                    .listFiles()
                    .orEmpty()
                    .none { it.name.startsWith("compress-") },
            )
            assertEquals(before, sha(source))
        }

    @Test
    fun existingClipExportStillKeepsAudioVideoAndOriginal() =
        runBlocking {
            withTimeout(120_000) {
                val before = sha(input)
                val uri = destination("clip-regression.mp4")
                try {
                    val exporter = VideoClipExporter(context, Dispatchers.IO, store)
                    exporter.export(input.absolutePath, TrimSelection(1_000, 4_000), uri.toString()) {}
                    verifyTracks(File(context.cacheDir, "clip-regression.mp4"), 3_000L)
                    assertEquals(before, sha(input))
                } finally {
                    withContext(NonCancellable) { store.discard(uri.toString()) }
                }
            }
        }

    private fun destination(name: String) = DocumentsContract.buildDocumentUri("com.comst19.dambom.compression.test.documents", name)

    private fun verifyTracks(
        file: File,
        durationMillis: Long,
    ) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val formats = (0 until extractor.trackCount).map(extractor::getTrackFormat)
            assertTrue(formats.any { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true })
            assertTrue(formats.any { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true })
            formats.forEachIndexed { track, _ -> decodeTrack(file, track) }
            val duration = formats.maxOf { it.getLong(MediaFormat.KEY_DURATION) } / 1_000
            assertTrue("Duration $duration vs $durationMillis", kotlin.math.abs(duration - durationMillis) < 500)
        } finally {
            extractor.release()
        }
    }

    private fun decodeTrack(
        file: File,
        track: Int,
    ) {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            extractor.setDataSource(file.absolutePath)
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val codec = MediaCodec.createDecoderByType(checkNotNull(format.getString(MediaFormat.KEY_MIME)))
            decoder = codec
            codec.configure(format, null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            var decodedBuffers = 0
            val deadline = SystemClock.elapsedRealtime() + 90_000L
            while (!outputEnded && SystemClock.elapsedRealtime() < deadline) {
                if (!inputEnded) {
                    val index = codec.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val buffer = checkNotNull(codec.getInputBuffer(index))
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val index = codec.dequeueOutputBuffer(info, 10_000)
                if (index >= 0) {
                    if (info.size > 0) decodedBuffers++
                    outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(index, false)
                }
            }
            assertTrue("Decoder timed out for track $track", outputEnded)
            assertTrue("No decoded buffers for track $track", decodedBuffers > 0)
        } finally {
            decoder?.release()
            extractor.release()
        }
    }

    private fun sha(file: File): List<Byte> {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var read = input.read(buffer)
            while (read >= 0) {
                digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        return digest.digest().toList()
    }
}
