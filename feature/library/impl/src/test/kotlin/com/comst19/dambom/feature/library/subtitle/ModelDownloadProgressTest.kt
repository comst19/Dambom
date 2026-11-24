package com.comst19.dambom.feature.library.subtitle

import com.comst19.dambom.feature.library.subtitle.model.ModelDownloadProgress
import com.comst19.dambom.feature.library.subtitle.model.modelDownloadProgress
import com.comst19.dambom.feature.library.subtitle.work.modelDownloadProgress
import com.comst19.dambom.feature.library.subtitle.work.toWorkData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ModelDownloadProgressTest {
    @Test fun `unknown total and initial speed do not invent an estimate`() {
        assertNull(ModelDownloadProgress().fraction)
        assertNull(modelDownloadProgress(100, 200, 100).remainingSeconds)
    }

    @Test fun `progress and estimate survive persistence for screen reentry`() {
        val progress = modelDownloadProgress(250, 1_000, 10_000)
        assertEquals(0.25f, progress.fraction)
        assertEquals(30L, progress.remainingSeconds)
        assertEquals(progress, progress.toWorkData().modelDownloadProgress())
    }

    @Test fun `completed bytes never display negative remaining time`() {
        val progress = modelDownloadProgress(1_000, 1_000, 10_000)
        assertEquals(1f, progress.fraction)
        assertEquals(0L, progress.remainingSeconds)
    }
}
