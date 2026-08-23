package com.comst19.dambom.core.domain.repository

import com.comst19.dambom.core.domain.model.MediaDetectionResult

interface MediaDetectionRepository {
    suspend fun detect(url: String): MediaDetectionResult
}
