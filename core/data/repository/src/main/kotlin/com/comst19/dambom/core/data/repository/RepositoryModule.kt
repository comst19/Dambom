package com.comst19.dambom.core.data.repository

import com.comst19.dambom.core.domain.repository.DownloadRepository
import com.comst19.dambom.core.domain.repository.MediaDetectionRepository
import com.comst19.dambom.core.domain.repository.NetworkMonitor
import com.comst19.dambom.core.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindDownloadRepository(implementation: DefaultDownloadRepository): DownloadRepository

    @Binds
    @Singleton
    internal abstract fun bindMediaDetection(implementation: DefaultMediaDetectionRepository): MediaDetectionRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(implementation: DefaultSettingsRepository): SettingsRepository

    @Binds
    @Singleton
    internal abstract fun bindNetworkMonitor(implementation: ConnectivityNetworkMonitor): NetworkMonitor

    @Binds
    @Singleton
    internal abstract fun bindDownloadWorkScheduler(implementation: WorkManagerDownloadScheduler): DownloadWorkScheduler
}
