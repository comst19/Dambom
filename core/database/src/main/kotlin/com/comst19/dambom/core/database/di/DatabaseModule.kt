package com.comst19.dambom.core.database.di

import android.content.Context
import androidx.room.Room
import com.comst19.dambom.core.database.DambomDatabase
import com.comst19.dambom.core.database.download.DownloadTaskDao
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
            .addMigrations(DambomDatabase.MIGRATION_1_2)
            .build()

    @Provides
    fun provideDownloadTaskDao(database: DambomDatabase): DownloadTaskDao = database.downloadTaskDao()
}

private const val DATABASE_NAME = "dambom.db"
