package com.comst19.dambom.core.designsystem

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DambomButtonTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `slot content updates while enabled state controls clicks`() {
        val enabled = mutableStateOf(true)
        val label = mutableStateOf("Continue")
        var clicks = 0
        composeRule.setContent {
            DambomTheme {
                DambomButton(onClick = { clicks++ }, enabled = enabled.value) {
                    Text(label.value)
                }
            }
        }

        composeRule.onNodeWithText("Continue").assertIsDisplayed().performClick()
        assertEquals(1, clicks)

        composeRule.runOnIdle {
            enabled.value = false
            label.value = "Waiting"
        }
        composeRule
            .onNodeWithText("Waiting")
            .assertIsDisplayed()
            .assertIsNotEnabled()
            .performClick()
        composeRule.runOnIdle { assertEquals(1, clicks) }
    }
}
