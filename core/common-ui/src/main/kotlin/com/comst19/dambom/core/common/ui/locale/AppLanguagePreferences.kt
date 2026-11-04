package com.comst19.dambom.core.common.ui.locale

import android.content.Context
import android.icu.util.ULocale
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.LocaleManagerCompat
import androidx.core.os.LocaleListCompat
import com.comst19.dambom.core.common.locale.AppLocalePolicy
import java.util.Locale

object AppLanguagePreferences {
    fun selectedLanguageTags(context: Context): String {
        val currentTags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val automatic =
            preferences.getBoolean(FOLLOW_SYSTEM, false) &&
                preferences.getString(AUTOMATIC_TAG, null) == currentTags
        return if (automatic) "" else currentTags
    }

    fun apply(
        context: Context,
        languageTag: String,
    ) {
        if (languageTag.isEmpty()) {
            applySystemLanguage(context)
            return
        }
        context
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(FOLLOW_SYSTEM, false)
            .remove(AUTOMATIC_TAG)
            .apply()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
    }

    fun syncSystemLanguage(context: Context) {
        if (selectedLanguageTags(context).isEmpty()) {
            applySystemLanguage(context)
        } else {
            val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            if (preferences.getBoolean(FOLLOW_SYSTEM, false)) {
                preferences
                    .edit()
                    .putBoolean(FOLLOW_SYSTEM, false)
                    .remove(AUTOMATIC_TAG)
                    .apply()
            }
        }
    }

    private fun applySystemLanguage(context: Context) {
        val systemLocales = LocaleManagerCompat.getSystemLocales(context)
        val primaryLocale =
            systemLocales[0]?.let { locale ->
                if (locale.script.isNotEmpty()) {
                    locale
                } else {
                    val script = ULocale.addLikelySubtags(ULocale.forLocale(locale)).script
                    Locale
                        .Builder()
                        .setLocale(locale)
                        .setScript(script)
                        .build()
                }
            }
        val languageTag = AppLocalePolicy.resolve(listOfNotNull(primaryLocale))
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val automaticSelectionChanged =
            !preferences.getBoolean(FOLLOW_SYSTEM, false) ||
                preferences.getString(AUTOMATIC_TAG, null) != languageTag
        if (automaticSelectionChanged) {
            preferences
                .edit()
                .putBoolean(FOLLOW_SYSTEM, true)
                .putString(AUTOMATIC_TAG, languageTag)
                .apply()
        }
        val locales = LocaleListCompat.forLanguageTags(languageTag)
        if (AppCompatDelegate.getApplicationLocales() != locales) {
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    private const val PREFERENCES_NAME = "app_language"
    private const val FOLLOW_SYSTEM = "follow_system"
    private const val AUTOMATIC_TAG = "automatic_tag"
}
