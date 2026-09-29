package com.comst19.dambom.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.comst19.dambom.core.database.download.DownloadTaskDao
import com.comst19.dambom.core.database.download.DownloadTaskEntity

@Database(
    entities = [DownloadTaskEntity::class],
    version = FAVORITES_DATABASE_VERSION,
    exportSchema = true,
)
abstract class DambomDatabase : RoomDatabase() {
    abstract fun downloadTaskDao(): DownloadTaskDao

    internal companion object {
        val MIGRATION_2_3 =
            object : Migration(2, FAVORITES_DATABASE_VERSION) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE download_tasks ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
                }
            }

        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE download_tasks ADD COLUMN retryCount INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE download_tasks ADD COLUMN deletePending INTEGER NOT NULL DEFAULT 0")
                }
            }
    }
}

private const val FAVORITES_DATABASE_VERSION = 3
