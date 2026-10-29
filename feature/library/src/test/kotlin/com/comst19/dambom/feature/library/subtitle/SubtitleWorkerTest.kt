package com.comst19.dambom.feature.library.subtitle

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.comst19.dambom.core.common.io.videoSubtitleFile
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.core.domain.repository.DownloadRepository
import com.comst19.dambom.feature.library.subtitle.data.SubtitleFiles
import com.comst19.dambom.feature.library.subtitle.inference.SubtitleEngine
import com.comst19.dambom.feature.library.subtitle.inference.SubtitleModelStatus
import com.comst19.dambom.feature.library.subtitle.model.ModelDownloadProgress
import com.comst19.dambom.feature.library.subtitle.model.SubtitleCue
import com.comst19.dambom.feature.library.subtitle.model.SubtitleFailure
import com.comst19.dambom.feature.library.subtitle.model.SubtitleLanguagePair
import com.comst19.dambom.feature.library.subtitle.model.parseGeneratedSrt
import com.comst19.dambom.feature.library.subtitle.work.SUBTITLE_ERROR
import com.comst19.dambom.feature.library.subtitle.work.SUBTITLE_PAIR
import com.comst19.dambom.feature.library.subtitle.work.SUBTITLE_VIDEO_ID
import com.comst19.dambom.feature.library.subtitle.work.SubtitleNotifier
import com.comst19.dambom.feature.library.subtitle.work.SubtitleWorker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SubtitleWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var source: File
    private var deletePending = false
    private val entered = CompletableDeferred<Unit>()
    private val release = CompletableDeferred<Unit>()
    private val cues = listOf(SubtitleCue(0, 1000, "번역 결과"))

    @Before fun setup() {
        source = File.createTempFile("subtitle-worker", ".mp4", context.cacheDir).apply { writeText("original") }
        val component = ComponentName(context.packageName, "TestLauncher")
        shadowOf(context.packageManager).apply {
            addActivityIfNotPresent(component)
            addIntentFilterForActivity(
                component,
                IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
            )
        }
        assertNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName))
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
    }

    @After fun cleanup() {
        source.videoSubtitleFile().delete()
        source.delete()
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    @Test fun `worker completes SRT and keeps original`() =
        runTest {
            val result = async { worker().doWork() }
            entered.await()
            assertFalse(source.videoSubtitleFile().exists())
            release.complete(Unit)
            assertEquals(ListenableWorker.Result.success(), result.await())
            assertEquals(cues, parseGeneratedSrt(source.videoSubtitleFile().readText()))
            assertEquals("original", source.readText())
        }

    @Test fun `cancelling running worker preserves earlier SRT`() =
        runTest {
            source.videoSubtitleFile().writeText("earlier")
            val result = async { worker().doWork() }
            entered.await()
            result.cancelAndJoin()
            assertEquals("earlier", source.videoSubtitleFile().readText())
            assertEquals("original", source.readText())
        }

    @Test fun `deleting a video during generation does not publish its subtitles`() =
        runTest {
            source.videoSubtitleFile().writeText("earlier")
            val result = async { worker().doWork() }
            entered.await()
            deletePending = true
            release.complete(Unit)
            val expected = ListenableWorker.Result.failure(workDataOf(SUBTITLE_ERROR to SubtitleFailure.UNKNOWN.name))
            assertEquals(expected, result.await())
            assertEquals("earlier", source.videoSubtitleFile().readText())
        }

    private fun worker(): SubtitleWorker {
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
                source.path,
                1,
                1,
            )
        val repository =
            Proxy.newProxyInstance(
                DownloadRepository::class.java.classLoader,
                arrayOf(DownloadRepository::class.java),
            ) { _, method, _ ->
                check(method.name == "observeDownload")
                flowOf(task.copy(deletePending = deletePending))
            } as DownloadRepository
        val engine =
            object : SubtitleEngine {
                override suspend fun status(
                    pair: SubtitleLanguagePair,
                    forceRefresh: Boolean,
                ) = SubtitleModelStatus.AVAILABLE

                override suspend fun download(
                    pair: SubtitleLanguagePair,
                    onBytes: (ModelDownloadProgress) -> Unit,
                ) = SubtitleModelStatus.AVAILABLE

                override suspend fun deleteModel() = Unit

                override suspend fun generate(
                    path: String,
                    pair: SubtitleLanguagePair,
                    onProgress: (Long) -> Unit,
                ): List<SubtitleCue> {
                    entered.complete(Unit)
                    release.await()
                    onProgress(1000)
                    return cues
                }
            }
        return TestListenableWorkerBuilder<SubtitleWorker>(context)
            .setInputData(
                workDataOf(SUBTITLE_VIDEO_ID to "video", SUBTITLE_PAIR to SubtitleLanguagePair.ENGLISH_KOREAN.name),
            ).setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker =
                        SubtitleWorker(
                            appContext,
                            workerParameters,
                            repository,
                            engine,
                            SubtitleFiles(context, Dispatchers.IO),
                            SubtitleNotifier(context),
                        )
                },
            ).build()
    }
}
