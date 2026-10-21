package com.comst19.dambom.core.database.di

import android.content.Context
import androidx.room.Room
import com.comst19.dambom.core.database.DambomDatabase
import com.comst19.dambom.core.database.download.DownloadTaskDao
import com.comst19.dambom.core.database.playback.PlaybackHistoryDao
import com.comst19.dambom.core.database.tag.VideoTagDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): DambomDatabase =
        Room
            .databaseBuilder(context, DambomDatabase::class.java, DATABASE_NAME)
            .addMigrations(
                DambomDatabase.MIGRATION_1_2,
                DambomDatabase.MIGRATION_2_3,
                DambomDatabase.MIGRATION_3_4,
                DambomDatabase.MIGRATION_4_5,
                DambomDatabase.MIGRATION_5_6,
                DambomDatabase.MIGRATION_6_7,
            ).build()

    @Provides
    fun provideDownloadTaskDao(database: DambomDatabase): DownloadTaskDao = database.downloadTaskDao()

    @Provides
    fun providePlaybackHistoryDao(database: DambomDatabase): PlaybackHistoryDao = database.playbackHistoryDao()

    @Provides
    fun provideVideoTagDao(database: DambomDatabase): VideoTagDao = database.videoTagDao()
}

private const val DATABASE_NAME = "dambom.db"
