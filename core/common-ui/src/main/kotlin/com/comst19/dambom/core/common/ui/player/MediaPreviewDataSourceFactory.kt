package com.comst19.dambom.core.common.ui.player

import androidx.media3.datasource.DataSource

fun interface MediaPreviewDataSourceFactory {
    fun create(
        mediaUrl: String,
        requestHeaders: Map<String, String>,
    ): DataSource.Factory
}
