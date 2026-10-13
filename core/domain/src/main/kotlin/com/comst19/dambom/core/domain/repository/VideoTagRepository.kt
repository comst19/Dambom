package com.comst19.dambom.core.domain.repository

import com.comst19.dambom.core.domain.model.VideoTag
import com.comst19.dambom.core.domain.model.VideoTagAssignment
import kotlinx.coroutines.flow.Flow

interface VideoTagRepository {
    val tags: Flow<List<VideoTag>>
    val assignments: Flow<List<VideoTagAssignment>>

    suspend fun create(name: String)

    suspend fun rename(
        id: String,
        name: String,
    )

    suspend fun delete(id: String)

    suspend fun assign(
        videoIds: Set<String>,
        tagId: String,
        assigned: Boolean,
    )
}
