package com.comst19.dambom.feature.library.di

import androidx.media3.common.util.UnstableApi
import com.comst19.dambom.feature.library.trim.export.ClipExporter
import com.comst19.dambom.feature.library.trim.export.VideoClipExporter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
@androidx.annotation.OptIn(UnstableApi::class)
internal interface ClipExportModule {
    @Binds
    fun bindClipExporter(implementation: VideoClipExporter): ClipExporter
}
