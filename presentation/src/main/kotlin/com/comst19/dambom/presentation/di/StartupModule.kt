package com.comst19.dambom.presentation.di

import com.comst19.dambom.presentation.startup.DefaultStartupCoordinator
import com.comst19.dambom.presentation.startup.StartupCoordinator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class StartupModule {
    @Binds
    abstract fun bindStartupCoordinator(implementation: DefaultStartupCoordinator): StartupCoordinator
}
