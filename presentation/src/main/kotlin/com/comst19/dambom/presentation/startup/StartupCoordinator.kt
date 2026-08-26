package com.comst19.dambom.presentation.startup

import com.comst19.dambom.core.navigation.TopLevelNavKey
import com.comst19.dambom.core.navigation.contract.HomeGraph.HomeKey
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
