package com.comst19.dambom.core.data.media

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.comst19.dambom.core.network.di.MediaPreviewHttpClient
import com.comst19.dambom.core.network.header.mediaRequestClient
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OkHttpMediaPreviewDataSourceFactory
    @Inject
    constructor(
        @MediaPreviewHttpClient private val baseClient: OkHttpClient,
    ) {
        @OptIn(UnstableApi::class)
        fun create(
            mediaUrl: String,
            requestHeaders: Map<String, String>,
        ): DataSource.Factory {
            val client = mediaRequestClient(baseClient, mediaUrl, requestHeaders)
            return OkHttpDataSource.Factory(client)
        }
    }
