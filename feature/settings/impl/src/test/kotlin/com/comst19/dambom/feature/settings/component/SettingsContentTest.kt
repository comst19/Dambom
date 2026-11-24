package com.comst19.dambom.feature.settings.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.comst19.dambom.core.common.locale.AppLanguage
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.core.domain.model.ThemeMode
import com.comst19.dambom.feature.settings.contract.SaveLocationMode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ko")
class SettingsContentTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `default folder method shows the configured folder`() {
        setSettingsContent(saveLocationMode = SaveLocationMode.DEFAULT_FOLDER, downloadLocation = "Movies/Dambom")

        composeRule.onNodeWithText("저장 방법").assertIsDisplayed()
        composeRule.onNodeWithText("기본 폴더에 저장").assertIsDisplayed()
        composeRule.onNodeWithText("기본 폴더").assertIsDisplayed()
        composeRule.onNodeWithText("Movies/Dambom").assertIsDisplayed()
    }

    @Test
    fun `choose each time method hides the inactive default folder`() {
        setSettingsContent(saveLocationMode = SaveLocationMode.CHOOSE_EACH_TIME, downloadLocation = "Movies/Dambom")

        composeRule.onNodeWithText("저장할 때마다 선택").assertIsDisplayed()
        composeRule.onNodeWithText("기본 폴더").assertDoesNotExist()
        composeRule.onNodeWithText("Movies/Dambom").assertDoesNotExist()
    }

    @Test
    fun `save method row opens the method selector`() {
        var clicked = false
        setSettingsContent(
            saveLocationMode = SaveLocationMode.DEFAULT_FOLDER,
            downloadLocation = "Download/Dambom",
            onSaveMethodClick = { clicked = true },
        )

        composeRule.onNodeWithText("저장 방법").performClick()

        assertTrue(clicked)
    }

    @Test
    @Config(qualifiers = "hi")
    fun `automatic language row displays the effective language name`() {
        setSettingsContent(language = AppLanguage.SYSTEM)

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("हिन्दी"))
        composeRule.onNodeWithText("हिन्दी").assertIsDisplayed()
        composeRule.onNodeWithText("한국어").assertDoesNotExist()
    }

    private fun setSettingsContent(
        saveLocationMode: SaveLocationMode = SaveLocationMode.DEFAULT_FOLDER,
        downloadLocation: String = "Download/Dambom",
        language: AppLanguage = AppLanguage.KOREAN,
        onSaveMethodClick: () -> Unit = {},
    ) {
        composeRule.setContent {
            DambomTheme {
                SettingsContent(
                    state =
                        SettingsContentState(
                            themeMode = ThemeMode.SYSTEM,
                            language = language,
                            clipboardSuggestionEnabled = false,
                            wifiOnlyDownloads = false,
                            saveLocationMode = saveLocationMode,
                            downloadLocation = downloadLocation,
                            versionName = "1.0.1",
                        ),
                    actions = settingsActions(),
                    onSaveMethodClick = onSaveMethodClick,
                    onThemeClick = {},
                    onLanguageClick = {},
                )
            }
        }
    }
}

private fun settingsActions() =
    SettingsActions(
        onBack = {},
        saveToDevice =
            DeviceSaveActions(
                onSaveLocationModeChange = {},
                onDownloadLocationClick = {},
            ),
        download =
            DownloadSettingsActions(
                onWifiOnlyDownloadsChange = {},
            ),
        general =
            GeneralSettingsActions(
                onThemeModeChange = {},
                onLanguageChange = {},
                onClipboardSuggestionChange = {},
            ),
        support =
            SupportSettingsActions(
                onHelp = {},
                onBugReport = {},
                onFeatureRequest = {},
                onLicenses = {},
                onTerms = {},
                onPrivacy = {},
            ),
    )
