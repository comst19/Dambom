package com.comst19.dambom.feature.library.subtitle

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.core.common.io.videoSubtitleFile
import com.comst19.dambom.core.common.io.videoSubtitleTemporaryFile
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.core.domain.repository.DownloadRepository
import com.comst19.dambom.core.testing.MainDispatcherRule
import com.comst19.dambom.core.testing.SpyNavigationDispatcher
import com.comst19.dambom.feature.library.subtitle.data.SubtitleFiles
import com.comst19.dambom.feature.library.subtitle.inference.SubtitleEngine
import com.comst19.dambom.feature.library.subtitle.inference.SubtitleModelStatus
import com.comst19.dambom.feature.library.subtitle.model.ModelDownloadProgress
import com.comst19.dambom.feature.library.subtitle.model.SubtitleCue
import com.comst19.dambom.feature.library.subtitle.model.SubtitleLanguagePair
import com.comst19.dambom.feature.library.subtitle.model.parseGeneratedSrt
import com.comst19.dambom.feature.library.subtitle.model.toSrt
import com.comst19.dambom.feature.library.subtitle.work.SubtitleJob
import com.comst19.dambom.feature.library.subtitle.work.SubtitleJobs
import com.comst19.dambom.feature.library.subtitle.work.SubtitleModelDownload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class SubtitleViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val store = ViewModelStore()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var source: File
    private val engine = FakeSubtitleEngine()
    private val background = CoroutineScope(SupervisorJob() + main.dispatcher)
    private val jobs by lazy {
        FakeSubtitleJobs(engine, SubtitleFiles(context, main.dispatcher), { source.path }, background)
    }

    @Before fun setup() {
        source = File.createTempFile("subtitle-test", ".mp4", context.cacheDir).apply { writeText("original") }
    }

    @After fun cleanup() {
        store.clear()
        background.cancel()
        source.videoSubtitleFile().delete()
        source.videoSubtitleTemporaryFile().delete()
        source.delete()
    }

    @Test fun `opening checks support without downloading or running inference`() =
        runTest(main.dispatcher) {
            val model = model()
            model.load("video")
            runCurrent()
            assertEquals(SubtitleModelStatus.DOWNLOADABLE, model.state.value.model)
            assertEquals(0, engine.downloads)
            assertEquals(0, engine.generations)
            model.generate()
            runCurrent()
            assertEquals(0, engine.generations)
        }

    @Test fun `unsupported pair never downloads and explicit download is single flight`() =
        runTest(main.dispatcher) {
            val model = model()
            engine.available = SubtitleModelStatus.UNAVAILABLE
            model.load("video")
            runCurrent()
            model.download()
            assertEquals(0, engine.downloads)
            engine.available = SubtitleModelStatus.DOWNLOADABLE
            model.refresh()
            runCurrent()
            engine.downloadGate = CompletableDeferred()
            model.download()
            model.download()
            assertEquals(1, engine.downloads)
            engine.downloadGate?.complete(Unit)
            runCurrent()
            assertEquals(SubtitleModelStatus.AVAILABLE, model.state.value.model)
            assertEquals(0, engine.generations)
        }

    @Test fun `generation keeps original publishes SRT and restores it on reentry`() =
        runTest(main.dispatcher) {
            engine.available = SubtitleModelStatus.AVAILABLE
            val model = model()
            model.load("video")
            runCurrent()
            model.select(SubtitleLanguagePair.JAPANESE_ENGLISH)
            runCurrent()
            model.generate()
            runCurrent()
            assertEquals(SubtitleLanguagePair.JAPANESE_ENGLISH, engine.generatedPair)
            assertEquals("original", source.readText())
            assertEquals(engine.cues, parseGeneratedSrt(source.videoSubtitleFile().readText()))
            assertFalse(source.videoSubtitleTemporaryFile().exists())
            val reopened = model("reopened")
            reopened.load("video")
            runCurrent()
            assertEquals(engine.cues, reopened.state.value.cues)
            assertEquals(SubtitleLanguagePair.JAPANESE_ENGLISH, reopened.state.value.pair)
        }

    @Test fun `cancelled generation keeps earlier subtitles and permits retry`() =
        runTest(main.dispatcher) {
            engine.available = SubtitleModelStatus.AVAILABLE
            source.videoSubtitleFile().writeText(listOf(SubtitleCue(0, 300, "earlier")).toSrt())
            val before = source.videoSubtitleFile().readText()
            val model = model()
            model.load("video")
            runCurrent()
            engine.generationGate = CompletableDeferred()
            model.generate()
            model.generate()
            assertEquals(1, engine.generations)
            model.select(SubtitleLanguagePair.JAPANESE_ENGLISH)
            assertEquals(SubtitleLanguagePair.ENGLISH_KOREAN, model.state.value.pair)
            model.cancel()
            runCurrent()
            assertEquals(before, source.videoSubtitleFile().readText())
            assertFalse(model.state.value.busy)
            assertFalse(model.state.value.failed)
            engine.generationGate = null
            model.generate()
            runCurrent()
            assertEquals(2, engine.generations)
        }

    @Test fun `empty model result is a failure and does not replace existing subtitles`() =
        runTest(main.dispatcher) {
            engine.available = SubtitleModelStatus.AVAILABLE
            source.videoSubtitleFile().writeText("keep")
            engine.cues = emptyList()
            val model = model()
            model.load("video")
            runCurrent()
            model.generate()
            runCurrent()
            assertTrue(model.state.value.failed)
            assertEquals("keep", source.videoSubtitleFile().readText())
        }

    @Test fun `generation continues after the view model is cleared`() =
        runTest(main.dispatcher) {
            engine.available = SubtitleModelStatus.AVAILABLE
            engine.generationGate = CompletableDeferred()
            val model = model()
            model.load("video")
            runCurrent()
            model.generate()
            runCurrent()
            store.clear()
            engine.generationGate?.complete(Unit)
            runCurrent()
            assertTrue(source.videoSubtitleFile().isFile)
            assertEquals(engine.cues, parseGeneratedSrt(source.videoSubtitleFile().readText()))
            val reopened = model("reopened")
            reopened.load("video")
            runCurrent()
            assertEquals(engine.cues, reopened.state.value.cues)
        }

    @Test fun `reentry restores generation before a slow support check finishes`() =
        runTest(main.dispatcher) {
            engine.available = SubtitleModelStatus.AVAILABLE
            engine.generationGate = CompletableDeferred()
            val first = model()
            first.load("video")
            runCurrent()
            first.generate()
            runCurrent()
            engine.statusGate = CompletableDeferred()
            val reopened = model("reopened")
            reopened.load("video")
            runCurrent()
            assertEquals(SubtitleOperation.GENERATING, reopened.state.value.operation)
            engine.statusGate?.complete(Unit)
            runCurrent()
            assertEquals(SubtitleOperation.GENERATING, reopened.state.value.operation)
            assertTrue(reopened.state.value.busy)
            reopened.cancel()
            runCurrent()
        }

    @Test fun `failed update keeps the installed model usable`() =
        runTest(main.dispatcher) {
            engine.available = SubtitleModelStatus.AVAILABLE
            val model = model()
            model.load("video")
            runCurrent()
            engine.downloadFailure = true
            model.download()
            runCurrent()
            assertTrue(model.state.value.failed)
            assertEquals(SubtitleModelStatus.AVAILABLE, model.state.value.model)
            model.generate()
            runCurrent()
            assertEquals(1, engine.generations)
        }

    @Test fun `known model status remains visible while support is refreshed in the background`() =
        runTest(main.dispatcher) {
            engine.cached = SubtitleModelStatus.AVAILABLE
            engine.available = SubtitleModelStatus.AVAILABLE
            engine.statusGate = CompletableDeferred()
            val model = model()
            assertEquals(SubtitleModelStatus.AVAILABLE, model.state.value.model)
            model.load("video")
            runCurrent()
            assertEquals(SubtitleModelStatus.AVAILABLE, model.state.value.model)
            assertEquals(SubtitleOperation.IDLE, model.state.value.operation)
            engine.statusGate?.complete(Unit)
            runCurrent()
        }

    @Test fun `model download survives view model removal and reentry restores progress`() =
        runTest(main.dispatcher) {
            engine.downloadGate = CompletableDeferred()
            val first = model()
            first.load("video")
            runCurrent()
            first.download()
            runCurrent()
            store.clear()
            val reopened = model("reopened")
            reopened.load("video")
            runCurrent()
            assertEquals(SubtitleOperation.DOWNLOADING, reopened.state.value.operation)
            assertEquals(1, engine.downloads)
            engine.downloadGate?.complete(Unit)
            runCurrent()
            assertEquals(SubtitleOperation.IDLE, reopened.state.value.operation)
            assertEquals(SubtitleModelStatus.AVAILABLE, reopened.state.value.model)
        }

    @Test fun `cancelling model download from reopened screen permits another request`() =
        runTest(main.dispatcher) {
            engine.downloadGate = CompletableDeferred()
            val first = model()
            first.load("video")
            runCurrent()
            first.download()
            runCurrent()
            val reopened = model("reopened")
            reopened.load("video")
            runCurrent()
            reopened.cancel()
            runCurrent()
            assertEquals(SubtitleOperation.IDLE, reopened.state.value.operation)
            assertFalse(reopened.state.value.failed)
            engine.downloadGate = null
            reopened.download()
            runCurrent()
            assertEquals(2, engine.downloads)
        }

    @Test fun `historical generation failure is not shown as a new error on reentry`() =
        runTest(main.dispatcher) {
            engine.available = SubtitleModelStatus.AVAILABLE
            engine.cues = emptyList()
            val first = model()
            first.load("video")
            runCurrent()
            first.generate()
            runCurrent()
            assertTrue(first.state.value.failed)
            val reopened = model("reopened")
            reopened.load("video")
            runCurrent()
            assertFalse(reopened.state.value.failed)
        }

    @Test fun `historical model preparation failure does not flash an error on entry`() =
        runTest(main.dispatcher) {
            val first = model()
            first.load("video")
            runCurrent()
            engine.downloadFailure = true
            first.download()
            runCurrent()
            assertTrue(first.state.value.failed)
            val reopened = model("reopened")
            reopened.load("video")
            runCurrent()
            assertFalse(reopened.state.value.failed)
        }

    private fun model(key: String = "model"): SubtitleViewModel {
        val task =
            DownloadTask(
                "video",
                "https://example.com/v",
                "https://example.com",
                "Video",
                "video/mp4",
                null,
                1L,
                "original",
                DownloadStatus.COMPLETED,
                null,
                source.name,
                source.absolutePath,
                1,
                1,
            )
        val repository =
            Proxy.newProxyInstance(
                DownloadRepository::class.java.classLoader,
                arrayOf(DownloadRepository::class.java),
            ) { _, method, _ ->
                check(method.name == "observeDownload")
                flowOf(task)
            } as DownloadRepository
        return SubtitleViewModel(
            repository,
            engine,
            SubtitleFiles(context, main.dispatcher),
            SpyNavigationDispatcher(),
            SavedStateHandle(),
            jobs,
        ).also { store.put(key, it) }
    }
}

