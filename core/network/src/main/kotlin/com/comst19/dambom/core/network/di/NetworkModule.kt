package com.comst19.dambom.core.network.di

import com.comst19.dambom.core.network.NetworkConfig
import com.comst19.dambom.core.network.header.DefaultNetworkHeaderProvider
import com.comst19.dambom.core.network.header.NetworkHeaderProvider
import com.comst19.dambom.core.network.interceptor.HeaderInterceptor
import com.comst19.dambom.core.network.interceptor.RequestIdInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideNetworkHeaderProvider(provider: DefaultNetworkHeaderProvider): NetworkHeaderProvider = provider

    @Provides
    @Singleton
    fun provideJson(): Json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        headerInterceptor: HeaderInterceptor,
        requestIdInterceptor: RequestIdInterceptor,
        networkConfig: NetworkConfig,
    ): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(requestIdInterceptor)
            .addInterceptor(headerInterceptor)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = networkConfig.logLevel
                },
            ).connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

    private const val CONNECT_TIMEOUT_SECONDS = 10L
    private const val READ_TIMEOUT_SECONDS = 20L
}
