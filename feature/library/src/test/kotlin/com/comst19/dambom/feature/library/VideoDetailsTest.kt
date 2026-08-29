package com.comst19.dambom.feature.library

import com.comst19.dambom.feature.library.component.formatDownloadedAt
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class VideoDetailsTest {
    @Test
    fun `invalid epoch timestamp is shown as unknown`() {
        assertEquals("Unknown", formatDownloadedAt(1L, Locale.US, "Unknown"))
    }
}
