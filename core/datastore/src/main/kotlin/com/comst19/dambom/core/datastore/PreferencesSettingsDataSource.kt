package com.comst19.dambom.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesSettingsDataSource
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : SettingsDataSource {
        private val preferences =
            dataStore.data.catch { throwable ->
                if (throwable is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw throwable
                }
            }

        override val settings: Flow<StoredSettings> =
            preferences.map { values ->
                StoredSettings(
                    themeMode = values[THEME_MODE] ?: SYSTEM_THEME_MODE,
                    clipboardPromptShown = values[CLIPBOARD_PROMPT_SHOWN] ?: false,
                    clipboardSuggestionEnabled = values[CLIPBOARD_SUGGESTION_ENABLED] ?: false,
                    wifiOnlyDownloads = values[WIFI_ONLY_DOWNLOADS] ?: false,
                )
            }

        override suspend fun setThemeMode(value: String) {
            dataStore.edit { preferences -> preferences[THEME_MODE] = value }
        }

        override suspend fun setClipboardSuggestion(
            promptShown: Boolean,
            enabled: Boolean,
        ) {
            dataStore.edit { preferences ->
                preferences[CLIPBOARD_PROMPT_SHOWN] = promptShown
                preferences[CLIPBOARD_SUGGESTION_ENABLED] = enabled
            }
        }

        override suspend fun setWifiOnlyDownloads(enabled: Boolean) {
            dataStore.edit { preferences -> preferences[WIFI_ONLY_DOWNLOADS] = enabled }
        }

        private companion object {
            val THEME_MODE = stringPreferencesKey("theme_mode")
            val CLIPBOARD_PROMPT_SHOWN = booleanPreferencesKey("clipboard_prompt_shown")
            val CLIPBOARD_SUGGESTION_ENABLED = booleanPreferencesKey("clipboard_suggestion_enabled")
            val WIFI_ONLY_DOWNLOADS = booleanPreferencesKey("wifi_only_downloads")
            const val SYSTEM_THEME_MODE = "SYSTEM"
        }
    }

@Module
@InstallIn(SingletonComponent::class)
abstract class DataStoreModule {
    @Binds
    abstract fun bindSettingsDataSource(implementation: PreferencesSettingsDataSource): SettingsDataSource
}

@Module
@InstallIn(SingletonComponent::class)
object DataStoreProviderModule {
    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        androidx.datastore.preferences.core.PreferenceDataStoreFactory.create {
            context.filesDir.resolve("datastore/settings.preferences_pb")
        }
}
