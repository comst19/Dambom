package com.comst19.dambom.feature.library.subtitle

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.comst19.dambom.feature.library.subtitle.data.GemmaModelStore
import com.comst19.dambom.feature.library.subtitle.inference.GemmaSubtitleEngine
import com.comst19.dambom.feature.library.subtitle.inference.VideoAudioDecoder
import com.comst19.dambom.feature.library.subtitle.model.SubtitleLanguagePair
import com.comst19.dambom.feature.library.subtitle.runtime.SubtitleRuntimeDelivery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class SubtitleAudioRuntimeTest {
    @Test fun checkingAllThreePairsDoesNotDownloadTheLlm() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().context
            val models = GemmaModelStore(context, Dispatchers.IO)
            assertFalse(models.installed())
            val engine =
                GemmaSubtitleEngine(
                    context,
                    Dispatchers.IO,
                    VideoAudioDecoder(Dispatchers.IO),
                    models,
                    SubtitleRuntimeDelivery(context),
                )
            for (pair in SubtitleLanguagePair.entries) {
                val status = runCatching { engine.status(pair).name }.getOrElse { "FAILED:${it.javaClass.simpleName}" }
                println("SUBTITLE_CAPABILITY pair=$pair status=$status")
            }
            assertFalse(models.installed())
            assertFalse(models.model.exists())
        }

    @Test fun localVideoDecodesToBoundedMono16kChunksAndCanRunAgainAfterCancellation() =
        runBlocking {
            val path = checkNotNull(InstrumentationRegistry.getArguments().getString("subtitleFixturePath"))
            val source = File(path)
            val before = MessageDigest.getInstance("SHA-256").digest(source.readBytes()).toList()
            val decoder = VideoAudioDecoder(Dispatchers.IO)
            try {
                decoder.decode(path) { throw CancellationException("test cancellation") }
            } catch (_: CancellationException) {
            }
            var bytes = 0
            var lastEnd = 0L
            var audible = false
            decoder.decode(path) { chunk ->
                assertTrue(chunk.bytes.size in 2..256_000)
                assertEquals(0, chunk.bytes.size % 2)
                assertTrue(chunk.startMillis >= lastEnd - 2)
                assertTrue(chunk.endMillis > chunk.startMillis)
                bytes += chunk.bytes.size
                lastEnd = chunk.endMillis
                audible = audible || chunk.bytes.any { it != 0.toByte() }
            }
            assertTrue(bytes > 0)
            assertTrue(audible)
            assertTrue(kotlin.math.abs(lastEnd - bytes / 32L) < 100)
            assertEquals(before, MessageDigest.getInstance("SHA-256").digest(source.readBytes()).toList())
            println("SUBTITLE_PCM bytes=$bytes endMillis=$lastEnd originalUnchanged=true")
        }
}
