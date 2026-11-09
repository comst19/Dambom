package com.comst19.dambom.core.common.locale

import java.util.Locale

enum class AppLanguage(
    val languageTag: String,
    val supportedScript: String? = null,
) {
    SYSTEM(""),
    KOREAN("ko"),
    ENGLISH("en"),
    CHINESE_SIMPLIFIED("zh-Hans"),
    CHINESE_TRADITIONAL("zh-Hant"),
    JAPANESE("ja"),
    SPANISH("es"),
    URDU("ur"),
    HINDI("hi"),
    FRENCH("fr"),
    PORTUGUESE("pt"),
    BENGALI("bn"),
    ARABIC("ar"),
    PASHTO("ps"),
    AMHARIC("am"),
    SWAHILI("sw"),
    GERMAN("de"),
    INDONESIAN("id"),
    HEBREW("he"),
    PERSIAN("fa"),
    BURMESE("my"),
    PUNJABI("pa", "Guru"),
    SINDHI("sd", "Arab"),
    TAMIL("ta"),
    TELUGU("te"),
    MARATHI("mr"),
    GUJARATI("gu"),
    KANNADA("kn"),
    MALAYALAM("ml"),
    ODIA("or"),
    ASSAMESE("as"),
    HAUSA("ha", "Latn"),
    YORUBA("yo"),
    IGBO("ig"),
    TIGRINYA("ti"),
    SOMALI("so"),
    LINGALA("ln"),
    UZBEK("uz", "Latn"),
    TURKMEN("tk"),
    AZERBAIJANI("az", "Latn"),
    KURDISH("ku", "Latn"),
    CENTRAL_KURDISH("ckb"),
    AFRIKAANS("af"),
    ZULU("zu"),
    XHOSA("xh"),
    SOUTHERN_SOTHO("st"),
    RUSSIAN("ru"),
    ;

    companion object {
        fun from(languageTags: String): AppLanguage {
            val locale = Locale.forLanguageTag(languageTags.substringBefore(','))
            if (locale.language == "zh") {
                return when {
                    locale.script == "Hant" -> CHINESE_TRADITIONAL
                    locale.script == "Hans" -> CHINESE_SIMPLIFIED
                    locale.script.isNotEmpty() -> SYSTEM
                    locale.country == "TW" || locale.country == "HK" || locale.country == "MO" -> CHINESE_TRADITIONAL
                    else -> CHINESE_SIMPLIFIED
                }
            }
            val language = locale.language
            val normalizedLanguage =
                when (language) {
                    "in" -> "id"
                    "iw" -> "he"
                    else -> language
                }
            return entries.firstOrNull { it.languageTag == normalizedLanguage } ?: SYSTEM
        }
    }
}
