package com.comst19.dambom.core.domain.model

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val clipboardPromptShown: Boolean = false,
    val clipboardSuggestionEnabled: Boolean = false,
)

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}
