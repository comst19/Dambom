package com.comst19.dambom.core.datastore

import kotlinx.coroutines.flow.Flow

interface SettingsDataSource {
    val themeMode: Flow<String>

    suspend fun setThemeMode(value: String)
}
