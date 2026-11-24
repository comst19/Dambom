package com.comst19.dambom.feature.library.tag.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.feature.library.tag.contract.LibraryTagState
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

@Composable
internal fun rememberTagFilteredVideos(
    videos: PersistentList<DownloadTask>,
    tags: LibraryTagState,
    filter: suspend (PersistentList<DownloadTask>, LibraryTagState) -> PersistentList<DownloadTask>,
): PersistentList<DownloadTask> =
    if (tags.selectedTagIds.isEmpty()) {
        videos
    } else {
        key(videos, tags.selectedTagIds, tags.matchedVideoIds) {
            produceState(persistentListOf<DownloadTask>()) {
                value = filter(videos, tags)
            }
        }.value
    }
