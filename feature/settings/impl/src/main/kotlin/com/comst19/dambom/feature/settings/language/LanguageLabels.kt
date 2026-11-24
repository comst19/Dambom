package com.comst19.dambom.feature.settings.language

import com.comst19.dambom.core.common.locale.AppLanguage
import com.comst19.dambom.feature.settings.R

internal val AppLanguage.labelRes: Int
    get() =
        when (this) {
            AppLanguage.SYSTEM -> R.string.language_system
            AppLanguage.KOREAN -> R.string.language_korean
            AppLanguage.ENGLISH -> R.string.language_english
            AppLanguage.CHINESE_SIMPLIFIED -> R.string.language_chinese_simplified
            AppLanguage.CHINESE_TRADITIONAL -> R.string.language_chinese_traditional
            AppLanguage.JAPANESE -> R.string.language_japanese
            AppLanguage.SPANISH -> R.string.language_spanish
            AppLanguage.URDU -> R.string.language_urdu
            AppLanguage.HINDI -> R.string.language_hindi
            AppLanguage.FRENCH -> R.string.language_french
            AppLanguage.PORTUGUESE -> R.string.language_portuguese
            AppLanguage.BENGALI -> R.string.language_bengali
            AppLanguage.ARABIC -> R.string.language_arabic
            AppLanguage.PASHTO -> R.string.language_pashto
            AppLanguage.AMHARIC -> R.string.language_amharic
            AppLanguage.SWAHILI -> R.string.language_swahili
            AppLanguage.GERMAN -> R.string.language_german
            AppLanguage.INDONESIAN -> R.string.language_indonesian
            AppLanguage.HEBREW -> R.string.language_hebrew
            AppLanguage.PERSIAN -> R.string.language_persian
            AppLanguage.BURMESE -> R.string.language_burmese
            AppLanguage.PUNJABI -> R.string.language_punjabi
            AppLanguage.SINDHI -> R.string.language_sindhi
            AppLanguage.TAMIL -> R.string.language_tamil
            AppLanguage.TELUGU -> R.string.language_telugu
            AppLanguage.MARATHI -> R.string.language_marathi
            AppLanguage.GUJARATI -> R.string.language_gujarati
            AppLanguage.KANNADA -> R.string.language_kannada
            AppLanguage.MALAYALAM -> R.string.language_malayalam
            AppLanguage.ODIA -> R.string.language_odia
            AppLanguage.ASSAMESE -> R.string.language_assamese
            AppLanguage.HAUSA -> R.string.language_hausa
            AppLanguage.YORUBA -> R.string.language_yoruba
            AppLanguage.IGBO -> R.string.language_igbo
            AppLanguage.TIGRINYA -> R.string.language_tigrinya
            AppLanguage.SOMALI -> R.string.language_somali
            AppLanguage.LINGALA -> R.string.language_lingala
            AppLanguage.UZBEK -> R.string.language_uzbek
            AppLanguage.TURKMEN -> R.string.language_turkmen
            AppLanguage.AZERBAIJANI -> R.string.language_azerbaijani
            AppLanguage.KURDISH -> R.string.language_kurdish
            AppLanguage.CENTRAL_KURDISH -> R.string.language_central_kurdish
            AppLanguage.AFRIKAANS -> R.string.language_afrikaans
            AppLanguage.ZULU -> R.string.language_zulu
            AppLanguage.XHOSA -> R.string.language_xhosa
            AppLanguage.SOUTHERN_SOTHO -> R.string.language_southern_sotho
            AppLanguage.RUSSIAN -> R.string.language_russian
        }
