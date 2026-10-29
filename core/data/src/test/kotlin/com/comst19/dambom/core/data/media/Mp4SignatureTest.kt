package com.comst19.dambom.core.data.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Mp4SignatureTest {
    private val header = byteArrayOf(0, 0, 0, 24) + "ftypisom".toByteArray(Charsets.US_ASCII)

    @Test
    fun `every truncated header is rejected even when the marker is complete`() {
        for (size in 0 until header.size) {
            assertFalse("Header size $size", header.copyOf(size).hasMp4FileTypeBox())
        }
    }

    @Test
    fun `minimum and longer headers retain the file type signature`() {
        assertTrue(header.hasMp4FileTypeBox())
        assertTrue((header + ByteArray(512)).hasMp4FileTypeBox())
    }

    @Test
    fun `file type marker must match every byte at the original offset`() {
        for (index in 4..7) {
            val changed = header.copyOf()
            changed[index] = (changed[index].toInt() - 32).toByte()
            assertFalse(changed.hasMp4FileTypeBox())
        }
        assertFalse((byteArrayOf(0) + header).hasMp4FileTypeBox())
        assertFalse(("ftyp".toByteArray(Charsets.US_ASCII) + ByteArray(8)).hasMp4FileTypeBox())
    }
}
