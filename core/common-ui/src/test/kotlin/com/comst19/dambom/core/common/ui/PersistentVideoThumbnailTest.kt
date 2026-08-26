package com.comst19.dambom.core.common.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PersistentVideoThumbnailTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `existing thumbnail is reused without generating another frame`() {
        val videoFile = temporaryFolder.newFile("video.mp4").apply { writeBytes(byteArrayOf(1)) }
        var generatedCount = 0
        val writer: (java.io.File) -> Boolean = { output ->
            generatedCount++
            output.writeBytes(byteArrayOf(2))
            true
        }

        val first = ensureVideoThumbnailFile(videoFile, writer)
        val second = ensureVideoThumbnailFile(videoFile, writer)

        assertTrue(first?.isFile == true)
        assertEquals(first, second)
        assertEquals(1, generatedCount)
    }

    @Test
    fun `unavailable thumbnail is not retried until the video changes`() {
        val videoFile = temporaryFolder.newFile("unsupported.mp4").apply { writeBytes(byteArrayOf(1)) }

        rememberVideoThumbnailUnavailable(videoFile)

        assertTrue(isVideoThumbnailUnavailable(videoFile))

        videoFile.setLastModified(videoFile.lastModified() + 1_000L)

        assertFalse(isVideoThumbnailUnavailable(videoFile))
    }
}