private class FakeSubtitleEngine : SubtitleEngine {
    override suspend fun deleteModel() {
        available = SubtitleModelStatus.DOWNLOADABLE
    }

    var available = SubtitleModelStatus.DOWNLOADABLE
    var cached: SubtitleModelStatus? = null

    override fun cachedStatus(pair: SubtitleLanguagePair) = cached

    var downloadFailure = false
    var downloads = 0
    var generations = 0
    var generatedPair: SubtitleLanguagePair? = null
    var statusGate: CompletableDeferred<Unit>? = null
    var downloadGate: CompletableDeferred<Unit>? = null
    var generationGate: CompletableDeferred<Unit>? = null
    var cues = listOf(SubtitleCue(0, 800, "안녕하세요"))

    override suspend fun status(
        pair: SubtitleLanguagePair,
        forceRefresh: Boolean,
    ): SubtitleModelStatus {
        statusGate?.await()
        return available
    }

    override suspend fun download(
        pair: SubtitleLanguagePair,
        onBytes: (ModelDownloadProgress) -> Unit,
    ): SubtitleModelStatus {
        downloads++
        check(!downloadFailure)
        downloadGate?.await()
        available = SubtitleModelStatus.AVAILABLE
        return available
    }

    override suspend fun generate(
        path: String,
        pair: SubtitleLanguagePair,
        onProgress: (Long) -> Unit,
    ): List<SubtitleCue> {
        generations++
        generatedPair = pair
        generationGate?.await()
        return cues
    }
}

