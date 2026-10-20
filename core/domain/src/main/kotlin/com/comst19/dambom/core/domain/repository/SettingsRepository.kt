package com.comst19.dambom.core.domain.repository

import com.comst19.dambom.core.domain.model.AppSettings
import com.comst19.dambom.core.domain.model.SharedLinkQuality
import com.comst19.dambom.core.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setPlaybackSpeed(speed: Float)

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setClipboardSuggestion(
        promptShown: Boolean,
        enabled: Boolean,
    )

    suspend fun setSharedLinkAutoDownload(enabled: Boolean)

    suspend fun setSharedLinkQuality(
        quality: SharedLinkQuality,
        resolution: Int,
    )

    suspend fun setWifiOnlyDownloads(enabled: Boolean)

    suspend fun setDownloadLocation(
        enabled: Boolean,
        treeUri: String?,
    )
}
