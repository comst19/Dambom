package com.comst19.dambom.di

import com.comst19.dambom.BuildConfig
import com.comst19.dambom.core.common.model.AppEnvironment
import com.comst19.dambom.core.network.NetworkConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppConfigModule {
    @Provides
    @Singleton
    fun provideAppEnvironment(): AppEnvironment = AppEnvironment.from(BuildConfig.APP_ENVIRONMENT)

    @Provides
    @Singleton
    fun provideNetworkConfig(appEnvironment: AppEnvironment): NetworkConfig =
        NetworkConfig.from(
            baseUrl = BuildConfig.API_BASE_URL,
            appEnvironment = appEnvironment,
        )
}
