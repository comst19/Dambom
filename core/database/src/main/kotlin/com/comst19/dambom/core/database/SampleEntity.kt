package com.comst19.dambom.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "samples")
data class SampleEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val description: String,
    val syncedAtEpochMillis: Long = 0,
)
