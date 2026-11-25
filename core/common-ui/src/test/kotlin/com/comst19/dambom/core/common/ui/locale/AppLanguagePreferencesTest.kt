package com.comst19.dambom.core.common.ui.locale

import android.content.Context
import android.os.Bundle
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.core.datastore.di.createAppLanguageDataSource
import com.comst19.dambom.core.domain.model.AppLanguagePreference
import com.comst19.dambom.core.domain.repository.AppLanguagePreferenceStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35], qualifiers = "b+ks+IN")
class AppLanguagePreferencesTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var activity: ActivityController<LanguageTestActivity>

    @Before
    fun setUp() {
        context
            .getSharedPreferences(LEGACY_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit(commit = true) { clear() }
        activity = Robolectric.buildActivity(LanguageTestActivity::class.java)
        activity.setup()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    }

    @After
    fun tearDown() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        activity.pause().stop().destroy()
    }

    @Test
    fun `automatic choice stays represented as system in settings`() =
        runTest {
            withPreferences { preferences ->
                preferences.syncSystemLanguage()

                assertEquals("hi", AppCompatDelegate.getApplicationLocales().toLanguageTags())
                assertEquals("", preferences.selectedLanguageTags())
                preferences.syncSystemLanguage()
                assertEquals("", preferences.selectedLanguageTags())
            }
        }

    @Test
    fun `automatic selection follows a changed system locale on reentry`() =
        runTest {
            withPreferences { preferences ->
                preferences.syncSystemLanguage()
                assertEquals("hi", AppCompatDelegate.getApplicationLocales().toLanguageTags())

                RuntimeEnvironment.setQualifiers("de-rDE")
                preferences.syncSystemLanguage()

                assertEquals("de", AppCompatDelegate.getApplicationLocales().toLanguageTags())
                assertEquals("", preferences.selectedLanguageTags())
            }
        }

    @Test
    @Config(qualifiers = "pa-rPK")
    fun `regional script is inferred before matching the supported translation`() =
        runTest {
            withPreferences { preferences ->
                preferences.syncSystemLanguage()

                assertEquals("ur", AppCompatDelegate.getApplicationLocales().toLanguageTags())
                assertEquals("", preferences.selectedLanguageTags())
            }
        }

    @Test
    @Config(qualifiers = "zh-rTW")
    fun `traditional Chinese system selection preserves its script`() =
        runTest {
            withPreferences { preferences ->
                preferences.syncSystemLanguage()

                assertEquals("zh-Hant", AppCompatDelegate.getApplicationLocales().toLanguageTags())
                assertEquals("", preferences.selectedLanguageTags())
                preferences.apply("zh-Hans")
                preferences.syncSystemLanguage()
                assertEquals("zh-Hans", preferences.selectedLanguageTags())
                preferences.apply("")
                assertEquals("zh-Hant", AppCompatDelegate.getApplicationLocales().toLanguageTags())
            }
        }

    @Test
    fun `explicit choice survives automatic synchronization`() =
        runTest {
            withPreferences { preferences ->
                preferences.apply("de")
                preferences.syncSystemLanguage()

                assertEquals("de", preferences.selectedLanguageTags())
                assertEquals("de", AppCompatDelegate.getApplicationLocales().toLanguageTags())
            }
        }

    @Test
    fun `existing explicit preference survives upgrade`() =
        runTest {
            withPreferences { preferences ->
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ko"))
                preferences.syncSystemLanguage()

                assertEquals("ko", preferences.selectedLanguageTags())
            }
        }

    @Test
    fun `returning to system removes explicit selection`() =
        runTest {
            withPreferences { preferences ->
                preferences.apply("de")
                preferences.apply("")

                assertEquals("", preferences.selectedLanguageTags())
                assertFalse(AppCompatDelegate.getApplicationLocales().isEmpty)
            }
        }

    @Test
    fun `external app language change is kept`() =
        runTest {
            withPreferences { preferences ->
                preferences.apply("")
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("fr"))
                preferences.syncSystemLanguage()

                assertEquals("fr", preferences.selectedLanguageTags())
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("hi"))
                preferences.syncSystemLanguage()
                assertEquals("hi", preferences.selectedLanguageTags())
            }
        }

    @Test
    fun `migrated automatic legacy preference keeps following system locale changes`() =
        runTest {
            context.getSharedPreferences(LEGACY_PREFERENCES_NAME, Context.MODE_PRIVATE).edit(commit = true) {
                putBoolean(FOLLOW_SYSTEM_KEY, true)
                putString(AUTOMATIC_TAG_KEY, "hi")
            }
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("hi"))

            withPreferences { preferences ->
                assertEquals("", preferences.selectedLanguageTags())

                RuntimeEnvironment.setQualifiers("de-rDE")
                preferences.syncSystemLanguage()

                assertEquals("de", AppCompatDelegate.getApplicationLocales().toLanguageTags())
                assertEquals("", preferences.selectedLanguageTags())
            }
        }

    @Test
    fun `migrated manual legacy preference survives syncSystemLanguage`() =
        runTest {
            context.getSharedPreferences(LEGACY_PREFERENCES_NAME, Context.MODE_PRIVATE).edit(commit = true) {
                putBoolean(FOLLOW_SYSTEM_KEY, false)
            }
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("de"))

            withPreferences { preferences ->
                preferences.syncSystemLanguage()

                assertEquals("de", preferences.selectedLanguageTags())
                assertEquals("de", AppCompatDelegate.getApplicationLocales().toLanguageTags())
            }
        }

    @Test
    fun `apply persists DataStore state before updating AppCompatDelegate and serializes with sync`() =
        runTest {
            val writeStarted = CompletableDeferred<Unit>()
            val writeGate = CompletableDeferred<Unit>()
            val store =
                GatedAppLanguagePreferenceStore(
                    initial = AppLanguagePreference(followSystem = true, automaticTag = "hi"),
                    clearStarted = writeStarted,
                    clearGate = writeGate,
                )
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("hi"))
            val preferences = DefaultAppLanguagePreferences(context, store, Dispatchers.Unconfined)

            val applyJob = launch { preferences.apply("de") }
            writeStarted.await()
            val syncJob = launch { preferences.syncSystemLanguage() }
            runCurrent()

            assertEquals("hi", AppCompatDelegate.getApplicationLocales().toLanguageTags())

            writeGate.complete(Unit)
            advanceUntilIdle()
            applyJob.join()
            syncJob.join()

            assertEquals("de", AppCompatDelegate.getApplicationLocales().toLanguageTags())
            assertEquals("de", preferences.selectedLanguageTags())
            assertEquals(AppLanguagePreference(followSystem = false, automaticTag = null), store.read())
        }

    @Test
    fun `apply still updates application locales when the last activity finishes mid write`() =
        runTest {
            val writeStarted = CompletableDeferred<Unit>()
            val writeGate = CompletableDeferred<Unit>()
            val store =
                GatedAppLanguagePreferenceStore(
                    initial = AppLanguagePreference(followSystem = false, automaticTag = null),
                    clearStarted = writeStarted,
                    clearGate = writeGate,
                )
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            val preferences = DefaultAppLanguagePreferences(context, store, Dispatchers.Main.immediate)

            val applyJob = launch { preferences.apply("de") }
            writeStarted.await()
            activity.pause().stop().destroy()
            applyJob.cancel()

            writeGate.complete(Unit)
            advanceUntilIdle()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            applyJob.join()

            assertEquals("de", preferences.selectedLanguageTags())

            activity = Robolectric.buildActivity(LanguageTestActivity::class.java).setup()
            assertEquals("de", AppCompatDelegate.getApplicationLocales().toLanguageTags())
            assertEquals("de", preferences.selectedLanguageTags())
        }

    @Test
    fun `storage failure during sync does not alter AppCompatDelegate locales`() =
        runTest {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ko"))
            val failingStore =
                object : AppLanguagePreferenceStore {
                    override suspend fun read(): AppLanguagePreference = throw IOException("Read failed")

                    override suspend fun setAutomatic(languageTag: String) = error("Must not write")

                    override suspend fun clearAutomatic() = error("Must not write")
                }
            val preferences = DefaultAppLanguagePreferences(context, failingStore, Dispatchers.Unconfined)

            val result = runCatching { preferences.syncSystemLanguage() }

            assertTrue(result.exceptionOrNull() is IOException)
            assertEquals("ko", AppCompatDelegate.getApplicationLocales().toLanguageTags())
        }

    private suspend fun TestScope.withPreferences(block: suspend (AppLanguagePreferences) -> Unit) {
        val dataStoreScope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        try {
            val dataSource =
                createAppLanguageDataSource(
                    context = context,
                    scope = dataStoreScope,
                    produceFile = { temporaryFolder.newFile("settings.preferences_pb") },
                )
            val store =
                object : AppLanguagePreferenceStore {
                    override suspend fun read(): AppLanguagePreference {
                        val stored = dataSource.read()
                        return AppLanguagePreference(
                            followSystem = stored.followSystem,
                            automaticTag = stored.automaticTag,
                        )
                    }

                    override suspend fun setAutomatic(languageTag: String) = dataSource.setAutomatic(languageTag)

                    override suspend fun clearAutomatic() = dataSource.clearAutomatic()
                }
            val preferences = DefaultAppLanguagePreferences(context, store, Dispatchers.Unconfined)
            block(preferences)
        } finally {
            dataStoreScope.cancel()
        }
    }

    private companion object {
        const val LEGACY_PREFERENCES_NAME = "app_language"
        const val FOLLOW_SYSTEM_KEY = "follow_system"
        const val AUTOMATIC_TAG_KEY = "automatic_tag"
    }
}

private class GatedAppLanguagePreferenceStore(
    initial: AppLanguagePreference,
    private val clearStarted: CompletableDeferred<Unit>,
    private val clearGate: CompletableDeferred<Unit>,
) : AppLanguagePreferenceStore {
    private var state = initial

    override suspend fun read(): AppLanguagePreference = state

    override suspend fun setAutomatic(languageTag: String) {
        state = AppLanguagePreference(followSystem = true, automaticTag = languageTag)
    }

    override suspend fun clearAutomatic() {
        clearStarted.complete(Unit)
        clearGate.await()
        state = AppLanguagePreference(followSystem = false, automaticTag = null)
    }
}

class LanguageTestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(androidx.appcompat.R.style.Theme_AppCompat)
        super.onCreate(savedInstanceState)
    }
}
