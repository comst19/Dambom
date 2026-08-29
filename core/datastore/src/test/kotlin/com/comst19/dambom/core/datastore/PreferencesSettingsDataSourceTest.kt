package com.comst19.dambom.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class PreferencesSettingsDataSourceTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `default and updates are emitted by DataStore`() =
        runTest {
            val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
            val store =
                PreferenceDataStoreFactory.create(scope = scope) {
                    temporaryFolder.root.resolve("settings.preferences_pb")
                }
            val source = PreferencesSettingsDataSource(store)

            source.settings.test {
                assertEquals(StoredSettings(), awaitItem())
                source.setThemeMode("DARK")
                assertEquals(StoredSettings(themeMode = "DARK"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            source.settings.test {
                assertEquals(StoredSettings(themeMode = "DARK"), awaitItem())
                source.setClipboardSuggestion(promptShown = true, enabled = true)
                assertEquals(
                    StoredSettings(
                        themeMode = "DARK",
                        clipboardPromptShown = true,
                        clipboardSuggestionEnabled = true,
                    ),
                    awaitItem(),
                )
                cancelAndIgnoreRemainingEvents()
            }
            source.settings.test {
                awaitItem()
                source.setWifiOnlyDownloads(true)
                assertEquals(
                    StoredSettings(
                        themeMode = "DARK",
                        clipboardPromptShown = true,
                        clipboardSuggestionEnabled = true,
                        wifiOnlyDownloads = true,
                    ),
                    awaitItem(),
                )
                cancelAndIgnoreRemainingEvents()
            }
            source.settings.test {
                awaitItem()
                source.setDownloadLocation(enabled = false, treeUri = "content://downloads/tree/dambom")
                assertEquals(
                    StoredSettings(
                        themeMode = "DARK",
                        clipboardPromptShown = true,
                        clipboardSuggestionEnabled = true,
                        wifiOnlyDownloads = true,
                        useConfiguredDownloadLocation = false,
                        downloadTreeUri = "content://downloads/tree/dambom",
                    ),
                    awaitItem(),
                )
                cancelAndIgnoreRemainingEvents()
            }
            scope.cancel()
        }

    @Test
    fun `read IOException emits the system theme default`() =
        runTest {
            val source = PreferencesSettingsDataSource(IoExceptionDataStore)

            source.settings.test {
                assertEquals(StoredSettings(), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}

private object IoExceptionDataStore : androidx.datastore.core.DataStore<Preferences> {
    override val data = flow<Preferences> { throw IOException("Read failed") }

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = error("Not used")
}
