@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.comst19.dambom.core.data.download

import android.media.MediaMetadataRetriever
import android.os.Debug
import android.os.PowerManager
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.comst19.dambom.core.common.util.VideoExportGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class ExportConcurrencyMeasurementTest {
    @Test
    fun measureThreeIdenticalDownloads() =
        runBlocking {
            assumeTrue(InstrumentationRegistry.getArguments().getString("fixtureBaseUrl") != null)
            withTimeout(300_000) { measure() }
        }

    private suspend fun measure() =
        withContext(Dispatchers.IO) {
            val arguments = InstrumentationRegistry.getArguments()
            val baseUrl = checkNotNull(arguments.getString("fixtureBaseUrl"))
            val label = checkNotNull(arguments.getString("measurementLabel"))
            val parallelism = checkNotNull(arguments.getString("exportParallelism")).toInt()
            val cached = arguments.getString("cached") == "true"
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val store = DownloadFileStore(context)
            val engine = StreamDownloadEngine(context, OkHttpClient(), store, exportGate = VideoExportGate(parallelism))
            val ids = List(3) { "export-measurement-$label-$it" }
            val outputs = ids.map(store::partialFile)
            val samples = mutableListOf<MemorySample>()
            val power = context.getSystemService(PowerManager::class.java)
            try {
                if (cached) {
                    ids.forEachIndexed { index, id ->
                        engine.download(
                            id,
                            "$baseUrl/playlist.m3u8",
                            "application/vnd.apple.mpegurl",
                            emptyMap(),
                            outputs[index],
                        ) { _, _ -> }
                    }
                }
                delay(1_000)
                val thermalBefore = power.currentThermalStatus
                val startedAt = SystemClock.elapsedRealtimeNanos()
                val completionMillis = LongArray(ids.size)
                coroutineScope {
                    val sampler =
                        launch(Dispatchers.IO) {
                            var lastPssAt = Long.MIN_VALUE
                            var pssKb = Debug.getPss()
                            while (isActive) {
                                val millis = (SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000
                                if (millis - lastPssAt >= 100 || lastPssAt == Long.MIN_VALUE) {
                                    pssKb = Debug.getPss()
                                    lastPssAt = millis
                                }
                                val runtime = Runtime.getRuntime()
                                samples +=
                                    MemorySample(
                                        millis,
                                        runtime.totalMemory() - runtime.freeMemory(),
                                        Debug.getNativeHeapAllocatedSize(),
                                        pssKb,
                                    )
                                delay(25)
                            }
                        }
                    try {
                        ids
                            .mapIndexed { index, id ->
                                async {
                                    engine.download(
                                        id,
                                        "$baseUrl/playlist.m3u8",
                                        "application/vnd.apple.mpegurl",
                                        emptyMap(),
                                        outputs[index],
                                    ) { _, _ -> }
                                    completionMillis[index] = (SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000
                                }
                            }.awaitAll()
                    } finally {
                        sampler.cancelAndJoin()
                    }
                }
                val elapsedMillis = completionMillis.max()
                val thermalAfter = power.currentThermalStatus
                outputs.forEach { output -> verifyOutput(output) }
                assertTrue(samples.isNotEmpty())
                val result =
                    JSONObject()
                        .put("label", label)
                        .put("parallelism", parallelism)
                        .put("cached", cached)
                        .put("elapsedMillis", elapsedMillis)
                        .put("completionMillis", JSONArray(completionMillis.toList()))
                        .put("outputBytes", JSONArray(outputs.map(File::length)))
                        .put("javaHeapLimitBytes", Runtime.getRuntime().maxMemory())
                        .put("thermalBefore", thermalBefore)
                        .put("thermalAfter", thermalAfter)
                        .put("samples", JSONArray(samples.map { it.json() }))
                File(context.filesDir, "export-concurrency").apply { mkdirs() }.resolve("$label.json").writeText(result.toString())
                println(
                    "EXPORT_MEASUREMENT $label elapsedMillis=$elapsedMillis javaPeak=${samples.maxOf {
                        it.javaBytes
                    }} nativePeak=${samples.maxOf { it.nativeBytes }} pssPeakKb=${samples.maxOf { it.pssKb }}",
                )
            } finally {
                ids.forEach { assertTrue(store.delete(it, null)) }
                store.streamDatabaseProvider.close()
            }
        }

    private fun verifyOutput(output: File) {
        assertTrue(output.length() > 0)
        val reader = MediaMetadataRetriever()
        try {
            reader.setDataSource(output.path)
            assertEquals("yes", reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO))
            assertEquals("yes", reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
            val duration = checkNotNull(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)).toLong()
            assertTrue("Unexpected duration: $duration", duration in 119_000..121_000)
        } finally {
            reader.release()
        }
    }

    private data class MemorySample(
        val millis: Long,
        val javaBytes: Long,
        val nativeBytes: Long,
        val pssKb: Long,
    ) {
        fun json(): JSONObject =
            JSONObject()
                .put("millis", millis)
                .put("javaBytes", javaBytes)
                .put("nativeBytes", nativeBytes)
                .put("pssKb", pssKb)
    }
}
