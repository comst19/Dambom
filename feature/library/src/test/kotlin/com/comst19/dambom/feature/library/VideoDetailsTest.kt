package com.comst19.dambom.feature.library

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.feature.library.component.VideoDetails
import com.comst19.dambom.feature.library.component.formatDownloadedAt
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VideoDetailsTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `invalid epoch timestamp is shown as unknown`() {
        assertEquals("Unknown", formatDownloadedAt(1L, Locale.US, "Unknown"))
    }

    @Test
    fun `video information is collapsed until the user opens it`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val informationTitle = context.getString(R.string.player_info_title)
        val durationLabel = context.getString(R.string.player_info_duration)

        composeRule.setContent {
            MaterialTheme {
                VideoDetails(
                    task = task(),
                    metadata = null,
                    onOpenOriginal = {},
                    onCopyLink = {},
                    onShareLink = {},
                )
            }
        }

        composeRule.onAllNodesWithText(durationLabel).assertCountEquals(0)
        composeRule.onNodeWithText(informationTitle).performClick()
        composeRule.onAllNodesWithText(durationLabel).assertCountEquals(1)
    }
}

private fun task() =
    DownloadTask(
        id = "video",
        url = "https://example.com/video.mp4",
        sourcePageUrl = "https://x.com/user/status/1",
        title = "Video",
        mimeType = "video/mp4",
        expectedBytes = 100L,
        downloadedBytes = 100L,
        quality = "720p",
        status = DownloadStatus.COMPLETED,
        failureReason = null,
        localFileName = "video.mp4",
        localFilePath = "/video.mp4",
        createdAtMillis = 1L,
        updatedAtMillis = 1L,
    )
