package com.comst19.dambom.core.domain.repository

import com.comst19.dambom.core.domain.model.MediaDetectionResult
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaDetectionSnapshots
    @Inject
    constructor() {
        private val snapshots = LinkedHashMap<String, Pair<String, MediaDetectionResult.Success>>()

        @Synchronized
        fun save(
            url: String,
            result: MediaDetectionResult.Success,
        ): String {
            val id = UUID.randomUUID().toString()
            snapshots[id] = url to result.copy(candidates = result.candidates.toList())
            while (snapshots.size > MAX_SNAPSHOTS) snapshots.remove(snapshots.keys.first())
            return id
        }

        @Synchronized
        fun get(
            id: String,
            url: String,
        ): MediaDetectionResult.Success? = snapshots[id]?.takeIf { it.first == url }?.second
    }

private const val MAX_SNAPSHOTS = 12
