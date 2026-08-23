package com.comst19.dambom.core.domain.repository

import com.comst19.dambom.core.domain.model.Sample
import kotlinx.coroutines.flow.Flow

interface SampleRepository {
    fun observeSamples(): Flow<List<Sample>>

    fun observeSample(id: Long): Flow<Sample?>

    suspend fun refreshSamples()
}
