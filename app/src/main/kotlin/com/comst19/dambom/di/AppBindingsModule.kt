package com.comst19.dambom.di

import com.comst19.dambom.core.analytics.AnalyticsHelper
import com.comst19.dambom.core.analytics.NoOpAnalyticsHelper
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindingsModule {
    @Binds
    @Singleton
    abstract fun bindAnalyticsHelper(implementation: NoOpAnalyticsHelper): AnalyticsHelper
}
