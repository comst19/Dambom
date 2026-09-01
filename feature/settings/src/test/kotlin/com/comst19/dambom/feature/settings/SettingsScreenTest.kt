package com.comst19.dambom.feature.settings

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.core.domain.model.ThemeMode
import com.comst19.dambom.feature.settings.component.DeviceSaveActions
import com.comst19.dambom.feature.settings.component.DownloadSettingsActions
import com.comst19.dambom.feature.settings.component.GeneralSettingsActions
import com.comst19.dambom.feature.settings.component.SettingsActions
import com.comst19.dambom.feature.settings.component.SettingsContentState
import com.comst19.dambom.feature.settings.component.SupportSettingsActions
import com.comst19.dambom.feature.settings.contract.AppLanguage
import com.comst19.dambom.feature.settings.contract.SaveLocationMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ko")
class SettingsScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `save method dialog changes to choose each time`() {
        var selectedMode: SaveLocationMode? = null
        composeRule.setContent {
            DambomTheme {
                SettingsScreen(
                    state = settingsState(),
                    actions = settingsActions(onSaveLocationModeChange = { selectedMode = it }),
                )
            }
        }

        composeRule.onNodeWithText("저장 방법").performClick()
        composeRule.onNodeWithText("저장할 때마다 선택").performClick()

        assertEquals(SaveLocationMode.CHOOSE_EACH_TIME, selectedMode)
    }
}

private fun settingsState() =
    SettingsContentState(
        themeMode = ThemeMode.SYSTEM,
        language = AppLanguage.KOREAN,
        clipboardSuggestionEnabled = false,
        wifiOnlyDownloads = false,
        saveLocationMode = SaveLocationMode.DEFAULT_FOLDER,
        downloadLocation = "Download/Dambom",
        versionName = "1.0.1",
    )

private fun settingsActions(onSaveLocationModeChange: (SaveLocationMode) -> Unit) =
    SettingsActions(
        onBack = {},
        saveToDevice =
            DeviceSaveActions(
                onSaveLocationModeChange = onSaveLocationModeChange,
                onDownloadLocationClick = {},
            ),
        download = DownloadSettingsActions(onWifiOnlyDownloadsChange = {}),
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
