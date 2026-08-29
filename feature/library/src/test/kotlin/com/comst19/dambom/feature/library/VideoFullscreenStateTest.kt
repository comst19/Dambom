package com.comst19.dambom.feature.library

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.feature.library.component.FullscreenContentMode
import com.comst19.dambom.feature.library.component.LibraryFileActions
import com.comst19.dambom.feature.library.component.fullscreenContentModeFor
import com.comst19.dambom.feature.library.component.shouldAutoHideFullscreenControls
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VideoFullscreenStateTest {
    @get:Rule val composeRule = createComposeRule()

    private lateinit var player: ExoPlayer
    private lateinit var videoFile: File

    @Before
    fun setUp() {
        player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
        videoFile = File.createTempFile("video", ".mp4")
    }

    @After
    fun tearDown() {
        player.release()
        File("${videoFile.path}.thumbnail.unavailable").delete()
        videoFile.delete()
    }

    @Test
    fun `fullscreen button propagates the detail route setter`() {
        var isVideoFullscreen = false
        val fullscreenDescription =
            ApplicationProvider.getApplicationContext<Context>().getString(R.string.player_fullscreen)

        composeRule.setContent {
            MaterialTheme {
                VideoPlayerScreen(
                    task = task(),
                    player = player,
                    fileActions = fileActions(),
                    onBack = {},
                    showBack = true,
                    isVideoFullscreen = false,
                    onVideoFullscreenChange = { isVideoFullscreen = it },
                    onVideoRotate = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription(fullscreenDescription).performClick()

        assertTrue(isVideoFullscreen)
    }

    @Test
    fun `fullscreen composes exactly one player surface`() {
        val surfaceDescription =
            ApplicationProvider.getApplicationContext<Context>().getString(R.string.player_surface_description, "video")

        composeRule.setContent {
            MaterialTheme {
                VideoPlayerScreen(
                    task = task(),
                    player = player,
                    fileActions = fileActions(),
                    onBack = {},
                    showBack = true,
                    isVideoFullscreen = true,
                    onVideoFullscreenChange = {},
                    onVideoRotate = {},
                )
            }
        }

        composeRule
            .onAllNodesWithContentDescription(surfaceDescription, useUnmergedTree = true)
            .assertCountEquals(1)
    }

    @Test
    fun `missing detail video clears fullscreen`() {
        assertTrue(shouldClearVideoFullscreen(isVideoFullscreen = true, hasVideo = false))
    }

    @Test
    fun `available detail video keeps fullscreen state`() {
        assertFalse(shouldClearVideoFullscreen(isVideoFullscreen = true, hasVideo = true))
    }

    @Test
    fun `completed task with a missing local file is unavailable and clears fullscreen`() {
        val missingTask = task().copy(localFilePath = File(videoFile.parentFile, "missing.mp4").path)

        assertFalse(isLocalVideoAvailable(missingTask))
        assertTrue(shouldClearVideoFullscreen(isVideoFullscreen = true, hasVideo = isLocalVideoAvailable(missingTask)))
    }

    @Test
    fun `completed task with a regular local file is playable`() {
        assertTrue(isLocalVideoAvailable(task()))
    }

    @Test
    fun `non-file path is unavailable without deleting the database task`() {
        val directoryTask = task().copy(localFilePath = requireNotNull(videoFile.parentFile).path)

        assertFalse(isLocalVideoAvailable(directoryTask))
        assertEquals("video", directoryTask.id)
    }

    @Test
    fun `expanded crop resets to fit for PiP media change and fullscreen reentry`() {
        assertEquals(
            FullscreenContentMode.Fit,
            fullscreenContentModeFor(
                selected = FullscreenContentMode.ExpandedCrop,
                isPipContentOnly = true,
            ),
        )
        assertEquals(
            FullscreenContentMode.ExpandedCrop,
            fullscreenContentModeFor(
                selected = FullscreenContentMode.ExpandedCrop,
                isPipContentOnly = false,
            ),
        )
    }

    @Test
    fun `fullscreen rotate action is available only on compact phone windows`() {
        assertTrue(shouldShowFullscreenRotationControl(smallestScreenWidthDp = 599))
        assertFalse(shouldShowFullscreenRotationControl(smallestScreenWidthDp = 600))
    }

    @Test
    fun `fullscreen controls only auto hide while playing and idle`() {
        assertTrue(
            shouldAutoHideFullscreenControls(
                controlsVisible = true,
                isPlaying = true,
                isControlsInteracting = false,
            ),
        )
        assertFalse(
            shouldAutoHideFullscreenControls(
                controlsVisible = true,
                isPlaying = false,
                isControlsInteracting = false,
            ),
        )
        assertFalse(
            shouldAutoHideFullscreenControls(
                controlsVisible = true,
                isPlaying = true,
                isControlsInteracting = true,
            ),
        )
        assertFalse(
            shouldAutoHideFullscreenControls(
                controlsVisible = false,
                isPlaying = true,
                isControlsInteracting = false,
            ),
        )
    }

    private fun task() =
        DownloadTask(
            id = "video",
            url = "https://example.com/video.mp4",
            sourcePageUrl = "https://example.com",
            title = "video",
            mimeType = "video/mp4",
            expectedBytes = 100L,
            downloadedBytes = 100L,
            quality = "원본",
            status = DownloadStatus.COMPLETED,
            failureReason = null,
            localFileName = "video.mp4",
            localFilePath = videoFile.path,
            createdAtMillis = 1L,
            updatedAtMillis = 1L,
        )

    private fun fileActions() =
        LibraryFileActions(
            onRename = { _, _ -> },
            onExport = {},
            onShare = {},
            onCopyLink = {},
            onOpenOriginal = {},
            onDelete = {},
        )
}
