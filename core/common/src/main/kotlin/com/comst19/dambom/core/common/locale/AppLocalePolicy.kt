package com.comst19.dambom.core.common.locale

import java.util.Locale

object AppLocalePolicy {
    fun resolve(systemLocales: List<Locale>): String {
        val primaryLocale = systemLocales.firstOrNull() ?: return "en"
        val language = AppLanguage.from(primaryLocale.toLanguageTag())
        val supportedScript = supportedScripts[language.languageTag]
        val scriptMatches = supportedScript == null || primaryLocale.script.isEmpty() || primaryLocale.script == supportedScript
        if (language != AppLanguage.SYSTEM && scriptMatches) return language.languageTag
        return countryDefaults[primaryLocale.country] ?: "en"
    }

    private val supportedScripts =
        mapOf(
            "pa" to "Guru",
            "sd" to "Arab",
            "az" to "Latn",
            "uz" to "Latn",
            "ha" to "Latn",
            "ku" to "Latn",
        )

    private val countryDefaults =
        mapOf(
            "PK" to "ur",
            "IN" to "hi",
            "NG" to "en",
            "BR" to "pt",
            "FR" to "fr",
            "KR" to "ko",
            "BD" to "bn",
            "MA" to "ar",
            "AF" to "fa",
            "BJ" to "fr",
            "ET" to "am",
            "GN" to "fr",
            "MR" to "ar",
            "TZ" to "sw",
            "AE" to "ar",
            "CD" to "fr",
            "DE" to "de",
            "DZ" to "ar",
            "EG" to "ar",
            "ID" to "id",
            "IL" to "he",
            "IQ" to "ar",
            "IR" to "fa",
            "LB" to "ar",
            "MM" to "my",
            "MZ" to "pt",
            "SA" to "ar",
            "TN" to "ar",
            "YE" to "ar",
            "YT" to "fr",
            "ZA" to "en",
            "ZM" to "en",
        )
}
