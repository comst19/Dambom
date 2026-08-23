package com.comst19.dambom.core.domain.usecase

import com.comst19.dambom.core.domain.model.Sample
import com.comst19.dambom.core.domain.repository.SampleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ObserveSamplesUseCase
    @Inject
    constructor(
        private val repository: SampleRepository,
    ) {
        operator fun invoke(): Flow<List<Sample>> =
            repository
                .observeSamples()
                .map { samples -> samples.filter { it.title.isNotBlank() }.sortedBy(Sample::id) }
    }
