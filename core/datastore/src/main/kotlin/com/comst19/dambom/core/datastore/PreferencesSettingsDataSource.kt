package com.comst19.dambom.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
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
                    useConfiguredDownloadLocation = values[USE_CONFIGURED_DOWNLOAD_LOCATION] ?: true,
                    downloadTreeUri = values[DOWNLOAD_TREE_URI],
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

        override suspend fun setDownloadLocation(
            enabled: Boolean,
            treeUri: String?,
        ) {
            dataStore.edit { preferences ->
                preferences[USE_CONFIGURED_DOWNLOAD_LOCATION] = enabled
                if (treeUri == null) {
                    preferences.remove(DOWNLOAD_TREE_URI)
                } else {
                    preferences[DOWNLOAD_TREE_URI] = treeUri
                }
            }
        }

        private companion object {
            val THEME_MODE = stringPreferencesKey("theme_mode")
            val CLIPBOARD_PROMPT_SHOWN = booleanPreferencesKey("clipboard_prompt_shown")
            val CLIPBOARD_SUGGESTION_ENABLED = booleanPreferencesKey("clipboard_suggestion_enabled")
            val WIFI_ONLY_DOWNLOADS = booleanPreferencesKey("wifi_only_downloads")
            val USE_CONFIGURED_DOWNLOAD_LOCATION = booleanPreferencesKey("use_configured_download_location")
            val DOWNLOAD_TREE_URI = stringPreferencesKey("download_tree_uri")
            const val SYSTEM_THEME_MODE = "SYSTEM"
        }
    }
