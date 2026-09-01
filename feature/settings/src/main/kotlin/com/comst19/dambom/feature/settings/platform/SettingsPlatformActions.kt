package com.comst19.dambom.feature.settings.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

internal interface SettingsPlatformActions {
    val currentLanguageTags: String
    val versionName: String

    fun applyLanguage(languageTag: String)

    fun persistDownloadDirectory(treeUri: String): Boolean
}

@Singleton
internal class AndroidSettingsPlatformActions
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SettingsPlatformActions {
        override val currentLanguageTags: String
            get() = AppCompatDelegate.getApplicationLocales().toLanguageTags()

        override val versionName: String =
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName
                .orEmpty()
                .ifBlank { "-" }

        override fun applyLanguage(languageTag: String) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
        }

        override fun persistDownloadDirectory(treeUri: String): Boolean =
            try {
                context.contentResolver.takePersistableUriPermission(
                    Uri.parse(treeUri),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                true
            } catch (_: SecurityException) {
                false
            }
    }

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SettingsPlatformModule {
    @Binds
    abstract fun bindSettingsPlatformActions(implementation: AndroidSettingsPlatformActions): SettingsPlatformActions
}
