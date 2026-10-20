package com.comst19.dambom.feature.library.media

import android.media.MediaMetadataRetriever
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.comst19.dambom.feature.library.libraryTestVideo
import org.junit.Assert.assertEquals
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
@Config(sdk = [35], instrumentedPackages = ["com.comst19.dambom.feature.library.media"])
class LocalVideoMetadataComposeTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `renamed task recomposes with the same cached metadata while replacement reloads`() {
        val videoFile = File.createTempFile("rename-cache", ".mp4").apply { writeBytes(byteArrayOf(1)) }
        ShadowMediaMetadataRetriever.addMetadata(videoFile.path, MediaMetadataRetriever.METADATA_KEY_DURATION, "1000")
        val marker =
            File("${videoFile.path}.thumbnail.unavailable").apply {
                writeBytes(byteArrayOf())
                setLastModified(videoFile.lastModified())
            }
        org.junit.Assert.assertTrue(marker.isFile && marker.lastModified() >= videoFile.lastModified())
        val task = mutableStateOf(libraryTestVideo(videoFile.path))
        var current: LocalVideoMetadata? = null
        try {
            composeRule.setContent {
                val value = task.value
                current = rememberLocalVideoMetadata(value.localFilePath).value
                Text(value.title)
            }
            composeRule.waitUntil(timeoutMillis = 10_000) { current != null }
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
            marker.setLastModified(videoFile.lastModified())
            composeRule.runOnIdle { task.value = task.value.copy(updatedAtMillis = 3L, title = "Replaced video") }
            composeRule.waitUntil(timeoutMillis = 10_000) { current != null && current !== initial }
            assertNotSame(initial, current)
            assertEquals(2_000L, current?.durationMillis)
        } finally {
            marker.delete()
            videoFile.delete()
        }
    }
}
