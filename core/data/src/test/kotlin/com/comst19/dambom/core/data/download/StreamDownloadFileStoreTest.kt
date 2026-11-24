@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.comst19.dambom.core.data.download

import android.content.Context
import android.content.ContextWrapper
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.core.common.io.videoSubtitleFile
import com.comst19.dambom.core.common.io.videoSubtitleTemporaryFile
import com.comst19.dambom.core.common.io.videoThumbnailFile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StreamDownloadFileStoreTest {
    private lateinit var store: DownloadFileStore

    @Test
    fun `constructing file store defers directory access until first file operation`() {
        var accessedFiles = false
        val context =
            object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
                override fun getFilesDir(): File {
                    accessedFiles = true
                    return super.getFilesDir()
                }
            }

        val deferred = DownloadFileStore(context)

        assertFalse(accessedFiles)
        assertTrue(checkNotNull(deferred.partialFile("deferred-init").parentFile).isDirectory)
        assertTrue(accessedFiles)
    }

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

    @Test
    fun `private file move isolates video and sidecars outside shared videos directory`() {
        val file = store.completedFile("stream-file-test", "https://example.test/video.mp4", "video/mp4")
        file.writeText("video")
        file.videoSubtitleFile().writeText("subtitles")
        file.videoThumbnailFile().writeText("thumb")

        val toPrivateResult = store.movePrivacyFiles(file.name, toPrivate = true)
        assertTrue(toPrivateResult is PrivacyFileMoveResult.Moved)
        assertEquals(3, (toPrivateResult as PrivacyFileMoveResult.Moved).files.size)
        assertFalse(file.exists())
        assertFalse(file.videoSubtitleFile().exists())
        assertFalse(file.videoThumbnailFile().exists())
        assertNull(store.completedFilePath(file.name, isPrivate = false))
        val privatePath = store.completedFilePath(file.name, isPrivate = true)
        assertTrue(privatePath?.contains("/private_videos/") == true)
        val privateFile = File(checkNotNull(privatePath))
        assertTrue(privateFile.isFile)
        assertTrue(privateFile.videoSubtitleFile().isFile)
        assertTrue(privateFile.videoThumbnailFile().isFile)

        val toPublicResult = store.movePrivacyFiles(file.name, toPrivate = false)
        assertTrue(toPublicResult is PrivacyFileMoveResult.Moved)
        assertEquals(3, (toPublicResult as PrivacyFileMoveResult.Moved).files.size)
        assertTrue(file.isFile)
        assertTrue(file.videoSubtitleFile().isFile)
        assertTrue(file.videoThumbnailFile().isFile)
        assertFalse(privateFile.exists())
    }

    @Test
    fun `leftover sidecar moves and rolls back without disturbing video already in target directory`() {
        val file = store.completedFile("stream-file-test", "https://example.test/video.mp4", "video/mp4")
        file.writeText("video")
        val initialMove = store.movePrivacyFiles(file.name, toPrivate = true)
        assertTrue(initialMove is PrivacyFileMoveResult.Moved)
        val privateFile = File(checkNotNull(store.completedFilePath(file.name, isPrivate = true)))
        assertTrue(privateFile.isFile)

        // Leave only a subtitle sidecar in the public folder while the video is already private.
        file.videoSubtitleFile().writeText("late-subtitle")

        val sidecarMove = store.movePrivacyFiles(file.name, toPrivate = true) as PrivacyFileMoveResult.Moved
        assertEquals(1, sidecarMove.files.size)
        assertFalse(file.videoSubtitleFile().exists())
        assertTrue(privateFile.videoSubtitleFile().isFile)

        store.rollbackPrivacyFiles(sidecarMove.files)
        assertTrue(privateFile.isFile)
        assertFalse(file.exists())
        assertTrue(file.videoSubtitleFile().isFile)
        assertFalse(privateFile.videoSubtitleFile().exists())
    }

    @Test
    fun `sidecar move failure rolls back already moved video file`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = store.completedFile("stream-file-test", "https://example.test/video.mp4", "video/mp4")
        file.writeText("video")
        file.videoSubtitleFile().writeText("subtitles")
        val privateDir = context.filesDir.resolve("private_videos").apply(File::mkdirs)
        val blockingSubtitleDir = privateDir.resolve(file.videoSubtitleFile().name).apply(File::mkdirs)
        blockingSubtitleDir.resolve("blocker").writeText("block")
        try {
            assertEquals(PrivacyFileMoveResult.Failed, store.movePrivacyFiles(file.name, toPrivate = true))
            assertTrue(file.isFile)
            assertTrue(file.videoSubtitleFile().isFile)
            assertFalse(privateDir.resolve(file.name).exists())
            assertTrue(privateDir.listFiles().orEmpty().none { it.name.endsWith(".move.tmp") })
        } finally {
            blockingSubtitleDir.deleteRecursively()
        }
    }

    @Test
    fun `copy fallback writes via temporary file and removes temp artifacts after moving`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sourceDir = context.filesDir.resolve("move-fallback-src").apply(File::mkdirs)
        val targetDir = context.filesDir.resolve("move-fallback-dst").apply(File::mkdirs)
        val rawSource = sourceDir.resolve("video.mp4").apply { writeText("intact-11b!") }
        val nonRenamingSource =
            object : File(rawSource.path) {
                override fun renameTo(dest: File): Boolean = false
            }
        val target = targetDir.resolve("video.mp4").apply { writeText("old") }
        try {
            assertTrue(moveFile(nonRenamingSource, target))
            assertFalse(rawSource.exists())
            assertEquals("intact-11b!", target.readText())
            assertTrue(targetDir.listFiles().orEmpty().none { it.name.endsWith(".move.tmp") })
        } finally {
            sourceDir.deleteRecursively()
            targetDir.deleteRecursively()
        }
    }

    @Test
    fun `path traversal file names are rejected by file store`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val outsideFile = context.filesDir.resolve("outside-secret.mp4").apply { writeText("secret") }
        try {
            assertNull(store.completedFilePath("../outside-secret.mp4", isPrivate = false))
            assertNull(store.completedFilePath("../outside-secret.mp4", isPrivate = true))
            assertEquals(
                PrivacyFileMoveResult.Failed,
                store.movePrivacyFiles("../outside-secret.mp4", toPrivate = true),
            )
            assertTrue(store.delete("traversal-test", "../outside-secret.mp4"))
            assertTrue(outsideFile.exists())
        } finally {
            outsideFile.delete()
        }
    }
}
