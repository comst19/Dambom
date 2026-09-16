package com.comst19.dambom.core.common.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DambomSeekBarPolicyTest {
    @Test
    fun `touch position maps to a clamped progress value`() {
        assertEquals(0f, seekBarValueFromTouch(x = -1f, width = 100f), 0f)
        assertEquals(0.25f, seekBarValueFromTouch(x = 25f, width = 100f), 0f)
        assertEquals(1f, seekBarValueFromTouch(x = 120f, width = 100f), 0f)
    }

    @Test
    fun `zero width maps to zero progress`() {
        assertEquals(0f, seekBarValueFromTouch(x = 25f, width = 0f), 0f)
    }

    @Test
    fun `only positive duration enables seeking`() {
        assertTrue(isDambomSeekBarEnabled(durationMillis = 1L))
        assertFalse(isDambomSeekBarEnabled(durationMillis = 0L))
        assertFalse(isDambomSeekBarEnabled(durationMillis = -1L))
    }

    @Test
    fun `non finite values normalize to zero before drawing or seeking`() {
        assertEquals(0f, normalizedSeekBarValue(Float.NaN), 0f)
        assertEquals(0f, normalizedSeekBarValue(Float.POSITIVE_INFINITY), 0f)
        assertEquals(0f, normalizedSeekBarValue(Float.NEGATIVE_INFINITY), 0f)
    }
}
