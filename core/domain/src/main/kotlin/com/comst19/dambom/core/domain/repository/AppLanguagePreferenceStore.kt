package com.comst19.dambom.core.domain.repository

import com.comst19.dambom.core.domain.model.AppLanguagePreference

interface AppLanguagePreferenceStore {
    suspend fun read(): AppLanguagePreference

    suspend fun setAutomatic(languageTag: String)

    suspend fun clearAutomatic()
}
