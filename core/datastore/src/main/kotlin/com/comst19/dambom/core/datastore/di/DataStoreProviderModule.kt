package com.comst19.dambom.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.comst19.dambom.core.datastore.AppLanguageDataSource
import com.comst19.dambom.core.datastore.PreferencesAppLanguageDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataStoreProviderModule {
    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = createSettingsDataStore(context)
}

fun createSettingsDataStore(
    context: Context,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    produceFile: () -> File = { context.filesDir.resolve("datastore/settings.preferences_pb") },
): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        migrations =
            listOf(
                SharedPreferencesMigration(
                    context = context,
                    sharedPreferencesName = PreferencesAppLanguageDataSource.LEGACY_PREFERENCES_NAME,
                    keysToMigrate =
                        setOf(
                            PreferencesAppLanguageDataSource.FOLLOW_SYSTEM_KEY_NAME,
                            PreferencesAppLanguageDataSource.AUTOMATIC_TAG_KEY_NAME,
                        ),
                ),
            ),
        scope = scope,
        produceFile = produceFile,
    )

fun createAppLanguageDataSource(
    context: Context,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    produceFile: () -> File = { context.filesDir.resolve("datastore/settings.preferences_pb") },
): AppLanguageDataSource =
    PreferencesAppLanguageDataSource(
        createSettingsDataStore(
            context = context,
            scope = scope,
            produceFile = produceFile,
        ),
    )
