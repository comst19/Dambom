package com.comst19.dambom.feature.library.trim

import com.comst19.dambom.feature.library.trim.contract.TrimSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrimSelectionTest {
    @Test
    fun `selection rejects reversed empty negative and out of bounds ranges`() {
        listOf(TrimSelection(1L, 1L), TrimSelection(2L, 1L), TrimSelection(-1L, 2L), TrimSelection(0L, 11L))
            .forEach { assertFalse(it.isValidFor(10L)) }
    }

    @Test
    fun `full video and subsecond clips are valid`() {
        assertTrue(TrimSelection(0L, 10L).isValidFor(10L))
        val selection = TrimSelection(1500L, 1900L)
        assertTrue(selection.isValidFor(2000L))
        assertEquals(400L, selection.durationMillis)
    }
}
