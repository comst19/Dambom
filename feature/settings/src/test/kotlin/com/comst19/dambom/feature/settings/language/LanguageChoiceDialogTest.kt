package com.comst19.dambom.feature.settings.language

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.comst19.dambom.core.common.locale.AppLanguage
import com.comst19.dambom.core.designsystem.DambomTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en")
class LanguageChoiceDialogTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `long language list scrolls to the last entry and selects it`() {
        var selected: AppLanguage? = null
        composeRule.setContent {
            DambomTheme {
                LanguageChoiceDialog(AppLanguage.SYSTEM, onDismiss = {}, onSelect = { selected = it })
            }
        }

        composeRule.onNodeWithText("System default").assertIsDisplayed().assertIsSelected()
        composeRule.onNodeWithText("Русский").assertDoesNotExist()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Русский"))
        composeRule.onNodeWithText("Русский").assertIsDisplayed().performClick()

        assertEquals(AppLanguage.RUSSIAN, selected)
    }

    @Test
    fun `opening the list reveals the selected language near its end`() {
        composeRule.setContent {
            DambomTheme {
                LanguageChoiceDialog(AppLanguage.RUSSIAN, onDismiss = {}, onSelect = {})
            }
        }

        composeRule.onNodeWithText("Русский").assertIsDisplayed().assertIsSelected()
        composeRule.onNodeWithText("System default").assertDoesNotExist()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("System default"))
        composeRule.onNodeWithText("System default").assertIsDisplayed()
    }

    @Test
    fun `swiping the language list scrolls past its initial entries`() {
        composeRule.setContent {
            DambomTheme {
                LanguageChoiceDialog(AppLanguage.SYSTEM, onDismiss = {}, onSelect = {})
            }
        }

        composeRule.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("System default").assertDoesNotExist()
    }
}
