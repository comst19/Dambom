package com.comst19.dambom.feature.library.compress

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.core.domain.repository.DownloadRepository
import com.comst19.dambom.core.testing.MainDispatcherRule
import com.comst19.dambom.core.testing.SpyNavigationDispatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
class VideoCompressionViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val store = ViewModelStore()
    private val destination = Uri.parse("content://test.documents/compressed")

    @After fun tearDown() {
        store.clear()
    }

    @Test fun `pending metadata is shared and export waits for it`() =
        runTest(main.dispatcher) {
            val compressor = FakeCompressor().apply { inspection = CompletableDeferred() }
            val model = model(compressor)
            model.load("saved-video")
            model.load("saved-video")
            runCurrent()
            model.export("saved-video", destination)
            runCurrent()
            assertEquals(1, compressor.inspections)
            assertEquals(0, compressor.exports)
            compressor.inspection?.complete(source)
            runCurrent()
            assertEquals(1, compressor.exports)
            assertEquals(destination.toString(), model.state.value.savedUri)
            assertEquals(100L, model.state.value.savedBytes)
        }

    @Test fun `load failure discards the document and leaves an error`() =
        runTest(main.dispatcher) {
            val compressor = FakeCompressor().apply { failInspection = true }
            val model = model(compressor)
            model.export("saved-video", destination)
            runCurrent()
            assertEquals(0, compressor.exports)
            assertEquals(listOf(destination.toString()), compressor.discarded)
            assertEquals(CompressionFailure.FAILED, model.state.value.failure)
            assertFalse(model.state.value.exporting)
        }

    @Test fun `export freezes selection ignores duplicate requests and can cancel then retry`() =
        runTest(main.dispatcher) {
            val compressor = FakeCompressor().apply { exportGate = CompletableDeferred() }
            val model = model(compressor)
            model.export("saved-video", destination)
            runCurrent()
            model.select(CompressionProfile.SMALL)
            model.export("saved-video", destination)
            runCurrent()
            assertEquals(CompressionProfile.BALANCED, model.state.value.profile)
            assertEquals(1, compressor.exports)
            model.cancelExport()
            runCurrent()
            assertTrue(compressor.cancelled)
            assertFalse(model.state.value.exporting)
            assertNull(model.state.value.failure)
            assertNull(model.state.value.savedUri)
            compressor.exportGate = null
            model.select(CompressionProfile.SMALL)
            model.export("saved-video", destination)
            runCurrent()
            assertEquals(2, compressor.exports)
            assertEquals(CompressionProfile.SMALL, compressor.lastProfile)
            assertNotNull(model.state.value.savedUri)
        }

    @Test fun `a file that cannot be smaller has its own recoverable result`() =
        runTest(main.dispatcher) {
            val compressor = FakeCompressor().apply { notSmaller = true }
            val model = model(compressor)
            model.export("saved-video", destination)
            runCurrent()
            assertEquals(CompressionFailure.NOT_SMALLER, model.state.value.failure)
            assertNull(model.state.value.savedUri)
            model.select(CompressionProfile.SMALL)
            assertNull(model.state.value.failure)
            compressor.notSmaller = false
            model.export("saved-video", destination)
            runCurrent()
            assertNotNull(model.state.value.savedUri)
        }

    @Test fun `profile survives restoration and selecting another clears old output`() =
        runTest(main.dispatcher) {
            val saved = SavedStateHandle(mapOf("compression-profile" to "SMALL"))
            val model = model(FakeCompressor(), saved)
            assertEquals(CompressionProfile.SMALL, model.state.value.profile)
            model.export("saved-video", destination)
            runCurrent()
            assertEquals(destination.toString(), saved.get<String>("compression-saved-uri"))
            model.select(CompressionProfile.QUALITY)
            assertEquals("QUALITY", saved.get<String>("compression-profile"))
            assertNull(saved.get<String>("compression-saved-uri"))
            assertNull(model.state.value.savedBytes)
        }

    @Test
    fun `a second picker document is discarded without deleting the active destination`() =
        runTest(main.dispatcher) {
            val compressor = FakeCompressor().apply { exportGate = CompletableDeferred() }
            val model = model(compressor)
            model.export("saved-video", destination)
            runCurrent()
            val duplicate = Uri.parse("content://test.documents/duplicate")
            model.export("saved-video", duplicate)
            model.export("saved-video", destination)
            runCurrent()
            assertEquals(listOf(duplicate.toString()), compressor.discarded)
            assertEquals(1, compressor.exports)
            assertTrue(model.state.value.exporting)
            compressor.exportGate?.complete(Unit)
            runCurrent()
            assertEquals(destination.toString(), model.state.value.savedUri)
        }

    private fun model(
        compressor: FakeCompressor,
        saved: SavedStateHandle = SavedStateHandle(),
    ): VideoCompressionViewModel {
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
        return VideoCompressionViewModel(repository, compressor, SpyNavigationDispatcher(), saved)
            .also { store.put("compression", it) }
    }
}

private val source = CompressionSource(1920, 1080, 20_000L, 1_000_000L, 4_000_000)

private class FakeCompressor : VideoCompressor {
    var inspection: CompletableDeferred<CompressionSource>? = null
    var exportGate: CompletableDeferred<Unit>? = null
    var inspections = 0
    var exports = 0
    var failInspection = false
    var notSmaller = false
    var cancelled = false
    var lastProfile: CompressionProfile? = null
    val discarded = mutableListOf<String>()

    override suspend fun inspect(path: String): CompressionSource {
        inspections++
        if (failInspection) throw IOException("Missing video")
        return inspection?.await() ?: source
    }

    override suspend fun compress(
        path: String,
        profile: CompressionProfile,
        destination: String,
        onProgress: (Int?) -> Unit,
    ): Long {
        exports++
        lastProfile = profile
        if (notSmaller) throw CompressionNotSmallerException()
        try {
            exportGate?.await()
        } catch (failure: kotlinx.coroutines.CancellationException) {
            cancelled = true
            throw failure
        }
        onProgress(100)
        return 100L
    }

    override suspend fun discard(destination: String) {
        discarded += destination
    }
}
