package com.comst19.dambom.feature.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackVisibilityStateTest {
    @Test
    fun `playing media resumes once after an adaptive layout transition`() {
        val state = PlaybackVisibilityState()

        state.onHidden(wasPlayWhenReady = true)

        assertTrue(state.consumeResumeRequest())
        assertFalse(state.consumeResumeRequest())
    }

    @Test
    fun `paused media stays paused after an adaptive layout transition`() {
        val state = PlaybackVisibilityState()

        state.onHidden(wasPlayWhenReady = false)

        assertFalse(state.consumeResumeRequest())
    }
}
