package com.comst19.dambom.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [SampleEntity::class],
    version = 2,
    exportSchema = true,
)
internal abstract class AppDatabase : RoomDatabase() {
    abstract fun sampleDao(): SampleDao
}
