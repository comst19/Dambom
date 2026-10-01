package com.comst19.dambom.feature.library.trim

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.core.domain.repository.DownloadRepository
import com.comst19.dambom.core.testing.MainDispatcherRule
import com.comst19.dambom.core.testing.SpyNavigationDispatcher
import com.comst19.dambom.feature.library.trim.contract.TrimSelection
import com.comst19.dambom.feature.library.trim.export.ClipExporter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class VideoTrimViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val store = ViewModelStore()

    @After
    fun tearDown() {
        store.clear()
    }

    @Test
    fun `repeated load requests share one pending metadata read`() =
        runTest(main.dispatcher) {
            val exporter = FakeClipExporter().apply { durationGate = CompletableDeferred() }
            val model = model(exporter)
            backgroundScope.launch { model.load("saved-video") }
            backgroundScope.launch { model.load("saved-video") }
            runCurrent()
            val reads = exporter.durationCalls
            exporter.durationGate?.complete(20_000L)
            runCurrent()
            assertEquals(1, reads)
            assertFalse(model.state.value.loading)
        }

    @Test
    fun `leaving a composition does not cancel the ViewModel metadata load`() =
        runTest(main.dispatcher) {
            val exporter = FakeClipExporter().apply { durationGate = CompletableDeferred() }
            val model = model(exporter)
            val composition = backgroundScope.launch { model.load("saved-video") }
            runCurrent()
            composition.cancel()
            runCurrent()
            exporter.durationGate?.complete(20_000L)
            runCurrent()
            assertFalse(model.state.value.loading)
            assertEquals(
                "saved-video",
                model.state.value.task
                    ?.id,
            )
        }

    @Test
    fun `export waits for the existing load and duplicate export is ignored`() =
        runTest(main.dispatcher) {
            val exporter = FakeClipExporter().apply { durationGate = CompletableDeferred() }
            val model = model(exporter)
            backgroundScope.launch { model.load("saved-video") }
            runCurrent()
            model.export("saved-video", destination)
            model.export("saved-video", destination)
            runCurrent()
            assertEquals(0, exporter.exports)
            val reads = exporter.durationCalls
            exporter.durationGate?.complete(20_000L)
            runCurrent()
            assertEquals(1, reads)
            assertEquals(1, exporter.exports)
            assertEquals(destination.toString(), model.state.value.savedUri)
        }

    @Test
    fun `load failure discards the created document without exporting an empty path`() =
        runTest(main.dispatcher) {
            val exporter = FakeClipExporter().apply { failDuration = true }
            val model = model(exporter)
            model.export("saved-video", destination)
            runCurrent()
            assertEquals(0, exporter.exports)
            assertEquals(listOf(destination.toString()), exporter.discarded)
            assertTrue(model.state.value.exportFailed)
            assertFalse(model.state.value.exporting)
        }

    @Test
    fun `cancel before metadata completes still discards the created document`() =
        runTest(main.dispatcher) {
            val exporter = FakeClipExporter().apply { durationGate = CompletableDeferred() }
            val model = model(exporter)
            model.export("saved-video", destination)
            model.cancelExport()
            runCurrent()
            exporter.durationGate?.complete(20_000L)
            runCurrent()
            assertEquals(listOf(destination.toString()), exporter.discarded)
            assertFalse(model.state.value.exporting)
            assertFalse(model.state.value.exportFailed)
        }

    @Test
    fun `selection is restored and cannot change during export then cancellation clears progress`() =
        runTest(main.dispatcher) {
            val saved = SavedStateHandle(mapOf("trim-start" to 1000L, "trim-end" to 5000L))
            val exporter = FakeClipExporter().apply { exportGate = CompletableDeferred() }
            val model = model(exporter, saved)
            model.load("saved-video")
            runCurrent()
            assertEquals(TrimSelection(1000L, 5000L), model.state.value.selection)
            model.export("saved-video", destination)
            runCurrent()
            model.select(TrimSelection(0L, 20_000L))
            assertEquals(TrimSelection(1000L, 5000L), model.state.value.selection)
            model.cancelExport()
            runCurrent()
            assertFalse(model.state.value.exporting)
            assertNull(model.state.value.progress)
            assertTrue(exporter.cancelled)
        }

    @Test
    fun `invalid restored selection resets and failed export can be retried`() =
        runTest(main.dispatcher) {
            val exporter = FakeClipExporter().apply { failExport = true }
            val saved = SavedStateHandle(mapOf("trim-start" to 30_000L, "trim-end" to 40_000L))
            val model = model(exporter, saved)
            model.load("saved-video")
            runCurrent()
            assertEquals(TrimSelection(0L, 20_000L), model.state.value.selection)
            model.export("saved-video", destination)
            runCurrent()
            assertTrue(model.state.value.exportFailed)
            exporter.failExport = false
            model.export("saved-video", destination)
            runCurrent()
            assertFalse(model.state.value.exportFailed)
            assertEquals(destination.toString(), saved.get<String>("trim-saved-uri"))
        }

    private fun model(
        exporter: FakeClipExporter,
        saved: SavedStateHandle = SavedStateHandle(),
    ): VideoTrimViewModel {
        val video =
            DownloadTask(
                id = "saved-video",
                url = "https://example.com/video.mp4",
                sourcePageUrl = "https://example.com",
                title = "Saved video",
                mimeType = "video/mp4",
                expectedBytes = null,
                downloadedBytes = 1L,
                quality = "original",
                status = DownloadStatus.COMPLETED,
                failureReason = null,
                localFileName = "video.mp4",
                localFilePath = "/test/video.mp4",
                createdAtMillis = 1L,
                updatedAtMillis = 1L,
            )
        val repository =
            Proxy.newProxyInstance(
                DownloadRepository::class.java.classLoader,
                arrayOf(DownloadRepository::class.java),
            ) { _, method, arguments ->
                check(method.name == "observeDownload") { "Unexpected repository call: ${method.name}" }
                flowOf(video.takeIf { it.id == arguments?.firstOrNull() })
            } as DownloadRepository
        return VideoTrimViewModel(
            repository,
            exporter,
            SpyNavigationDispatcher(),
            saved,
        ).also { store.put("trim", it) }
    }

    private val destination = Uri.parse("content://test.documents/clip")
}

private class FakeClipExporter : ClipExporter {
    var durationGate: CompletableDeferred<Long>? = null
    var exportGate: CompletableDeferred<Unit>? = null
    var durationCalls = 0
    var exports = 0
    var failDuration = false
    var failExport = false
    var cancelled = false
    val discarded = mutableListOf<String>()

    override suspend fun durationMillis(path: String): Long {
        durationCalls++
        if (failDuration) throw IOException("Missing input")
        return durationGate?.await() ?: 20_000L
    }

    override suspend fun export(
        path: String,
        selection: TrimSelection,
        destination: String,
        onProgress: (Int?) -> Unit,
    ) {
        exports++
        require(path.isNotBlank())
        if (failExport) throw IOException("Export failed")
        onProgress(25)
        try {
            exportGate?.await()
        } catch (failure: kotlinx.coroutines.CancellationException) {
            cancelled = true
            throw failure
        }
    }

    override suspend fun discard(destination: String) {
        discarded += destination
    }
}
