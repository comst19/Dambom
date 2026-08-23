package com.comst19.dambom.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SampleDao {
    @Query("SELECT * FROM samples ORDER BY id")
    fun observeAll(): Flow<List<SampleEntity>>

    @Query("SELECT * FROM samples WHERE id = :id")
    fun observeById(id: Long): Flow<SampleEntity?>

    @Upsert
    suspend fun upsertAll(samples: List<SampleEntity>)

    @Query("DELETE FROM samples")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(samples: List<SampleEntity>) {
        deleteAll()
        upsertAll(samples)
    }
}
