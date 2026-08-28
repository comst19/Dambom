package com.comst19.dambom.feature.library.pip

import androidx.compose.ui.unit.IntRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PipPlaybackPolicyTest {
    @Test
    fun `PiP is ineligible when the system feature is unavailable`() {
        val eligibility =
            PipEligibility(
                isSystemPipSupported = false,
                isFullscreen = true,
                isPlaying = true,
                hasCompletedLocalVideo = true,
            )

        assertFalse(eligibility.isEligible)
    }

    @Test
    fun `PiP is ineligible when playback is not fullscreen`() {
        val eligibility =
            PipEligibility(
                isSystemPipSupported = true,
                isFullscreen = false,
                isPlaying = true,
                hasCompletedLocalVideo = true,
            )

        assertFalse(eligibility.isEligible)
    }

    @Test
    fun `PiP is ineligible when the player is paused`() {
        val eligibility =
            PipEligibility(
                isSystemPipSupported = true,
                isFullscreen = true,
                isPlaying = false,
                hasCompletedLocalVideo = true,
            )

        assertFalse(eligibility.isEligible)
    }

    @Test
    fun `PiP is ineligible when no completed local video is present`() {
        val eligibility =
            PipEligibility(
                isSystemPipSupported = true,
                isFullscreen = true,
                isPlaying = true,
                hasCompletedLocalVideo = false,
            )

        assertFalse(eligibility.isEligible)
    }

    @Test
    fun `PiP is eligible only when every required fact is true`() {
        val eligibility =
            PipEligibility(
                isSystemPipSupported = true,
                isFullscreen = true,
                isPlaying = true,
                hasCompletedLocalVideo = true,
            )

        assertTrue(eligibility.isEligible)
    }

    @Test
    fun `16 by 9 video keeps its aspect ratio`() {
        assertEquals(PipAspectRatio(16, 9), pipAspectRatio(videoWidth = 16, videoHeight = 9))
    }

    @Test
    fun `9 by 16 video keeps its aspect ratio`() {
        assertEquals(PipAspectRatio(9, 16), pipAspectRatio(videoWidth = 9, videoHeight = 16))
    }

    @Test
    fun `zero or negative video dimensions do not create an aspect ratio`() {
        assertNull(pipAspectRatio(videoWidth = 0, videoHeight = 9))
        assertNull(pipAspectRatio(videoWidth = 16, videoHeight = 0))
        assertNull(pipAspectRatio(videoWidth = -16, videoHeight = 9))
        assertNull(pipAspectRatio(videoWidth = 16, videoHeight = -9))
    }

    @Test
    fun `extreme wide video clamps to the platform maximum`() {
        assertEquals(PipAspectRatio(239, 100), pipAspectRatio(videoWidth = Int.MAX_VALUE, videoHeight = 1))
    }

    @Test
    fun `extreme tall video clamps to the platform minimum`() {
        assertEquals(PipAspectRatio(100, 239), pipAspectRatio(videoWidth = 1, videoHeight = Int.MAX_VALUE))
    }

    @Test
    fun `landscape video is vertically letterboxed and centered in a portrait container`() {
        val bounds = fittedVideoBounds(container = IntRect(0, 0, 1_080, 1_920), videoWidth = 16, videoHeight = 9)

        assertEquals(IntRect(0, 656, 1_080, 1_264), bounds)
    }

    @Test
    fun `portrait video is horizontally letterboxed and centered in a landscape container`() {
        val bounds = fittedVideoBounds(container = IntRect(0, 0, 1_920, 1_080), videoWidth = 9, videoHeight = 16)

        assertEquals(IntRect(656, 0, 1_264, 1_080), bounds)
    }

    @Test
    fun `fitted bounds preserve a nonzero container origin`() {
        val bounds = fittedVideoBounds(container = IntRect(100, 200, 1_180, 2_120), videoWidth = 16, videoHeight = 9)

        assertEquals(IntRect(100, 856, 1_180, 1_464), bounds)
    }

    @Test
    fun `four by three video uses the centered fitted source rect instead of the full portrait window`() {
        val bounds = fittedVideoBounds(container = IntRect(100, 200, 1_180, 2_120), videoWidth = 4, videoHeight = 3)

        assertEquals(IntRect(100, 755, 1_180, 1_565), bounds)
    }

    @Test
    fun `twenty one by nine video uses the centered fitted source rect instead of the full portrait window`() {
        val bounds = fittedVideoBounds(container = IntRect(100, 200, 1_180, 2_120), videoWidth = 21, videoHeight = 9)

        assertEquals(IntRect(100, 928, 1_180, 1_391), bounds)
    }

    @Test
    fun `invalid or overflowing containers do not produce source bounds`() {
        assertNull(fittedVideoBounds(container = IntRect(0, 0, 0, 100), videoWidth = 16, videoHeight = 9))
        assertNull(fittedVideoBounds(container = IntRect(10, 0, 0, 100), videoWidth = 16, videoHeight = 9))
        assertNull(fittedVideoBounds(container = IntRect(Int.MIN_VALUE, 0, Int.MAX_VALUE, 100), videoWidth = 16, videoHeight = 9))
        assertNull(fittedVideoBounds(container = IntRect(0, 0, 100, 100), videoWidth = 0, videoHeight = 9))
        assertNull(fittedVideoBounds(container = IntRect(0, 0, 100, 100), videoWidth = 16, videoHeight = -9))
    }
}
