package com.comst19.dambom.core.datastore

import android.content.Context
import androidx.core.content.edit
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.core.datastore.di.createSettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class PreferencesAppLanguageDataSourceTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        context
            .getSharedPreferences(PreferencesAppLanguageDataSource.LEGACY_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit(commit = true) { clear() }
    }

    @Test
    fun `migrates legacy automatic language state and cleans up SharedPreferences`() =
        runTest {
            val legacy =
                context.getSharedPreferences(
                    PreferencesAppLanguageDataSource.LEGACY_PREFERENCES_NAME,
                    Context.MODE_PRIVATE,
                )
            legacy.edit(commit = true) {
                putBoolean(PreferencesAppLanguageDataSource.FOLLOW_SYSTEM_KEY_NAME, true)
                putString(PreferencesAppLanguageDataSource.AUTOMATIC_TAG_KEY_NAME, "hi")
            }

            val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
            val dataStore =
                createSettingsDataStore(
                    context = context,
                    scope = scope,
                    produceFile = { temporaryFolder.newFile("automatic.preferences_pb") },
                )
            val dataSource = PreferencesAppLanguageDataSource(dataStore)

            val stored = dataSource.read()

            assertEquals(StoredAppLanguage(followSystem = true, automaticTag = "hi"), stored)
            assertFalse(legacy.contains(PreferencesAppLanguageDataSource.FOLLOW_SYSTEM_KEY_NAME))
            assertFalse(legacy.contains(PreferencesAppLanguageDataSource.AUTOMATIC_TAG_KEY_NAME))
            scope.cancel()
        }

    @Test
    fun `migrates legacy manual language state without clobbering unrelated settings`() =
        runTest {
            val file = temporaryFolder.newFile("manual.preferences_pb")
            val seedScope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
            val seedStore =
                createSettingsDataStore(
                    context = context,
                    scope = seedScope,
                    produceFile = { file },
                )
            PreferencesSettingsDataSource(seedStore).setThemeMode("DARK")
            seedScope.cancel()
            testScheduler.advanceUntilIdle()

            val legacy =
                context.getSharedPreferences(
                    PreferencesAppLanguageDataSource.LEGACY_PREFERENCES_NAME,
                    Context.MODE_PRIVATE,
                )
            legacy.edit(commit = true) {
                putBoolean(PreferencesAppLanguageDataSource.FOLLOW_SYSTEM_KEY_NAME, false)
            }

            val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
            val dataStore =
                createSettingsDataStore(
                    context = context,
                    scope = scope,
                    produceFile = { file },
                )
            val languageSource = PreferencesAppLanguageDataSource(dataStore)
            val settingsSource = PreferencesSettingsDataSource(dataStore)

            assertEquals(StoredAppLanguage(followSystem = false, automaticTag = null), languageSource.read())
            assertEquals("DARK", settingsSource.settings.first().themeMode)
            assertFalse(legacy.contains(PreferencesAppLanguageDataSource.FOLLOW_SYSTEM_KEY_NAME))
            scope.cancel()
        }

    @Test
    fun `updates automatic and manual states without altering other settings`() =
        runTest {
            val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
            val dataStore =
                createSettingsDataStore(
                    context = context,
                    scope = scope,
                    produceFile = { temporaryFolder.newFile("updates.preferences_pb") },
                )
            val languageSource = PreferencesAppLanguageDataSource(dataStore)
            val settingsSource = PreferencesSettingsDataSource(dataStore)

            settingsSource.setWifiOnlyDownloads(true)
            languageSource.setAutomatic("zh-Hant")

            assertEquals(StoredAppLanguage(followSystem = true, automaticTag = "zh-Hant"), languageSource.read())
            assertTrue(settingsSource.settings.first().wifiOnlyDownloads)

            languageSource.clearAutomatic()

            val cleared = languageSource.read()
            assertFalse(cleared.followSystem)
            assertNull(cleared.automaticTag)
            assertTrue(settingsSource.settings.first().wifiOnlyDownloads)
            scope.cancel()
        }

    @Test
    fun `read propagates IOException instead of fabricating default language state`() =
        runTest {
            val failingStore =
                object : DataStore<Preferences> {
                    override val data = flow<Preferences> { throw IOException("Disk read failure") }

                    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                        error("Not used")
                    }
                }
            val languageSource = PreferencesAppLanguageDataSource(failingStore)

            val result = runCatching { languageSource.read() }

            assertTrue(result.exceptionOrNull() is IOException)
        }
}
