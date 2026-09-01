package com.comst19.dambom.core.common.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class FileSizeFormatterTest {
    @Test
    fun `sizes smaller than one megabyte use kilobytes`() {
        assertEquals("512 KB", (512L * 1_024L).formatFileSize(Locale.US))
    }

    @Test
    fun `sizes from one megabyte use one decimal place`() {
        assertEquals("1.0 MB", (1_024L * 1_024L).formatFileSize(Locale.US))
        assertEquals("1.5 MB", (1_536L * 1_024L).formatFileSize(Locale.US))
    }

    @Test
    fun `decimal separator follows the display locale`() {
        assertEquals("1,5 MB", (1_536L * 1_024L).formatFileSize(Locale.GERMANY))
    }
}
