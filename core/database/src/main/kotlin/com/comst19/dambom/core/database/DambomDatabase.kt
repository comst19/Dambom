package com.comst19.dambom.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.comst19.dambom.core.database.download.DownloadTaskDao
import com.comst19.dambom.core.database.download.DownloadTaskEntity

@Database(
    entities = [DownloadTaskEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class DambomDatabase : RoomDatabase() {
    abstract fun downloadTaskDao(): DownloadTaskDao
}
