package com.comst19.dambom.feature.library.subtitle.export

import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.provider.DocumentsContract
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.comst19.dambom.feature.library.export.VideoExportFiles
import com.comst19.dambom.feature.library.subtitle.model.SubtitleCue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
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
class SubtitleVideoExportRuntimeTest {
    private val context = InstrumentationRegistry.getInstrumentation().context

    @Test fun overlayUsesCueTimingAndDrawsUnicodeAcrossLines() {
        val overlay = SubtitleBitmapOverlay(listOf(SubtitleCue(1_000, 2_000, "안녕하세요\nمرحبا بالعالم")))
        overlay.configure(Size(640, 360))
        assertEquals(1, overlay.getBitmap(0).width)
        val visible = overlay.getBitmap(1_500_000)
        assertTrue(visible.width > 1)
        assertTrue(brightPixels(visible) > 30)
        File(context.filesDir, "subtitle-overlay-unicode.png").outputStream().use {
            visible.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        assertEquals(1, overlay.getBitmap(2_000_000).width)
        overlay.release()
    }

    @Test fun exportedMp4ContainsTimedSubtitlePixelsAndKeepsSource() =
        runBlocking {
            val input = File(checkNotNull(InstrumentationRegistry.getArguments().getString("subtitleExportFixturePath")))
            val before = MessageDigest.getInstance("SHA-256").digest(input.readBytes())
            val name = "subtitle-burned.mp4"
            val destination = DocumentsContract.buildDocumentUri("com.comst19.dambom.compression.test.documents", name)
            val exporter = Media3SubtitleVideoExporter(context, VideoExportFiles(context, Dispatchers.IO))
            try {
                withTimeout(180_000) {
                    exporter.export(input.path, listOf(SubtitleCue(1_000, 2_000, "안녕하세요 Hello")), destination.toString()) {}
                }
                val output = File(context.cacheDir, name)
                assertTrue(output.length() > 0)
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(output.path)
                    val beforeCue = checkNotNull(retriever.getFrameAtTime(300_000, MediaMetadataRetriever.OPTION_CLOSEST))
                    val duringCue = checkNotNull(retriever.getFrameAtTime(1_500_000, MediaMetadataRetriever.OPTION_CLOSEST))
                    val afterCue = checkNotNull(retriever.getFrameAtTime(2_500_000, MediaMetadataRetriever.OPTION_CLOSEST))
                    assertTrue("Subtitle pixels missing", brightPixels(duringCue) > brightPixels(beforeCue) + 30)
                    assertTrue("Subtitle persists after cue", brightPixels(duringCue) > brightPixels(afterCue) + 30)
                    File(context.filesDir, "subtitle-burned-visible.png").outputStream().use {
                        duringCue.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    File(context.filesDir, "subtitle-burned-before.png").outputStream().use {
                        beforeCue.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    assertEquals("yes", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
                    assertTrue(checkNotNull(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)).toLong() >= 2_900)
                }
                assertTrue(before.contentEquals(MessageDigest.getInstance("SHA-256").digest(input.readBytes())))
            } finally {
                exporter.discard(destination.toString())
            }
            assertFalse(File(context.cacheDir, name).exists())
        }

    @Test fun cancellingAnActiveTransformerRemovesDocumentAndTemporaryVideo() =
        runBlocking {
            val input = File(checkNotNull(InstrumentationRegistry.getArguments().getString("subtitleExportLongFixturePath")))
            val name = "subtitle-cancelled.mp4"
            val destination = DocumentsContract.buildDocumentUri("com.comst19.dambom.compression.test.documents", name)
            val exporter = Media3SubtitleVideoExporter(context, VideoExportFiles(context, Dispatchers.IO))
            val started = CompletableDeferred<Unit>()
            val work =
                async {
                    exporter.export(input.path, listOf(SubtitleCue(0, 120_000, "자막 Cancel test")), destination.toString()) {
                        started.complete(Unit)
                    }
                }
            try {
                withTimeout(30_000) { started.await() }
                assertTrue("Transformer already completed before cancellation", work.isActive)
                work.cancelAndJoin()
                assertFalse(File(context.cacheDir, name).exists())
                assertFalse(
                    context.cacheDir
                        .listFiles()
                        .orEmpty()
                        .any { it.name.startsWith("subtitled-") },
                )
            } finally {
                work.cancelAndJoin()
                exporter.discard(destination.toString())
            }
        }

    private fun brightPixels(bitmap: Bitmap): Int {
        var count = 0
        for (y in bitmap.height * 2 / 3 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val color = bitmap.getPixel(x, y)
                if (Color.red(color) > 180 && Color.green(color) > 180 && Color.blue(color) > 180) count++
            }
        }
        return count
    }
}
