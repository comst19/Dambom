package com.comst19.dambom.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoTimeTextTest {
    @Test
    fun `duration is formatted for cards and player details`() {
        assertEquals("0:00", (-1L).toTimeText())
        assertEquals("2:03", 123_000L.toTimeText())
        assertEquals("1:02:03", 3_723_000L.toTimeText())
    }
}
