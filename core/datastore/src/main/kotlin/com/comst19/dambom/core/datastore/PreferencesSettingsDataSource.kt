package com.comst19.dambom.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
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
            dataStore.data.retryWhen { throwable, _ ->
                if (throwable is IOException) {
                    emit(emptyPreferences())
                    delay(READ_RETRY_DELAY_MILLIS)
                    true
                } else {
                    false
                }
            }

        override val settings: Flow<StoredSettings> =
            preferences.map { values ->
                StoredSettings(
                    sharedLinkAutoDownload = values[SHARED_AUTO_DOWNLOAD] ?: false,
                    sharedLinkQuality = values[SHARED_QUALITY] ?: "HIGHEST",
                    sharedLinkResolution = values[SHARED_RESOLUTION] ?: 1080,
                    themeMode = values[THEME_MODE] ?: SYSTEM_THEME_MODE,
                    clipboardPromptShown = values[CLIPBOARD_PROMPT_SHOWN] ?: false,
                    clipboardSuggestionEnabled = values[CLIPBOARD_SUGGESTION_ENABLED] ?: false,
                    wifiOnlyDownloads = values[WIFI_ONLY_DOWNLOADS] ?: false,
                    useConfiguredDownloadLocation = values[USE_CONFIGURED_DOWNLOAD_LOCATION] ?: true,
                    downloadTreeUri = values[DOWNLOAD_TREE_URI],
                    playbackSpeed = values[PLAYBACK_SPEED] ?: 1f,
                )
            }

        override suspend fun setPlaybackSpeed(speed: Float) {
            dataStore.edit { it[PLAYBACK_SPEED] = speed }
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

        override suspend fun setSharedLinkAutoDownload(enabled: Boolean) {
            dataStore.edit { it[SHARED_AUTO_DOWNLOAD] = enabled }
        }

        override suspend fun setSharedLinkQuality(
            quality: String,
            resolution: Int,
        ) {
            dataStore.edit {
                it[SHARED_QUALITY] = quality
                it[SHARED_RESOLUTION] = resolution
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
            val SHARED_AUTO_DOWNLOAD = booleanPreferencesKey("shared_link_auto_download")
            val SHARED_QUALITY = stringPreferencesKey("shared_link_quality")
            val SHARED_RESOLUTION = intPreferencesKey("shared_link_resolution")
            val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
            val THEME_MODE = stringPreferencesKey("theme_mode")
            val CLIPBOARD_PROMPT_SHOWN = booleanPreferencesKey("clipboard_prompt_shown")
            val CLIPBOARD_SUGGESTION_ENABLED = booleanPreferencesKey("clipboard_suggestion_enabled")
            val WIFI_ONLY_DOWNLOADS = booleanPreferencesKey("wifi_only_downloads")
            val USE_CONFIGURED_DOWNLOAD_LOCATION = booleanPreferencesKey("use_configured_download_location")
            val DOWNLOAD_TREE_URI = stringPreferencesKey("download_tree_uri")
            const val SYSTEM_THEME_MODE = "SYSTEM"
            const val READ_RETRY_DELAY_MILLIS = 1_000L
        }
    }
