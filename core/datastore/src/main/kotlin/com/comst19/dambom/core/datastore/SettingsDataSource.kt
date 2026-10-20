package com.comst19.dambom.core.datastore

import kotlinx.coroutines.flow.Flow

interface SettingsDataSource {
    val settings: Flow<StoredSettings>

    suspend fun setPlaybackSpeed(speed: Float)

    suspend fun setThemeMode(value: String)

    suspend fun setClipboardSuggestion(
        promptShown: Boolean,
        enabled: Boolean,
    )

    suspend fun setSharedLinkAutoDownload(enabled: Boolean)

    suspend fun setSharedLinkQuality(
        quality: String,
        resolution: Int,
    )

    suspend fun setWifiOnlyDownloads(enabled: Boolean)

    suspend fun setDownloadLocation(
        enabled: Boolean,
        treeUri: String?,
    )
}

data class StoredSettings(
    val sharedLinkAutoDownload: Boolean = false,
    val sharedLinkQuality: String = "HIGHEST",
    val sharedLinkResolution: Int = 1080,
    val themeMode: String = "SYSTEM",
    val clipboardPromptShown: Boolean = false,
    val clipboardSuggestionEnabled: Boolean = false,
    val wifiOnlyDownloads: Boolean = false,
    val useConfiguredDownloadLocation: Boolean = true,
    val downloadTreeUri: String? = null,
    val playbackSpeed: Float = 1f,
)