private class FakeSubtitleJobs(
    private val engine: SubtitleEngine,
    private val files: SubtitleFiles,
    private val path: () -> String,
    private val scope: CoroutineScope,
) : SubtitleJobs {
    private val state = MutableStateFlow<SubtitleJob?>(null)
    private var pending: Job? = null
    private val modelState = MutableStateFlow<SubtitleModelDownload?>(null)
    private var modelPending: Job? = null

    override fun observeModelDownload() = modelState

    override suspend fun enqueueModelDownload(pair: SubtitleLanguagePair) {
        if (modelPending?.isActive == true) return
        modelPending =
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                modelState.value = SubtitleModelDownload(true, false, false)
                try {
                    engine.download(pair) { modelState.value = SubtitleModelDownload(true, false, false, it) }
                    modelState.value = SubtitleModelDownload(false, true, false)
                } catch (cancelled: CancellationException) {
                    modelState.value = SubtitleModelDownload(false, false, false)
                    throw cancelled
                } catch (_: Exception) {
                    modelState.value = SubtitleModelDownload(false, false, true)
                }
            }
    }

    override suspend fun cancelModelDownload() {
        modelPending?.cancel()
    }

    override fun observe(videoId: String) = state

    override suspend fun enqueue(
        videoId: String,
        pair: SubtitleLanguagePair,
    ) {
        if (pending?.isActive == true) return
        pending =
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                state.value = SubtitleJob(true, false, false, pair)
                try {
                    val cues = engine.generate(path(), pair) {}
                    check(cues.isNotEmpty())
                    files.save(path(), cues)
                    state.value = SubtitleJob(false, true, false, pair)
                } catch (cancelled: CancellationException) {
                    state.value = SubtitleJob(false, false, false, pair)
                    throw cancelled
                } catch (_: Exception) {
                    state.value = SubtitleJob(false, false, true, pair)
                }
            }
    }

    override suspend fun cancel(videoId: String) {
        pending?.cancel()
    }
}
