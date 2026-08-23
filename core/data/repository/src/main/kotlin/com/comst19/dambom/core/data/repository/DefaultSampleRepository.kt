package com.comst19.dambom.core.data.repository

import com.comst19.dambom.core.data.remote.datasource.SampleRemoteDataSource
import com.comst19.dambom.core.database.SampleDao
import com.comst19.dambom.core.domain.model.Sample
import com.comst19.dambom.core.domain.repository.SampleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DefaultSampleRepository
    @Inject
    constructor(
        private val remote: SampleRemoteDataSource,
        private val sampleDao: SampleDao,
    ) : SampleRepository {
        override fun observeSamples(): Flow<List<Sample>> =
            sampleDao
                .observeAll()
                .map { entities -> entities.map { it.toDomain() } }

        override fun observeSample(id: Long): Flow<Sample?> =
            sampleDao
                .observeById(id)
                .map { entity -> entity?.toDomain() }

        override suspend fun refreshSamples() =
            withRemoteErrorMapping {
                val now = System.currentTimeMillis()
                sampleDao.replaceAll(remote.fetchSamples().map { it.toEntity(now) })
            }
    }
