package com.comst19.dambom.feature.library.compress

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.comst19.dambom.feature.library.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VideoCompressionScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val initial =
        VideoCompressionState(
            source = CompressionSource(1920, 1080, 20_000, 10_000_000, 4_000_000),
            loading = false,
        )

    @Test
    fun qualityChoicesAreSelectableAndSaveUsesSelectedChoice() {
        var state by mutableStateOf(initial)
        var saved: CompressionProfile? = null
        compose.setContent {
            MaterialTheme {
                VideoCompressionScreen(state, {}, { state = state.copy(profile = it) }, { saved = state.profile }, {}, {})
            }
        }
        compose.onNodeWithText(context.getString(R.string.compress_small)).performClick().assertIsSelected()
        compose.onNodeWithText(context.getString(R.string.compress_save)).performClick()
        assertEquals(CompressionProfile.SMALL, saved)
    }

    @Test
    fun encodingDisablesChoicesAndShowsCancellation() {
        compose.setContent {
            MaterialTheme {
                VideoCompressionScreen(initial.copy(exporting = true, progress = 30), {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText(context.getString(R.string.compress_small)).assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.trim_cancel_export)).assertIsEnabled()
        compose.onNodeWithText(context.getString(R.string.compress_exporting)).assertIsDisplayed()
    }

    @Test
    fun notSmallerExplainsWhyAndAllowsAnotherSave() {
        compose.setContent {
            MaterialTheme {
                VideoCompressionScreen(initial.copy(failure = CompressionFailure.NOT_SMALLER), {}, {}, {}, {}, {})
            }
        }
        compose.onNodeWithText(context.getString(R.string.compress_not_smaller)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.compress_save)).assertIsEnabled()
    }

    @Test
    fun completedFileCanBeShared() {
        var shares = 0
        compose.setContent {
            MaterialTheme {
                VideoCompressionScreen(initial.copy(savedUri = "content://saved/video", savedBytes = 500_000), {}, {}, {}, {}, { shares++ })
            }
        }
        compose.onNodeWithText(context.getString(R.string.library_share_video)).performClick()
        assertEquals(1, shares)
    }
}
