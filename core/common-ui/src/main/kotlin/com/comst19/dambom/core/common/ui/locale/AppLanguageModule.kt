package com.comst19.dambom.core.common.ui.locale

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppLanguageModule {
    @Binds
    @Singleton
    abstract fun bindAppLanguagePreferences(implementation: DefaultAppLanguagePreferences): AppLanguagePreferences
}
