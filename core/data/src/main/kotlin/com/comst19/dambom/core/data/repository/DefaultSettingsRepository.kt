package com.comst19.dambom.core.data.repository

import com.comst19.dambom.core.datastore.SettingsDataSource
import com.comst19.dambom.core.domain.model.AppSettings
import com.comst19.dambom.core.domain.model.SUPPORTED_PLAYBACK_SPEEDS
import com.comst19.dambom.core.domain.model.SharedLinkQuality
import com.comst19.dambom.core.domain.model.ThemeMode
import com.comst19.dambom.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DefaultSettingsRepository
    @Inject
    constructor(
        private val dataSource: SettingsDataSource,
    ) : SettingsRepository {
        override val settings: Flow<AppSettings> =
            dataSource.settings.map { settings ->
                AppSettings(
                    sharedLinkAutoDownload = settings.sharedLinkAutoDownload,
                    sharedLinkQuality =
                        SharedLinkQuality.entries.find { it.name == settings.sharedLinkQuality }
                            ?: SharedLinkQuality.HIGHEST,
                    sharedLinkResolution = settings.sharedLinkResolution.coerceAtLeast(1),
                    themeMode = ThemeMode.entries.find { it.name == settings.themeMode } ?: ThemeMode.SYSTEM,
                    clipboardPromptShown = settings.clipboardPromptShown,
                    clipboardSuggestionEnabled = settings.clipboardSuggestionEnabled,
                    wifiOnlyDownloads = settings.wifiOnlyDownloads,
                    useConfiguredDownloadLocation = settings.useConfiguredDownloadLocation,
                    downloadTreeUri = settings.downloadTreeUri,
                    playbackSpeed = settings.playbackSpeed.takeIf { it in SUPPORTED_PLAYBACK_SPEEDS } ?: 1f,
                )
            }

        override suspend fun setPlaybackSpeed(speed: Float) {
            require(speed in SUPPORTED_PLAYBACK_SPEEDS)
            dataSource.setPlaybackSpeed(speed)
        }

        override suspend fun setThemeMode(mode: ThemeMode) = dataSource.setThemeMode(mode.name)

        override suspend fun setClipboardSuggestion(
            promptShown: Boolean,
            enabled: Boolean,
        ) = dataSource.setClipboardSuggestion(promptShown, enabled)

        override suspend fun setSharedLinkAutoDownload(enabled: Boolean) = dataSource.setSharedLinkAutoDownload(enabled)

        override suspend fun setSharedLinkQuality(
            quality: SharedLinkQuality,
            resolution: Int,
        ) = dataSource.setSharedLinkQuality(quality.name, resolution.coerceAtLeast(1))

        override suspend fun setWifiOnlyDownloads(enabled: Boolean) = dataSource.setWifiOnlyDownloads(enabled)

        override suspend fun setDownloadLocation(
            enabled: Boolean,
            treeUri: String?,
        ) = dataSource.setDownloadLocation(enabled, treeUri)
    }
