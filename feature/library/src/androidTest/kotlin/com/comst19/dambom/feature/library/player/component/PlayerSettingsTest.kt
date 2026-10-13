package com.comst19.dambom.feature.library.player.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.comst19.dambom.feature.library.R
import com.comst19.dambom.feature.library.component.FullscreenContentMode
import com.comst19.dambom.feature.library.player.PlayerInteractionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlayerSettingsTest {
    @get:Rule val composeRule = createComposeRule()

    private fun label(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test
    fun touch_lock_has_visible_name_and_labeled_unlock_restores_controls() {
        val interaction = PlayerInteractionState()
        var expanded = false
        composeRule.setContent {
            MaterialTheme {
                if (interaction.locked) {
                    PlayerLockButton(interaction)
                } else {
                    PlayerSettingsButton(interaction, FullscreenContentMode.Fit, {}, null, { expanded = it })
                }
            }
        }
        composeRule.onNodeWithContentDescription(label(R.string.player_settings)).performClick()
        assertTrue(expanded)
        composeRule.onNodeWithText(label(R.string.player_lock_hint)).assertDoesNotExist()
        composeRule.onNodeWithText(label(R.string.player_lock)).performClick()
        composeRule.runOnIdle {
            assertTrue(interaction.locked)
            assertFalse(expanded)
        }
        composeRule.onNodeWithContentDescription(label(R.string.player_settings)).assertDoesNotExist()
        composeRule.onNodeWithText(label(R.string.player_unlock)).assertIsDisplayed().performClick()
        composeRule.onNodeWithContentDescription(label(R.string.player_settings)).assertIsDisplayed()
        composeRule.runOnIdle { assertFalse(interaction.locked) }
    }

    @Test
    fun reset_zoom_is_hidden_until_zoomed() {
        composeRule.setContent {
            MaterialTheme { PlayerSettingsButton(PlayerInteractionState(), FullscreenContentMode.Fit, {}, null, {}) }
        }
        composeRule.onNodeWithContentDescription(label(R.string.player_settings)).performClick()
        composeRule.onNodeWithText(label(R.string.player_zoom_reset)).assertDoesNotExist()
        composeRule.onNodeWithText(label(R.string.player_lock)).assertIsDisplayed()
    }

    @Test
    fun reset_zoom_clears_pan_and_closes_menu() {
        val interaction = PlayerInteractionState(scale = 3f, panX = 100f, panY = -50f)
        var expanded = false
        composeRule.setContent {
            MaterialTheme { PlayerSettingsButton(interaction, FullscreenContentMode.Fit, {}, null, { expanded = it }) }
        }
        composeRule.onNodeWithContentDescription(label(R.string.player_settings)).performClick()
        composeRule.onNodeWithText(label(R.string.player_zoom_reset)).performClick()
        composeRule.runOnIdle {
            assertEquals(1f, interaction.scale)
            assertEquals(0f, interaction.panX)
            assertEquals(0f, interaction.panY)
            assertFalse(expanded)
        }
    }

    @Test
    fun fit_and_rotate_have_visible_names_without_inline_paragraphs() {
        var modeChanges = 0
        var rotations = 0
        composeRule.setContent {
            MaterialTheme {
                PlayerSettingsButton(PlayerInteractionState(), FullscreenContentMode.Fit, { modeChanges++ }, { rotations++ }, {})
            }
        }
        composeRule.onNodeWithContentDescription(label(R.string.player_settings)).performClick()
        composeRule.onNodeWithText(label(R.string.player_fill_hint)).assertDoesNotExist()
        composeRule.onNodeWithText(label(R.string.player_expand_crop)).performClick()
        assertEquals(1, modeChanges)
        composeRule.onNodeWithContentDescription(label(R.string.player_settings)).performClick()
        composeRule.onNodeWithText(label(R.string.player_rotate)).performClick()
        assertEquals(1, rotations)
    }
}
