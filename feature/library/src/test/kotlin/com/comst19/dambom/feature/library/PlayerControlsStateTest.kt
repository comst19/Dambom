package com.comst19.dambom.feature.library

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.comst19.dambom.feature.library.component.PlayerControlsState
import com.comst19.dambom.feature.library.component.rememberPlayerControlsState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlayerControlsStateTest {
    @get:Rule val composeRule = createComposeRule()

    private val mediaId = mutableStateOf("first")
    private val isPlaying = mutableStateOf(true)
    private lateinit var controls: PlayerControlsState

    @Before
    fun setUp() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            controls = rememberPlayerControlsState(mediaId.value, isPlaying.value)
            Text(if (controls.isVisible) "Visible" else "Hidden")
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
    }

    @Test
    fun `pause cancels auto hide and playback starts a full delay`() {
        composeRule.mainClock.advanceTimeBy(2_000L)
        composeRule.runOnIdle { isPlaying.value = false }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(5_000L)
        composeRule.runOnIdle { assertTrue(controls.isVisible) }

        composeRule.runOnIdle { isPlaying.value = true }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(2_000L)
        composeRule.runOnIdle { assertTrue(controls.isVisible) }
        composeRule.mainClock.advanceTimeBy(1_200L)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertFalse(controls.isVisible) }
    }

    @Test
    fun `interaction keeps controls visible until a full delay after release`() {
        composeRule.mainClock.advanceTimeBy(2_000L)
        composeRule.runOnIdle { controls.onInteractionChanged(true) }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(5_000L)
        composeRule.runOnIdle { assertTrue(controls.isVisible) }

        composeRule.runOnIdle { controls.onInteractionChanged(false) }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(2_000L)
        composeRule.runOnIdle { assertTrue(controls.isVisible) }
        composeRule.mainClock.advanceTimeBy(1_200L)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertFalse(controls.isVisible) }
    }

    @Test
    fun `interaction restarts the delay even while controls are already visible`() {
        composeRule.mainClock.advanceTimeBy(2_000L)
        composeRule.runOnIdle { controls.onInteraction() }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(2_000L)
        composeRule.runOnIdle { assertTrue(controls.isVisible) }
        composeRule.mainClock.advanceTimeBy(1_200L)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertFalse(controls.isVisible) }

        composeRule.runOnIdle { controls.onInteraction() }
        composeRule.mainClock.advanceTimeBy(32L)
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertTrue(controls.isVisible) }
    }

    @Test
    fun `switching media starts a new delay without retaining the previous timer`() {
        composeRule.mainClock.advanceTimeBy(2_000L)
        composeRule.runOnIdle { mediaId.value = "second" }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(2_000L)
        composeRule.runOnIdle { assertTrue(controls.isVisible) }
        composeRule.mainClock.advanceTimeBy(1_200L)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertFalse(controls.isVisible) }
    }
}
