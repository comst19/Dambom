@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.comst19.dambom.core.data.download

import android.content.Context
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.core.common.io.videoSubtitleFile
import com.comst19.dambom.core.common.io.videoSubtitleTemporaryFile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StreamDownloadFileStoreTest {
    private lateinit var store: DownloadFileStore

    @Before
    fun setUp() {
        store = DownloadFileStore(ApplicationProvider.getApplicationContext<Context>())
    }

    @After
    fun tearDown() {
        store.delete("stream-file-test", null)
        store.streamDatabaseProvider.close()
    }

    @Test
    fun `adaptive input completes as MP4 while direct video extension remains unchanged`() {
        assertEquals(
            "stream-file-test.mp4",
            store.completedFile("stream-file-test", "https://example.test/index.m3u8", null).name,
        )
        assertEquals(
            "stream-file-test.mp4",
            store.completedFile("stream-file-test", "https://example.test/signed", "application/dash+xml").name,
        )
        assertEquals(
            "stream-file-test.webm",
            store.completedFile("stream-file-test", "https://example.test/video.webm", "video/webm").name,
        )
    }

    @Test
    fun `deleting a video removes its subtitles and interrupted subtitle output`() {
        val file = store.completedFile("stream-file-test", "https://example.test/video.mp4", "video/mp4")
        file.writeText("video")
        file.videoSubtitleFile().writeText("subtitles")
        file.videoSubtitleTemporaryFile().writeText("incomplete")
        assertTrue(store.delete("stream-file-test", file.name))
        assertFalse(file.exists())
        assertFalse(file.videoSubtitleFile().exists())
        assertFalse(file.videoSubtitleTemporaryFile().exists())
    }

    @Test
    fun `delete defers cache removal until writer releases it`() {
        val directory = store.streamDirectory("stream-file-test")
        val cache = SimpleCache(directory, NoOpCacheEvictor(), store.streamDatabaseProvider)
        try {
            assertFalse(store.delete("stream-file-test", null))
            assertTrue(SimpleCache.isCacheFolderLocked(directory))
        } finally {
            cache.release()
        }
        assertTrue(store.delete("stream-file-test", null))
        assertFalse(directory.exists())
    }
}
