package com.comst19.dambom.core.common.locale

enum class AppLanguage(
    val languageTag: String,
) {
    SYSTEM(""),
    KOREAN("ko"),
    ENGLISH("en"),
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
    PUNJABI("pa"),
    SINDHI("sd"),
    TAMIL("ta"),
    TELUGU("te"),
    MARATHI("mr"),
    GUJARATI("gu"),
    KANNADA("kn"),
    MALAYALAM("ml"),
    ODIA("or"),
    ASSAMESE("as"),
    HAUSA("ha"),
    YORUBA("yo"),
    IGBO("ig"),
    TIGRINYA("ti"),
    SOMALI("so"),
    LINGALA("ln"),
    UZBEK("uz"),
    TURKMEN("tk"),
    AZERBAIJANI("az"),
    KURDISH("ku"),
    CENTRAL_KURDISH("ckb"),
    AFRIKAANS("af"),
    ZULU("zu"),
    XHOSA("xh"),
    SOUTHERN_SOTHO("st"),
    RUSSIAN("ru"),
    ;

    companion object {
        fun from(languageTags: String): AppLanguage {
            val language = languageTags.substringBefore(',').substringBefore('-').lowercase()
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
