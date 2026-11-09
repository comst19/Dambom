package com.comst19.dambom

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class SubtitleWorkRecoveryTest {
    @Test
    fun enqueueSubtitleForExternalForceStop() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("subtitleRecoverySeed") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = WorkManager.getInstance(context)
        val worker =
            Class
                .forName(
                    "com.comst19.dambom.feature.library.subtitle.work.SubtitleWorker",
                ).asSubclass(ListenableWorker::class.java)
        val request =
            OneTimeWorkRequest
                .Builder(worker)
                .setInputData(workDataOf("subtitle-video-id" to "recovery-missing-video", "subtitle-language-pair" to "en-US>ko"))
                .setInitialDelay(20, TimeUnit.SECONDS)
                .build()
        manager
            .enqueueUniqueWork("translated-subtitles:recovery-missing-video", ExistingWorkPolicy.REPLACE, request)
            .result
            .get(10, TimeUnit.SECONDS)
        assertEquals(WorkInfo.State.ENQUEUED, manager.getWorkInfoById(request.id).get(10, TimeUnit.SECONDS)?.state)
        println("RECOVERY_WORK_ID=${request.id}")
    }
}
