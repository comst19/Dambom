package com.comst19.dambom.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesSettingsDataSource
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : SettingsDataSource {
        override val themeMode: Flow<String> =
            dataStore.data
                .catch { throwable ->
                    if (throwable is IOException) {
                        emit(emptyPreferences())
                    } else {
                        throw throwable
                    }
                }.map { preferences ->
                    preferences[THEME_MODE] ?: SYSTEM_THEME_MODE
                }

        override suspend fun setThemeMode(value: String) {
            dataStore.edit { preferences -> preferences[THEME_MODE] = value }
        }

        private companion object {
            val THEME_MODE = stringPreferencesKey("theme_mode")
            const val SYSTEM_THEME_MODE = "SYSTEM"
        }
    }

@Module
@InstallIn(SingletonComponent::class)
abstract class DataStoreModule {
    @Binds
    abstract fun bindSettingsDataSource(implementation: PreferencesSettingsDataSource): SettingsDataSource
}

@Module
@InstallIn(SingletonComponent::class)
object DataStoreProviderModule {
    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        androidx.datastore.preferences.core.PreferenceDataStoreFactory.create {
            context.filesDir.resolve("datastore/settings.preferences_pb")
        }
}
