package com.comst19.dambom.presentation

import com.comst19.dambom.core.navigation.TopLevelNavKey
import com.comst19.dambom.core.navigation.contract.HomeGraph.HomeKey
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

interface StartupCoordinator {
    suspend fun initialize(): TopLevelNavKey
}

@Singleton
class DefaultStartupCoordinator
    @Inject
    constructor() : StartupCoordinator {
        override suspend fun initialize(): TopLevelNavKey = HomeKey
    }

@Module
@InstallIn(SingletonComponent::class)
abstract class StartupModule {
    @Binds
    abstract fun bindStartupCoordinator(implementation: DefaultStartupCoordinator): StartupCoordinator
}
