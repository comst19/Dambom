package com.comst19.dambom.feature.library.subtitle.di

import com.comst19.dambom.feature.library.subtitle.inference.GemmaSubtitleEngine
import com.comst19.dambom.feature.library.subtitle.inference.SubtitleEngine
import com.comst19.dambom.feature.library.subtitle.work.SubtitleJobs
import com.comst19.dambom.feature.library.subtitle.work.WorkManagerSubtitleJobs
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface SubtitleModule {
    @Binds fun bindSubtitleJobs(implementation: WorkManagerSubtitleJobs): SubtitleJobs

    @Binds fun bindSubtitleEngine(implementation: GemmaSubtitleEngine): SubtitleEngine
}
