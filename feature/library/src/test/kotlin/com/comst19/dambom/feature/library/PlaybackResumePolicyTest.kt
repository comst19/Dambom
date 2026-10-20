package com.comst19.dambom.feature.library

import com.comst19.dambom.core.domain.model.PlaybackHistory
import com.comst19.dambom.feature.library.player.resumePosition
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackResumePolicyTest {
    @Test
    fun `resume restores progress but resets completed and last second positions`() {
        assertEquals(0L, resumePosition(null))
        assertEquals(4_000L, resumePosition(history(4_000L)))
        assertEquals(8_999L, resumePosition(history(8_999L)))
        assertEquals(0L, resumePosition(history(9_000L)))
        assertEquals(0L, resumePosition(history(10_000L)))
        assertEquals(0L, resumePosition(history(11_000L)))
        assertEquals(0L, resumePosition(history(-1L)))
        assertEquals(0L, resumePosition(history(100L).copy(durationMillis = 0L)))
    }
}

private fun history(position: Long) = PlaybackHistory("video", position, 10_000L, 100L)
