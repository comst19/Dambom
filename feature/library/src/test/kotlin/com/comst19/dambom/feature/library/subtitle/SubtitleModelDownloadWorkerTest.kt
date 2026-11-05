package com.comst19.dambom.feature.library.subtitle

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.comst19.dambom.feature.library.subtitle.inference.SubtitleEngine
import com.comst19.dambom.feature.library.subtitle.inference.SubtitleModelStatus
import com.comst19.dambom.feature.library.subtitle.model.ModelDownloadProgress
import com.comst19.dambom.feature.library.subtitle.model.SubtitleCue
import com.comst19.dambom.feature.library.subtitle.model.SubtitleLanguagePair
import com.comst19.dambom.feature.library.subtitle.work.SUBTITLE_PAIR
import com.comst19.dambom.feature.library.subtitle.work.SubtitleModelDownloadWorker
import com.comst19.dambom.feature.library.subtitle.work.SubtitleNotifier
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SubtitleModelDownloadWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val started = CompletableDeferred<Unit>()
    private val release = CompletableDeferred<Unit>()
    private var cancelled = false
    private var result = SubtitleModelStatus.AVAILABLE

    @Before fun setup() {
        val component = ComponentName(context.packageName, "TestLauncher")
        shadowOf(context.packageManager).apply {
            addActivityIfNotPresent(component)
            addIntentFilterForActivity(
                component,
                IntentFilter(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                },
            )
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
    }

    @After fun cleanup() {
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    @Test fun `model worker finishes only after download and validation complete`() =
        runTest {
            val pending = async { worker().doWork() }
            started.await()
            assertTrue(pending.isActive)
            release.complete(Unit)
            assertEquals(ListenableWorker.Result.success(), pending.await())
        }

    @Test fun `cancelling model worker cancels its engine download`() =
        runTest {
            val pending = async { worker().doWork() }
            started.await()
            pending.cancelAndJoin()
            assertTrue(cancelled)
        }

    @Test fun `system speech download still preparing is retried instead of reported ready`() =
        runTest {
            result = SubtitleModelStatus.DOWNLOADING
            release.complete(Unit)
            assertEquals(ListenableWorker.Result.retry(), worker().doWork())
        }

    @Test fun `model transfer declares data sync foreground type`() {
        val info = SubtitleNotifier(context).modelForeground(UUID.randomUUID(), ModelDownloadProgress())
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, info.foregroundServiceType)
    }

    private fun worker(): SubtitleModelDownloadWorker {
        val engine =
            object : SubtitleEngine {
                override suspend fun deleteModel() = Unit

                override suspend fun status(
                    pair: SubtitleLanguagePair,
                    forceRefresh: Boolean,
                ) = SubtitleModelStatus.DOWNLOADABLE

                override suspend fun generate(
                    path: String,
                    pair: SubtitleLanguagePair,
                    onProgress: (Long) -> Unit,
                ): List<SubtitleCue> = error("Model preparation must not run inference")

                override suspend fun download(
                    pair: SubtitleLanguagePair,
                    onBytes: (ModelDownloadProgress) -> Unit,
                ): SubtitleModelStatus {
                    started.complete(Unit)
                    try {
                        release.await()
                        onBytes(ModelDownloadProgress(100, 100))
                        return result
                    } finally {
                        cancelled = !release.isCompleted
                    }
                }
            }
        return TestListenableWorkerBuilder<SubtitleModelDownloadWorker>(context)
            .setInputData(workDataOf(SUBTITLE_PAIR to SubtitleLanguagePair.ENGLISH_KOREAN.name))
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker =
                        SubtitleModelDownloadWorker(
                            context = appContext,
                            parameters = workerParameters,
                            engine = engine,
                            notifier = SubtitleNotifier(context),
                        )
                },
            ).build()
    }
}
