package com.comst19.dambom.core.domain.model

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}
