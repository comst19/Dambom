package com.comst19.dambom.feature.library

import android.os.FileObserver
import com.comst19.dambom.feature.library.file.createDirectoryFileObserver
import com.comst19.dambom.feature.library.file.isTargetFileEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class LocalVideoFileObserverTest {
    @get:Rule val tempFolder = TemporaryFolder()

    @Test
    @Config(sdk = [35])
    fun `only the observed file name triggers availability refresh`() {
        assertTrue(isTargetFileEvent("video.mp4", "video.mp4"))
        assertFalse(isTargetFileEvent("other.mp4", "video.mp4"))
        assertFalse(isTargetFileEvent(null, "video.mp4"))
    }

    @Test
    @Config(sdk = [28, 35])
    fun `directory observer creates cleanly and forwards events on API 28 and API 35`() {
        val observed = mutableListOf<String?>()
        val observer = createDirectoryFileObserver(tempFolder.root) { observed.add(it) }
        try {
            observer.startWatching()
            observer.onEvent(FileObserver.CLOSE_WRITE, "video.mp4")
            assertEquals(listOf("video.mp4"), observed)
        } finally {
            observer.stopWatching()
        }
    }
}
