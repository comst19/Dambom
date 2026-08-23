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

            source.themeMode.test {
                assertEquals("SYSTEM", awaitItem())
                source.setThemeMode("DARK")
                assertEquals("DARK", awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            scope.cancel()
        }

    @Test
    fun `read IOException emits the system theme default`() =
        runTest {
            val source = PreferencesSettingsDataSource(IoExceptionDataStore)

            source.themeMode.test {
                assertEquals("SYSTEM", awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}

private object IoExceptionDataStore : androidx.datastore.core.DataStore<Preferences> {
    override val data = flow<Preferences> { throw IOException("Read failed") }

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = error("Not used")
}
