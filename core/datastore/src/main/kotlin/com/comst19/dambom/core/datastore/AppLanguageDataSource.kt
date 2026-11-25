package com.comst19.dambom.core.datastore

interface AppLanguageDataSource {
    suspend fun read(): StoredAppLanguage

    suspend fun setAutomatic(languageTag: String)

    suspend fun clearAutomatic()
}

data class StoredAppLanguage(
    val followSystem: Boolean = false,
    val automaticTag: String? = null,
)
