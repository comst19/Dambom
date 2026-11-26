package com.comst19.dambom.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesAppLanguageDataSource
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : AppLanguageDataSource {
        override suspend fun read(): StoredAppLanguage {
            val values = dataStore.data.first()
            return StoredAppLanguage(
                followSystem = values[FOLLOW_SYSTEM] ?: false,
                automaticTag = values[AUTOMATIC_TAG],
            )
        }

        override suspend fun setAutomatic(languageTag: String) {
            dataStore.edit { preferences ->
                preferences[FOLLOW_SYSTEM] = true
                preferences[AUTOMATIC_TAG] = languageTag
            }
        }

        override suspend fun clearAutomatic() {
            dataStore.edit { preferences ->
                preferences[FOLLOW_SYSTEM] = false
                preferences.remove(AUTOMATIC_TAG)
            }
        }

        internal companion object {
            const val LEGACY_PREFERENCES_NAME = "app_language"
            const val FOLLOW_SYSTEM_KEY_NAME = "follow_system"
            const val AUTOMATIC_TAG_KEY_NAME = "automatic_tag"
            val FOLLOW_SYSTEM = booleanPreferencesKey(FOLLOW_SYSTEM_KEY_NAME)
            val AUTOMATIC_TAG = stringPreferencesKey(AUTOMATIC_TAG_KEY_NAME)
        }
    }
