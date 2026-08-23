package com.comst19.dambom.core.domain.usecase

import com.comst19.dambom.core.domain.repository.SampleRepository
import javax.inject.Inject

class RefreshSamplesUseCase
    @Inject
    constructor(
        private val repository: SampleRepository,
    ) {
        suspend operator fun invoke() = repository.refreshSamples()
    }
