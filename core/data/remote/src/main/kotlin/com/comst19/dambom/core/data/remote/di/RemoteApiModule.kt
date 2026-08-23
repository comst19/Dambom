package com.comst19.dambom.core.data.remote.di

import com.comst19.dambom.core.data.remote.api.SampleApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object RemoteApiModule {
    @Provides
    fun provideSampleApi(retrofit: Retrofit): SampleApi = retrofit.create(SampleApi::class.java)
}
