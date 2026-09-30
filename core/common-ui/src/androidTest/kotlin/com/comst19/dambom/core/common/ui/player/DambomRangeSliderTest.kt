package com.comst19.dambom.core.common.ui.player

import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalMaterial3Api::class)
class DambomRangeSliderTest {
    @get:Rule val composeRule = createComposeRule()
    private var range by mutableStateOf(0.25f..0.75f)
    private var finished = 0
    private var density = Density(1f)

    @Test
    fun shared_visuals_keep_a_thin_track_and_round_sized_thumbs() {
        showRange()
        val track = composeRule.onNodeWithTag("track", true).getUnclippedBoundsInRoot()
        assertRoundedDimension(4.dp, track.bottom - track.top)
        listOf("start", "end").forEach { tag ->
            val thumb = composeRule.onNodeWithTag(tag, true).getUnclippedBoundsInRoot()
            assertRoundedDimension(16.dp, thumb.right - thumb.left)
            assertRoundedDimension(16.dp, thumb.bottom - thumb.top)
        }
        val slider = composeRule.onNodeWithTag("range").getUnclippedBoundsInRoot()
        assertRoundedDimension(48.dp, slider.bottom - slider.top)
    }

    @Test
    fun both_accessible_handles_update_independently_and_finish_the_selection() {
        showRange()
        val handles = composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
        handles[0].performSemanticsAction(SemanticsActions.SetProgress) { it(0.4f) }
        assertEquals(0.4f, range.start, 0.001f)
        assertEquals(0.75f, range.endInclusive, 0.001f)
        handles[1].performSemanticsAction(SemanticsActions.SetProgress) { it(0.9f) }
        assertEquals(0.4f, range.start, 0.001f)
        assertEquals(0.9f, range.endInclusive, 0.001f)
        assertEquals(2, finished)
    }

    @Test
    fun disabled_range_preserves_values_and_disables_both_handles() {
        showRange(enabled = false)
        val handles = composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
        handles[0].assertIsNotEnabled()
        handles[1].assertIsNotEnabled()
        composeRule.onNodeWithTag("range").performTouchInput { click(center.copy(x = 0f)) }
        assertEquals(0.25f..0.75f, range)
        assertEquals(0, finished)
    }

    private fun showRange(enabled: Boolean = true) {
        composeRule.setContent {
            MaterialTheme {
                density = LocalDensity.current
                RangeSlider(
                    value = range,
                    onValueChange = { range = it },
                    onValueChangeFinished = { finished++ },
                    enabled = enabled,
                    modifier = Modifier.width(320.dp).testTag("range"),
                    startThumb = { DambomSliderDefaults.Thumb(enabled, Modifier.testTag("start")) },
                    endThumb = { DambomSliderDefaults.Thumb(enabled, Modifier.testTag("end")) },
                    track = { DambomSliderDefaults.RangeTrack(it, enabled, Modifier.testTag("track")) },
                )
            }
        }
    }

    private fun assertRoundedDimension(
        expected: Dp,
        actual: Dp,
    ) {
        val rounded = with(density) { expected.roundToPx().toDp() }
        assertEquals(rounded.value, actual.value, 0.001f)
    }
}
