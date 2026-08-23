package com.comst19.dambom.core.data.remote.di

import com.comst19.dambom.core.data.remote.datasource.SampleRemoteDataSource
import com.comst19.dambom.core.data.remote.datasource.SeedSampleRemoteDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object DebugRemoteModule {
    @Provides
    fun provideSampleRemoteDataSource(): SampleRemoteDataSource = SeedSampleRemoteDataSource()
}
