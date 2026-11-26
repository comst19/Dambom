package com.comst19.dambom.core.common.ui.locale

import android.app.LocaleManager
import android.content.Context
import android.icu.util.ULocale
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.LocaleManagerCompat
import androidx.core.os.LocaleListCompat
import com.comst19.dambom.core.common.locale.AppLocalePolicy
import com.comst19.dambom.core.domain.model.AppLanguagePreference
import com.comst19.dambom.core.domain.repository.AppLanguagePreferenceStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

interface AppLanguagePreferences {
    suspend fun selectedLanguageTags(): String

    suspend fun apply(languageTag: String)

    suspend fun syncSystemLanguage()
}

@Singleton
class DefaultAppLanguagePreferences internal constructor(
    private val context: Context,
    private val store: AppLanguagePreferenceStore,
    private val mainDispatcher: CoroutineDispatcher,
) : AppLanguagePreferences {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        store: AppLanguagePreferenceStore,
    ) : this(
        context = context,
        store = store,
        mainDispatcher = Dispatchers.Main.immediate,
    )

    private val mutex = Mutex()

    override suspend fun selectedLanguageTags(): String =
        mutex.withLock {
            val stored = store.read()
            val currentTags = readApplicationLanguageTags()
            selectedLanguageTags(stored, currentTags)
        }

    override suspend fun apply(languageTag: String) {
        mutex.withLock {
            withContext(NonCancellable) {
                if (languageTag.isEmpty()) {
                    applySystemLanguageLocked(store.read())
                } else {
                    store.clearAutomatic()
                    setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
                }
            }
        }
    }

    override suspend fun syncSystemLanguage() {
        mutex.withLock {
            val stored = store.read()
            val currentTags = readApplicationLanguageTags()
            if (selectedLanguageTags(stored, currentTags).isEmpty()) {
                withContext(NonCancellable) {
                    applySystemLanguageLocked(stored)
                }
            } else if (stored.followSystem) {
                withContext(NonCancellable) {
                    store.clearAutomatic()
                }
            }
        }
    }

    private suspend fun applySystemLanguageLocked(stored: AppLanguagePreference) {
        val languageTag = resolveSystemLanguageTag()
        val automaticSelectionChanged =
            !stored.followSystem || stored.automaticTag != languageTag
        if (automaticSelectionChanged) {
            store.setAutomatic(languageTag)
        }
        val locales = LocaleListCompat.forLanguageTags(languageTag)
        withContext(mainDispatcher) {
            if (readApplicationLocalesOnMain() != locales) {
                writeApplicationLocalesOnMain(locales)
            }
        }
    }

    private fun resolveSystemLanguageTag(): String {
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
        return AppLocalePolicy.resolve(listOfNotNull(primaryLocale))
    }

    private suspend fun readApplicationLanguageTags(): String =
        withContext(mainDispatcher) {
            readApplicationLocalesOnMain().toLanguageTags()
        }

    private suspend fun setApplicationLocales(locales: LocaleListCompat) {
        withContext(mainDispatcher) {
            writeApplicationLocalesOnMain(locales)
        }
    }

    private fun readApplicationLocalesOnMain(): LocaleListCompat {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            if (localeManager != null) {
                return LocaleListCompat.wrap(localeManager.applicationLocales)
            }
        }
        return AppCompatDelegate.getApplicationLocales()
    }

    private fun writeApplicationLocalesOnMain(locales: LocaleListCompat) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            if (localeManager != null) {
                localeManager.applicationLocales = LocaleList.forLanguageTags(locales.toLanguageTags())
                return
            }
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    private fun selectedLanguageTags(
        stored: AppLanguagePreference,
        currentTags: String,
    ): String {
        val automatic = stored.followSystem && stored.automaticTag == currentTags
        return if (automatic) "" else currentTags
    }
}
