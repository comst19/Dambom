package com.comst19.dambom.feature.settings.contract

internal enum class AppLanguage(
    val languageTag: String,
) {
    SYSTEM(""),
    KOREAN("ko"),
    ENGLISH("en"),
    ;

    companion object {
        fun from(languageTags: String): AppLanguage =
            entries.firstOrNull { it.languageTag.isNotEmpty() && languageTags.startsWith(it.languageTag) } ?: SYSTEM
    }
}
