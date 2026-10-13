package com.comst19.dambom.core.common.ui.player

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class DambomSeekBarGestureTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `a second finger does not take over seeking`() {
        val positions = mutableListOf<Float>()
        composeRule.setContent {
            MaterialTheme {
                DambomSeekBar(
                    value = 0f,
                    enabled = true,
                    contentDescription = "Seek",
                    stateDescription = "",
                    onValueChange = { positions.add(it) },
                    onInteractionChanged = {},
                    modifier = Modifier.width(200.dp).testTag("seek"),
                )
            }
        }

        composeRule.onNodeWithTag("seek").performTouchInput {
            down(0, center.copy(x = center.x / 2f))
            down(1, center.copy(x = center.x * 1.8f))
            advanceEventTime(100)
            up(0)
            up(1)
        }

        assertEquals(0.25f, positions.last(), 0.01f)
    }
}
