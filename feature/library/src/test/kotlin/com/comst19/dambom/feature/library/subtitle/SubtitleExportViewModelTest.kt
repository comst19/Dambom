package com.comst19.dambom.feature.library.subtitle

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.core.common.io.videoSubtitleFile
import com.comst19.dambom.core.testing.MainDispatcherRule
import com.comst19.dambom.core.testing.SpyNavigationDispatcher
import com.comst19.dambom.feature.library.LibraryTestDownloads
import com.comst19.dambom.feature.library.libraryTestVideo
import com.comst19.dambom.feature.library.subtitle.data.SubtitleFiles
import com.comst19.dambom.feature.library.subtitle.export.SubtitleExportViewModel
import com.comst19.dambom.feature.library.subtitle.export.SubtitleVideoExporter
import com.comst19.dambom.feature.library.subtitle.model.SubtitleCue
import com.comst19.dambom.feature.library.subtitle.model.toSrt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class SubtitleExportViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val source =
        File.createTempFile("subtitle-export", ".mp4", context.cacheDir).apply { writeText("original") }
    private val task get() = libraryTestVideo(source.path)
    private val store = ViewModelStore()
    private val fake = FakeSubtitleVideoExporter()

    @After fun cleanup() {
        store.clear()
        source.videoSubtitleFile().delete()
        source.delete()
    }

    @Test fun `restored destination loads task and uses a snapshot of its saved subtitles`() =
        runTest(main.dispatcher) {
            val cues = listOf(SubtitleCue(1_000, 2_000, "안녕하세요"))
            source.videoSubtitleFile().writeText(cues.toSrt())
            val model = model()
            model.export(task.id, Uri.parse("content://test/video"))
            runCurrent()
            assertEquals(source.path, fake.path)
            assertEquals(cues, fake.cues)
            assertTrue(model.state.value.saved)
            assertEquals("original", source.readText())
            assertEquals(cues.toSrt(), source.videoSubtitleFile().readText())
        }

    @Test fun `missing subtitles discards destination instead of exporting a silent fallback`() =
        runTest(main.dispatcher) {
            val model = model()
            model.export(task.id, Uri.parse("content://test/empty"))
            runCurrent()
            assertEquals(listOf("content://test/empty"), fake.discarded)
            assertEquals(null, fake.path)
            assertTrue(model.state.value.failed)
            assertFalse(model.state.value.saved)
        }

    @Test fun `second destination is discarded while the first export keeps running`() =
        runTest(main.dispatcher) {
            source.videoSubtitleFile().writeText(listOf(SubtitleCue(0, 100, "text")).toSrt())
            fake.gate = CompletableDeferred()
            val model = model()
            model.export(task.id, Uri.parse("content://test/first"))
            runCurrent()
            model.export(task.id, Uri.parse("content://test/second"))
            runCurrent()
            assertEquals(listOf("content://test/second"), fake.discarded)
            assertTrue(model.state.value.exporting)
            model.cancel()
            runCurrent()
            assertFalse(model.state.value.exporting)
            assertFalse(model.state.value.saved)
            assertFalse(model.state.value.failed)
        }

    private fun model() =
        SubtitleExportViewModel(
            LibraryTestDownloads(listOf(task)),
            SubtitleFiles(context, main.dispatcher),
            fake,
            SpyNavigationDispatcher(),
        ).also { store.put("export", it) }
}

private class FakeSubtitleVideoExporter : SubtitleVideoExporter {
    var path: String? = null
    var cues: List<SubtitleCue>? = null
    var gate: CompletableDeferred<Unit>? = null
    val discarded = mutableListOf<String>()

    override suspend fun export(
        path: String,
        cues: List<SubtitleCue>,
        destination: String,
        onProgress: (Int?) -> Unit,
    ) {
        this.path = path
        this.cues = cues
        gate?.await()
    }

    override suspend fun discard(destination: String) {
        discarded.add(destination)
    }
}
