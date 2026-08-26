package com.comst19.dambom.core.datastore.di

import com.comst19.dambom.core.datastore.PreferencesSettingsDataSource
import com.comst19.dambom.core.datastore.SettingsDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DataStoreModule {
    @Binds
    abstract fun bindSettingsDataSource(implementation: PreferencesSettingsDataSource): SettingsDataSource
}
