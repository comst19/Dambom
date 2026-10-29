package com.comst19.dambom.feature.detection.component

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.core.domain.model.MediaCandidate
import com.comst19.dambom.core.domain.model.NetworkAccessState
import com.comst19.dambom.core.domain.model.NetworkConnection
import com.comst19.dambom.feature.detection.R
import com.comst19.dambom.feature.detection.contract.DetectionUiState
import com.comst19.dambom.feature.detection.withFallbackTitles
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DetectionResultsRuntimeTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun resultsKeepSelectionAndDownloadActionWithoutThumbnails() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fallback = context.getString(R.string.detection_default_video_title)
        val first = MediaCandidate("one", "https://example.com/trip.mp4", "주말 여행", "video/mp4", 4_194_304L)
        val second = MediaCandidate("two", "https://example.com/master.m3u8", "master", "application/x-mpegURL", null)
        val third = MediaCandidate("three", "https://example.com/clip.webm", "바다에서 보낸 하루", "video/webm", 8_388_608L)
        val state =
            DetectionUiState
                .Content(
                    pageTitle = "여행 영상 모음",
                    candidates = persistentListOf(first, second, third),
                    selectedIds = persistentSetOf(first.id),
                ).withFallbackTitles(fallback)
        var downloads = 0
        composeRule.setContent {
            DambomTheme {
                Surface(Modifier.fillMaxSize()) {
                    DetectionCandidateContent(
                        state = state,
                        networkAccess = NetworkAccessState(NetworkConnection.UNMETERED),
                        onToggleCandidate = {},
                        onSelectVariant = { _, _ -> },
                        onDownload = { downloads++ },
                    )
                }
            }
        }
        composeRule.onNodeWithText("주말 여행").assertIsDisplayed()
        composeRule.onNodeWithText("여행 영상 모음 · 2").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.detection_download_selected, 1)).performClick()
        composeRule.runOnIdle { assertEquals(1, downloads) }
        if (InstrumentationRegistry.getArguments().getString("captureDetectionCards") == "true") {
            val image = composeRule.onRoot().captureToImage().asAndroidBitmap()
            context.cacheDir.resolve("detection-results.png").outputStream().use {
                image.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
