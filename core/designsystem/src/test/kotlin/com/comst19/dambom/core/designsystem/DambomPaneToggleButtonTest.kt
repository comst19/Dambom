package com.comst19.dambom.core.designsystem

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp")
class DambomPaneToggleButtonTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `click toggles but long press only explains the current action`() {
        val checked = mutableStateOf(false)
        composeRule.setContent {
            DambomTheme {
                DambomPaneToggleButton(
                    checked = checked.value,
                    onCheckedChange = { checked.value = it },
                    actionLabel = if (checked.value) "Hide results" else "Show results",
                    modifier = Modifier.testTag("pane-toggle"),
                )
            }
        }
        composeRule
            .onNodeWithTag("pane-toggle")
            .assertIsOff()
            .performClick()
            .assertIsOn()
        composeRule.onNodeWithTag("pane-toggle").performTouchInput { longClick() }
        composeRule.onNodeWithText("Hide results").assertIsDisplayed()
        composeRule.onNodeWithTag("pane-toggle").assertIsOn()
    }
}
