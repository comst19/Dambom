package com.comst19.dambom.feature.library.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.up
import com.comst19.dambom.feature.library.player.PlayerInteractionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FullscreenPlayerGesturesTest {
    @get:Rule val composeRule = createComposeRule()

    private var toggleCount by mutableIntStateOf(0)
    private var contentMode by mutableStateOf<FullscreenContentMode?>(null)

    @Test
    fun only_short_unmoved_single_pointer_tap_toggles_controls() {
        setGestureLayer()

        composeRule.onNodeWithTag(GESTURE_LAYER_TAG).performTouchInput { click(center) }

        assertEquals(1, toggleCount)
    }

    @Test
    fun semantics_click_toggles_controls() {
        setGestureLayer()

        composeRule.onNodeWithTag(GESTURE_LAYER_TAG).performClick()

        assertEquals(1, toggleCount)
    }

    @Test
    fun drag_long_press_and_stationary_two_pointer_do_not_toggle_controls() {
        setGestureLayer()

        composeRule.onNodeWithTag(GESTURE_LAYER_TAG).performTouchInput {
            swipe(start = Offset(100f, 100f), end = Offset(280f, 100f))
        }
        composeRule.onNodeWithTag(GESTURE_LAYER_TAG).performTouchInput {
            down(Offset(160f, 160f))
            advanceEventTime(600)
            up()
        }
        composeRule.onNodeWithTag(GESTURE_LAYER_TAG).performTouchInput {
            down(pointerId = 0, position = Offset(120f, 120f))
            down(pointerId = 1, position = Offset(280f, 120f))
            advanceEventTime(100)
            up(pointerId = 1)
            up(pointerId = 0)
        }

        assertEquals(0, toggleCount)
        assertEquals(null, contentMode)
    }

    @Test
    fun pinch_out_and_in_change_mode_once_per_gesture_without_toggling_controls() {
        setGestureLayer()

        composeRule.onNodeWithTag(GESTURE_LAYER_TAG).performTouchInput {
            pinch(
                start0 = Offset(170f, 200f),
                end0 = Offset(80f, 200f),
                start1 = Offset(230f, 200f),
                end1 = Offset(320f, 200f),
            )
        }
        assertEquals(FullscreenContentMode.ExpandedCrop, contentMode)
        assertEquals(0, toggleCount)

        contentMode = null
        composeRule.onNodeWithTag(GESTURE_LAYER_TAG).performTouchInput {
            pinch(
                start0 = Offset(80f, 200f),
                end0 = Offset(170f, 200f),
                start1 = Offset(320f, 200f),
                end1 = Offset(230f, 200f),
            )
        }
        assertEquals(FullscreenContentMode.Fit, contentMode)
        assertEquals(0, toggleCount)
    }

    @Test
    fun continuous_pinch_and_zoomed_pan_update_transform_without_toggling_controls() {
        val interaction = PlayerInteractionState()
        setGestureLayer(interaction)
        composeRule.onNodeWithTag(GESTURE_LAYER_TAG).performTouchInput {
            pinch(Offset(170f, 200f), Offset(80f, 200f), Offset(230f, 200f), Offset(320f, 200f))
        }
        composeRule.runOnIdle { assertTrue(interaction.scale > 1f) }
        composeRule.onNodeWithTag(GESTURE_LAYER_TAG).performTouchInput {
            swipe(Offset(150f, 200f), Offset(250f, 240f))
        }
        composeRule.runOnIdle {
            assertTrue(interaction.panX > 0f)
            assertEquals(0, toggleCount)
            assertEquals(null, contentMode)
        }
    }

    @Test
    fun restored_lock_blocks_input_and_explicit_unlock_restores_gestures() {
        val interaction = PlayerInteractionState(locked = true)
        setGestureLayer(interaction)
        composeRule.onNodeWithTag(GESTURE_LAYER_TAG).performTouchInput { click(center) }
        composeRule.onNodeWithTag(GESTURE_LAYER_TAG).performTouchInput {
            pinch(Offset(170f, 200f), Offset(80f, 200f), Offset(230f, 200f), Offset(320f, 200f))
        }
        composeRule.runOnIdle {
            assertEquals(1f, interaction.scale)
            assertEquals(0, toggleCount)
            interaction.locked = false
        }
        composeRule.onNodeWithTag(GESTURE_LAYER_TAG).performTouchInput { click(center) }
        composeRule.runOnIdle { assertEquals(1, toggleCount) }
        composeRule.onNodeWithTag(GESTURE_LAYER_TAG).performTouchInput {
            pinch(Offset(170f, 200f), Offset(80f, 200f), Offset(230f, 200f), Offset(320f, 200f))
        }
        composeRule.runOnIdle { assertTrue(interaction.scale > 1f) }
    }

    private fun setGestureLayer(interaction: PlayerInteractionState? = null) {
        toggleCount = 0
        contentMode = null
        composeRule.setContent {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .testTag(GESTURE_LAYER_TAG)
                        .fullscreenPlayerGestures(
                            interaction = interaction,
                            toggleControlsLabel = "Toggle playback controls",
                            onToggleControls = { toggleCount++ },
                            onContentModeChanged = { contentMode = it },
                        ),
            )
        }
    }
}

private const val GESTURE_LAYER_TAG = "fullscreen-gesture-layer"
