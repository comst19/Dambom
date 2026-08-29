package com.comst19.dambom.core.data.repository

import app.cash.turbine.test
import com.comst19.dambom.core.datastore.SettingsDataSource
import com.comst19.dambom.core.datastore.StoredSettings
import com.comst19.dambom.core.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultSettingsRepositoryTest {
    @Test
    fun `settings map DataStore values to domain and persist updates`() =
        runTest {
            val dataSource = FakeSettingsDataSource()
            val repository = DefaultSettingsRepository(dataSource)

            repository.settings.test {
                assertEquals(ThemeMode.SYSTEM, awaitItem().themeMode)

                repository.setThemeMode(ThemeMode.DARK)

                assertEquals(ThemeMode.DARK, awaitItem().themeMode)

                repository.setClipboardSuggestion(promptShown = true, enabled = true)

                val clipboardSettings = awaitItem()
                assertEquals(true, clipboardSettings.clipboardPromptShown)
                assertEquals(true, clipboardSettings.clipboardSuggestionEnabled)

                repository.setWifiOnlyDownloads(true)

                assertEquals(true, awaitItem().wifiOnlyDownloads)

                repository.setDownloadLocation(enabled = false, treeUri = "content://downloads/tree/dambom")

                val downloadLocation = awaitItem()
                assertEquals(false, downloadLocation.useConfiguredDownloadLocation)
                assertEquals("content://downloads/tree/dambom", downloadLocation.downloadTreeUri)
                cancelAndIgnoreRemainingEvents()
            }
        }
}

private class FakeSettingsDataSource : SettingsDataSource {
    private val settingsState = MutableStateFlow(StoredSettings())

    override val settings: Flow<StoredSettings> = settingsState

    override suspend fun setThemeMode(value: String) {
        settingsState.value = settingsState.value.copy(themeMode = value)
    }

    override suspend fun setClipboardSuggestion(
        promptShown: Boolean,
        enabled: Boolean,
    ) {
        settingsState.value =
            settingsState.value.copy(
                clipboardPromptShown = promptShown,
                clipboardSuggestionEnabled = enabled,
            )
    }

    override suspend fun setWifiOnlyDownloads(enabled: Boolean) {
        settingsState.value = settingsState.value.copy(wifiOnlyDownloads = enabled)
    }

    override suspend fun setDownloadLocation(
        enabled: Boolean,
        treeUri: String?,
    ) {
        settingsState.value =
            settingsState.value.copy(
                useConfiguredDownloadLocation = enabled,
                downloadTreeUri = treeUri,
            )
    }
}
