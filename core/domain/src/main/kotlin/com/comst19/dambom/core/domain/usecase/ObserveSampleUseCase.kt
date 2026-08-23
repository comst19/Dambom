package com.comst19.dambom.core.domain.usecase

import com.comst19.dambom.core.domain.model.Sample
import com.comst19.dambom.core.domain.repository.SampleRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveSampleUseCase
    @Inject
    constructor(
        private val repository: SampleRepository,
    ) {
        operator fun invoke(id: Long): Flow<Sample?> = repository.observeSample(id)
    }
