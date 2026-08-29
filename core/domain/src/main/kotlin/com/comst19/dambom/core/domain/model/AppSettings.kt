package com.comst19.dambom.core.domain.model

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val clipboardPromptShown: Boolean = false,
    val clipboardSuggestionEnabled: Boolean = false,
    val wifiOnlyDownloads: Boolean = false,
    val useConfiguredDownloadLocation: Boolean = true,
    val downloadTreeUri: String? = null,
)

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}
