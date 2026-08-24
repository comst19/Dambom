package com.comst19.dambom.core.datastore

import kotlinx.coroutines.flow.Flow

interface SettingsDataSource {
    val settings: Flow<StoredSettings>

    suspend fun setThemeMode(value: String)

    suspend fun setClipboardSuggestion(
        promptShown: Boolean,
        enabled: Boolean,
    )

    suspend fun setWifiOnlyDownloads(enabled: Boolean)
}

data class StoredSettings(
    val themeMode: String = "SYSTEM",
    val clipboardPromptShown: Boolean = false,
    val clipboardSuggestionEnabled: Boolean = false,
    val wifiOnlyDownloads: Boolean = false,
)
