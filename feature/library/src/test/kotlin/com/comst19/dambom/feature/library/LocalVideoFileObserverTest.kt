package com.comst19.dambom.feature.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalVideoFileObserverTest {
    @Test
    fun `only the observed file name triggers availability refresh`() {
        assertTrue(isTargetFileEvent("video.mp4", "video.mp4"))
        assertFalse(isTargetFileEvent("other.mp4", "video.mp4"))
        assertFalse(isTargetFileEvent(null, "video.mp4"))
    }
}
