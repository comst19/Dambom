package com.comst19.dambom.feature.library.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DambomSeekBarTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun taps_at_quarter_and_three_quarters_update_the_production_seek_bar() {
        var value by mutableFloatStateOf(0f)
        setSeekBar(value = value, enabled = true, onValueChange = { value = it })

        composeRule.onNodeWithTag(SEEK_BAR_TAG).performTouchInput { click(center.copy(x = center.x * 0.5f)) }
        assertEquals(0.25f, value, 0.02f)

        composeRule.onNodeWithTag(SEEK_BAR_TAG).performTouchInput { click(center.copy(x = center.x * 1.5f)) }
        assertEquals(0.75f, value, 0.02f)
    }

    @Test
    fun drag_updates_progress_and_reports_interaction_start_then_end() {
        var value by mutableFloatStateOf(0f)
        val interactions = mutableListOf<Boolean>()
        setSeekBar(value = value, enabled = true, onValueChange = { value = it }, onInteractionChanged = interactions::add)

        composeRule.onNodeWithTag(SEEK_BAR_TAG).performTouchInput {
            down(center.copy(x = center.x * 0.4f))
            moveTo(center.copy(x = center.x * 1.6f))
            up()
        }

        assertEquals(0.8f, value, 0.02f)
        assertEquals(listOf(true, false), interactions)
    }

    @Test
    fun disabled_seek_bar_does_not_change_value_or_start_interaction() {
        var value by mutableFloatStateOf(0.4f)
        val interactions = mutableListOf<Boolean>()
        setSeekBar(value = value, enabled = false, onValueChange = { value = it }, onInteractionChanged = interactions::add)

        composeRule.onNodeWithTag(SEEK_BAR_TAG).performTouchInput { click(center.copy(x = center.x * 1.5f)) }

        assertEquals(0.4f, value, 0f)
        assertEquals(emptyList<Boolean>(), interactions)
        composeRule.onNodeWithTag(SEEK_BAR_TAG).assertIsNotEnabled()
    }

    @Test
    fun semantics_expose_progress_and_set_progress() {
        var value by mutableFloatStateOf(0.25f)
        setSeekBar(value = value, enabled = true, onValueChange = { value = it })

        composeRule
            .onNodeWithTag(SEEK_BAR_TAG)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo(0.25f, 0f..1f),
                ),
            ).performSemanticsAction(SemanticsActions.SetProgress) { action -> action(0.6f) }

        assertEquals(0.6f, value, 0f)
    }

    @Test
    fun non_finite_input_and_semantics_target_normalize_to_zero() {
        var value by mutableFloatStateOf(Float.NaN)
        setSeekBar(value = value, enabled = true, onValueChange = { value = it })

        composeRule
            .onNodeWithTag(SEEK_BAR_TAG)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo(0f, 0f..1f),
                ),
            ).performSemanticsAction(SemanticsActions.SetProgress) { action -> action(Float.POSITIVE_INFINITY) }

        assertEquals(0f, value, 0f)
    }

    private fun setSeekBar(
        value: Float,
        enabled: Boolean,
        onValueChange: (Float) -> Unit,
        onInteractionChanged: (Boolean) -> Unit = {},
    ) {
        composeRule.setContent {
            MaterialTheme {
                DambomSeekBar(
                    value = value,
                    enabled = enabled,
                    contentDescription = "Playback position",
                    stateDescription = "0:15 / 1:00",
                    onValueChange = onValueChange,
                    onInteractionChanged = onInteractionChanged,
                    modifier = Modifier.testTag(SEEK_BAR_TAG),
                )
            }
        }
    }
}

private const val SEEK_BAR_TAG = "dambom-seek-bar"
