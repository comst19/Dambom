package com.comst19.dambom.feature.library.media

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.feature.library.libraryTestVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaMetadataRetriever
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalVideoMetadataTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `revision observer sees missing file creation replacement and deletion and stops on cancel`() =
        runBlocking {
            val directory =
                java.nio.file.Files
                    .createTempDirectory("observed-video")
                    .toFile()
            val file = directory.resolve("video.mp4")
            val updates = Channel<LocalVideoCacheKey>(Channel.UNLIMITED)
            val job = launch(Dispatchers.IO) { observeLocalVideoCacheKey(file.path).collect { updates.send(it) } }

            suspend fun awaitSize(size: Long) =
                withTimeout(5_000) {
                    var key = updates.receive()
                    while (key.sizeBytes != size) key = updates.receive()
                    key
                }
            try {
                awaitSize(0)
                file.writeBytes(byteArrayOf(1))
                val first = awaitSize(1)
                file.writeBytes(byteArrayOf(1, 2, 3))
                assertNotEquals(first, awaitSize(3))
                file.delete()
                awaitSize(0)
                job.cancelAndJoin()
                org.junit.Assert.assertTrue(job.isCancelled)
            } finally {
                job.cancelAndJoin()
                directory.deleteRecursively()
            }
        }

    @Test
    fun `concurrent loads reuse one cached result while replaced file reloads`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val videoFile = context.filesDir.resolve("replace-concurrent.mp4").apply { writeBytes(byteArrayOf(1)) }
            val key = localVideoCacheKey(videoFile.path)
            val results =
                List(20) {
                    async(Dispatchers.IO) { LocalVideoMetadataLoader.load(context, key) }
                }.awaitAll()

            results.forEach { assertSame(results.first(), it) }
            videoFile.writeBytes(byteArrayOf(1, 2, 3))
            assertNotSame(results.first(), LocalVideoMetadataLoader.load(context, localVideoCacheKey(videoFile.path)))
        }

    @Test
    fun `renamed task recomposes with the same cached metadata while replacement reloads`() {
        val videoFile = File.createTempFile("rename-cache", ".mp4").apply { writeBytes(byteArrayOf(1)) }
        ShadowMediaMetadataRetriever.addMetadata(videoFile.path, MediaMetadataRetriever.METADATA_KEY_DURATION, "1000")
        val task = mutableStateOf(libraryTestVideo(videoFile.path))
        var current: LocalVideoMetadata? = null
        try {
            composeRule.setContent {
                val value = task.value
                current = rememberLocalVideoMetadata(value.localFilePath).value
                Text(value.title)
            }
            composeRule.waitUntil { current != null }
            val initial = current
            assertEquals(1_000L, initial?.durationMillis)
            composeRule.runOnIdle { task.value = task.value.copy(title = "Renamed video", updatedAtMillis = 2L) }
            composeRule.onNodeWithText("Renamed video").assertExists()
            composeRule.runOnIdle { assertSame(initial, current) }
            ShadowMediaMetadataRetriever.addMetadata(
                videoFile.path,
                MediaMetadataRetriever.METADATA_KEY_DURATION,
                "2000",
            )
            videoFile.writeBytes(byteArrayOf(1, 2, 3))
            composeRule.runOnIdle { task.value = task.value.copy(updatedAtMillis = 3L, title = "Replaced video") }
            composeRule.waitUntil(timeoutMillis = 10_000) { current != null && current !== initial }
            assertNotSame(initial, current)
            assertEquals(2_000L, current?.durationMillis)
        } finally {
            videoFile.delete()
        }
    }

    @Test
    fun `cache key changes when video content at the same path is replaced`() {
        val videoFile = File.createTempFile("replace-cache", ".mp4").apply { writeBytes(byteArrayOf(1)) }
        val beforeReplace = localVideoCacheKey(videoFile.path)

        videoFile.writeBytes(byteArrayOf(1, 2))

        assertNotEquals(beforeReplace, localVideoCacheKey(videoFile.path))
        videoFile.delete()
    }
}
