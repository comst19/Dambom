package com.comst19.dambom.core.designsystem

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DambomLinearProgressIndicatorTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `progress passes through intermediate frames and reaches the latest target`() {
        val progress = mutableStateOf<Float?>(0.2f)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            DambomTheme {
                DambomLinearProgressIndicator(progress.value, Modifier.testTag("progress"))
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals(0.2f, currentProgress(), 0.001f)
        composeRule.runOnIdle { progress.value = 0.8f }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(128)
        val intermediate = currentProgress()
        assertTrue("Expected an intermediate fraction, got $intermediate", intermediate > 0.2f && intermediate < 0.8f)
        composeRule.runOnIdle { progress.value = 1f }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(64)
        assertTrue(currentProgress() >= intermediate)
        composeRule.mainClock.advanceTimeBy(400)
        assertEquals(1f, currentProgress(), 0.001f)
    }

    @Test
    fun `unknown progress stays indeterminate until a measured fraction arrives`() {
        val progress = mutableStateOf<Float?>(null)
        composeRule.setContent {
            DambomTheme {
                DambomLinearProgressIndicator(progress.value, Modifier.testTag("progress"))
            }
        }
        composeRule.onNodeWithTag("progress").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate),
        )
        composeRule.runOnIdle { progress.value = 0.5f }
        assertEquals(0.5f, currentProgress(), 0.001f)
    }

    private fun currentProgress(): Float =
        composeRule
            .onNodeWithTag("progress")
            .fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo]
            .current
}
