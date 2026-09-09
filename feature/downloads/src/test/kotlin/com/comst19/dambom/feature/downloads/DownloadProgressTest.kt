package com.comst19.dambom.feature.downloads

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.feature.downloads.component.DownloadGridCard
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en")
class DownloadProgressTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `unknown length uses indeterminate progress instead of zero percent`() {
        showTask(expectedBytes = null, status = DownloadStatus.DOWNLOADING)
        assertProgress(ProgressBarRangeInfo.Indeterminate)
    }

    @Test
    fun `paused download keeps the received fraction visible`() {
        showTask(expectedBytes = 100L, status = DownloadStatus.PAUSED)
        assertProgress(ProgressBarRangeInfo(0.25f, 0f..1f))
    }

    private fun assertProgress(info: ProgressBarRangeInfo) {
        composeRule
            .onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, info))
            .assertCountEquals(1)
    }

    private fun showTask(
        expectedBytes: Long?,
        status: DownloadStatus,
    ) {
        val task =
            DownloadTask(
                id = "progress",
                url = "https://example.com/video.mp4",
                sourcePageUrl = "https://example.com",
                title = "Progress",
                mimeType = "video/mp4",
                expectedBytes = expectedBytes,
                downloadedBytes = 25L,
                quality = "720p",
                status = status,
                failureReason = null,
                localFileName = null,
                createdAtMillis = 1L,
                updatedAtMillis = 1L,
            )
        composeRule.setContent {
            DambomTheme {
                DownloadGridCard(task, true, {}, {}, {}, {})
            }
        }
    }
}
