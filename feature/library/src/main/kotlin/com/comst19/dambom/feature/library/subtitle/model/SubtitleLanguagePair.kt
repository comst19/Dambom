package com.comst19.dambom.feature.library.subtitle.model

import androidx.compose.runtime.Immutable
import com.comst19.dambom.core.common.locale.AppLanguage
import java.util.Locale

@Immutable
internal data class SubtitleLanguagePair(
    val source: String,
    val target: String,
) {
    val name: String get() = "$source>$target"

    companion object {
        val ENGLISH_KOREAN = SubtitleLanguagePair("en-US", "ko")
        val JAPANESE_KOREAN = SubtitleLanguagePair("ja-JP", "ko")
        val JAPANESE_ENGLISH = SubtitleLanguagePair("ja-JP", "en")

        val sourceLanguages =
            listOf(
                "en-US",
                "ja-JP",
                "ko-KR",
                "cmn-Hans-CN",
                "cmn-Hant-TW",
                "de-DE",
                "es-ES",
                "fr-FR",
                "hi-IN",
                "it-IT",
                "pl-PL",
                "pt-BR",
                "ru-RU",
                "tr-TR",
                "vi-VN",
            )
        val targetLanguages =
            AppLanguage.entries.filter { it != AppLanguage.SYSTEM }.map {
                listOfNotNull(it.languageTag, it.supportedScript).joinToString("-")
            } + "ja"

        fun parse(value: String?): SubtitleLanguagePair? =
            when (value) {
                "ENGLISH_KOREAN" -> {
                    ENGLISH_KOREAN
                }

                "JAPANESE_KOREAN" -> {
                    JAPANESE_KOREAN
                }

                "JAPANESE_ENGLISH" -> {
                    JAPANESE_ENGLISH
                }

                else -> {
                    val parts = value?.split('>')
                    if (parts?.size == 2 && parts[0] in sourceLanguages && parts[1] in targetLanguages) {
                        SubtitleLanguagePair(parts[0], parts[1])
                    } else {
                        null
                    }
                }
            }

        fun defaultTarget(locale: Locale): String =
            targetLanguages.firstOrNull { Locale.forLanguageTag(it).language == locale.language } ?: "en"
    }
}
