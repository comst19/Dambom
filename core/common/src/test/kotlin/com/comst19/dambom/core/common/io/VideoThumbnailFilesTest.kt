package com.comst19.dambom.core.common.io

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class VideoThumbnailFilesTest {
    @Test
    fun `sidecar helpers preserve thumbnail file names`() {
        val videoFile = File("/videos/video.mp4")

        assertEquals(File("/videos/video.mp4.thumbnail.jpg"), videoFile.videoThumbnailFile())
        assertEquals(File("/videos/video.mp4.thumbnail.unavailable"), videoFile.videoThumbnailUnavailableFile())
        assertEquals(File("/videos/video.mp4.thumbnail.jpg.tmp"), videoFile.videoThumbnailTemporaryFile())
    }
}
