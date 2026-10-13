package com.comst19.dambom.core.data.repository

import com.comst19.dambom.core.database.tag.VideoTagDao
import com.comst19.dambom.core.database.tag.VideoTagEntity
import com.comst19.dambom.core.domain.model.VideoTag
import com.comst19.dambom.core.domain.model.VideoTagAssignment
import com.comst19.dambom.core.domain.repository.VideoTagRepository
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

class DefaultVideoTagRepository
    @Inject
    constructor(
        private val dao: VideoTagDao,
    ) : VideoTagRepository {
        override val tags = dao.observeTags().map { tags -> tags.map { VideoTag(it.id, it.name) } }
        override val assignments =
            dao.observeAssignments().map { rows -> rows.map { VideoTagAssignment(it.videoId, it.tagId) } }

        override suspend fun create(name: String) {
            val trimmed = name.trim()
            require(trimmed.isNotEmpty())
            dao.insert(VideoTagEntity(UUID.randomUUID().toString(), trimmed))
        }

        override suspend fun rename(
            id: String,
            name: String,
        ) {
            val trimmed = name.trim()
            require(trimmed.isNotEmpty())
            dao.rename(id, trimmed)
        }

        override suspend fun delete(id: String) = dao.delete(id)

        override suspend fun assign(
            videoIds: Set<String>,
            tagId: String,
            assigned: Boolean,
        ) = dao.assign(videoIds, tagId, assigned)
    }
