package com.comst19.dambom.core.domain.model

data class AppSettings(
    val sharedLinkAutoDownload: Boolean = false,
    val sharedLinkQuality: SharedLinkQuality = SharedLinkQuality.HIGHEST,
    val sharedLinkResolution: Int = 1080,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val clipboardPromptShown: Boolean = false,
    val clipboardSuggestionEnabled: Boolean = false,
    val wifiOnlyDownloads: Boolean = false,
    val useConfiguredDownloadLocation: Boolean = true,
    val downloadTreeUri: String? = null,
    val playbackSpeed: Float = 1f,
)

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class SharedLinkQuality {
    HIGHEST,
    LOWEST,
    PREFERRED_RESOLUTION,
}
