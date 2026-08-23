package com.comst19.dambom.core.data.repository

import com.comst19.dambom.core.datastore.SettingsDataSource
import com.comst19.dambom.core.domain.model.AppSettings
import com.comst19.dambom.core.domain.model.ThemeMode
import com.comst19.dambom.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DefaultSettingsRepository
    @Inject
    constructor(
        private val dataSource: SettingsDataSource,
    ) : SettingsRepository {
        override val settings: Flow<AppSettings> =
            dataSource.themeMode.map { value ->
                AppSettings(ThemeMode.entries.find { it.name == value } ?: ThemeMode.SYSTEM)
            }

        override suspend fun setThemeMode(mode: ThemeMode) = dataSource.setThemeMode(mode.name)
    }
