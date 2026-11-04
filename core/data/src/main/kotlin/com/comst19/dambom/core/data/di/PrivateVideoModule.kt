package com.comst19.dambom.core.data.di

import com.comst19.dambom.core.domain.model.PrivateVideoSession
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PrivateVideoModule {
    @Provides
    @Singleton
    fun providePrivateVideoSession(): PrivateVideoSession = PrivateVideoSession()
}
