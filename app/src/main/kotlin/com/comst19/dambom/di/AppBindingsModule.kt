package com.comst19.dambom.di

import com.comst19.dambom.core.analytics.AnalyticsHelper
import com.comst19.dambom.core.analytics.NoOpAnalyticsHelper
import com.comst19.dambom.core.common.ui.player.MediaPreviewDataSourceFactory
import com.comst19.dambom.core.data.media.OkHttpMediaPreviewDataSourceFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindingsModule {
    @Binds
    @Singleton
    abstract fun bindAnalyticsHelper(implementation: NoOpAnalyticsHelper): AnalyticsHelper

    companion object {
        @Provides
        @Singleton
        fun provideMediaPreviewFactory(factory: OkHttpMediaPreviewDataSourceFactory): MediaPreviewDataSourceFactory =
            MediaPreviewDataSourceFactory(factory::create)
    }
}
