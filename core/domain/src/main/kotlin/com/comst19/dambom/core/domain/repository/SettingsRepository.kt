package com.comst19.dambom.core.domain.repository

import com.comst19.dambom.core.domain.model.AppSettings
import com.comst19.dambom.core.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setClipboardSuggestion(
        promptShown: Boolean,
        enabled: Boolean,
    )

    suspend fun setWifiOnlyDownloads(enabled: Boolean)
}
