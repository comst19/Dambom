package com.comst19.dambom.feature.library.pip

import androidx.compose.ui.unit.IntRect
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PipPlatformControllerTest {
    @Test
    fun `same task id becoming completed with a local file refreshes PiP params to eligible`() {
        val platform = FakePipPlatform(apiLevel = 31)
        val controller = PipPlatformController(platform)
        val downloading = task(status = DownloadStatus.DOWNLOADING, localFilePath = null)
        val completed = task(status = DownloadStatus.COMPLETED, localFilePath = "/files/pip-qa.mp4")

        controller.update(
            pipPlatformState(
                isSystemPipSupported = true,
                isFullscreen = true,
                isPlaying = true,
                task = downloading,
                aspectRatio = PipAspectRatio(16, 9),
                sourceBounds = IntRect(0, 100, 1_080, 708),
            ),
        )
        assertFalse(platform.lastParams.enabled)

        controller.update(
            pipPlatformState(
                isSystemPipSupported = true,
                isFullscreen = true,
                isPlaying = true,
                task = completed,
                aspectRatio = PipAspectRatio(16, 9),
                sourceBounds = IntRect(0, 100, 1_080, 708),
            ),
        )

        assertTrue(platform.lastParams.enabled)
        assertTrue(platform.lastParams.autoEnterEnabled)
    }

    @Test
    fun `eligible API 31 state enables auto enter with fitted video parameters`() {
        val platform = FakePipPlatform(apiLevel = 31)
        val controller = PipPlatformController(platform)

        controller.update(
            PipPlatformState(
                eligibility = eligible(),
                aspectRatio = PipAspectRatio(16, 9),
                sourceBounds = IntRect(0, 100, 1_080, 708),
            ),
        )

        assertTrue(platform.lastParams.enabled)
        assertTrue(platform.lastParams.autoEnterEnabled)
        assertEquals(PipAspectRatio(16, 9), platform.lastParams.aspectRatio)
        assertEquals(IntRect(0, 100, 1_080, 708), platform.lastParams.sourceBounds)
        assertTrue(platform.lastParams.seamlessResizeEnabled)
    }

    @Test
    fun `API 26 user leave enters only from the latest eligible state and dispose disables params`() {
        val platform = FakePipPlatform(apiLevel = 26)
        val controller = PipPlatformController(platform)
        controller.update(PipPlatformState(eligibility = ineligible()))

        controller.onUserLeaveHint()
        assertEquals(0, platform.enterCalls)

        controller.update(PipPlatformState(eligibility = eligible()))
        controller.onUserLeaveHint()
        assertEquals(1, platform.enterCalls)

        controller.dispose()
        assertFalse(platform.lastParams.enabled)
    }

    @Test
    fun `PiP and API 35 transition hide fullscreen chrome`() {
        assertTrue(isVideoOnlyPipContent(isInPictureInPictureMode = true, isTransitioningToPip = false))
        assertTrue(isVideoOnlyPipContent(isInPictureInPictureMode = false, isTransitioningToPip = true))
        assertFalse(isVideoOnlyPipContent(isInPictureInPictureMode = false, isTransitioningToPip = false))
    }

    private fun eligible() =
        PipEligibility(
            isSystemPipSupported = true,
            isFullscreen = true,
            isPlaying = true,
            hasCompletedLocalVideo = true,
        )

    private fun ineligible() = eligible().copy(isPlaying = false)

    private fun task(
        status: DownloadStatus,
        localFilePath: String?,
    ): DownloadTask =
        DownloadTask(
            id = "same-id",
            url = "https://example.com/video.mp4",
            sourcePageUrl = "https://example.com",
            title = "video",
            mimeType = "video/mp4",
            expectedBytes = 100L,
            downloadedBytes = 100L,
            quality = "original",
            status = status,
            failureReason = null,
            localFileName = "pip-qa.mp4",
            localFilePath = localFilePath,
            createdAtMillis = 1L,
            updatedAtMillis = 1L,
        )
}

private class FakePipPlatform(
    override val apiLevel: Int,
) : PipPlatform {
    var lastParams = PipPlatformParams.Disabled
    var enterCalls = 0

    override fun setParams(params: PipPlatformParams) {
        lastParams = params
    }

    override fun enterPictureInPictureMode() {
        enterCalls += 1
    }
}
