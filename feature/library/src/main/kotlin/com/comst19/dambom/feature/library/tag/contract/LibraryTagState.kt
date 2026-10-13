package com.comst19.dambom.feature.library.tag.contract

import androidx.compose.runtime.Immutable
import com.comst19.dambom.core.domain.model.VideoTag
import com.comst19.dambom.core.domain.model.VideoTagAssignment
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentSet

@Immutable
internal data class LibraryTagState(
    val tags: PersistentList<VideoTag> = persistentListOf(),
    val videoIdsByTag: PersistentMap<String, PersistentSet<String>> = persistentMapOf(),
    val selectedTagIds: PersistentSet<String> = persistentSetOf(),
    val matchedVideoIds: PersistentSet<String> = persistentSetOf(),
) {
    fun matches(videoId: String): Boolean = selectedTagIds.isEmpty() || videoId in matchedVideoIds

    fun isAssignedToAll(
        tagId: String,
        videoIds: Set<String>,
    ): Boolean {
        val assignedIds = videoIdsByTag[tagId].orEmpty()
        return videoIds.all { it in assignedIds }
    }
}

internal fun indexTagAssignments(assignments: List<VideoTagAssignment>): PersistentMap<String, PersistentSet<String>> {
    val index = mutableMapOf<String, MutableSet<String>>()
    assignments.forEach { assignment ->
        index.getOrPut(assignment.tagId) { mutableSetOf() }.add(assignment.videoId)
    }
    return index.mapValues { (_, ids) -> ids.toPersistentSet() }.toPersistentMap()
}

internal fun toLibraryTagState(
    tags: List<VideoTag>,
    index: PersistentMap<String, PersistentSet<String>>,
    selected: Collection<String>,
): LibraryTagState {
    val tagIds = tags.mapTo(mutableSetOf()) { it.id }
    val selectedIds = selected.filter { it in tagIds }.toPersistentSet()
    val matchedIds = persistentSetOf<String>().builder()
    selectedIds.forEach { matchedIds.addAll(index[it].orEmpty()) }
    return LibraryTagState(tags.toPersistentList(), index, selectedIds, matchedIds.build())
}
