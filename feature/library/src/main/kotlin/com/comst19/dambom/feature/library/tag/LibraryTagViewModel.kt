package com.comst19.dambom.feature.library.tag

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comst19.dambom.core.common.ui.AppEvent
import com.comst19.dambom.core.common.ui.AppEventBus
import com.comst19.dambom.core.common.ui.UiText
import com.comst19.dambom.core.common.util.suspendRunCatching
import com.comst19.dambom.core.domain.repository.VideoTagRepository
import com.comst19.dambom.feature.library.R
import com.comst19.dambom.feature.library.tag.contract.LibraryTagState
import com.comst19.dambom.feature.library.tag.contract.indexTagAssignments
import com.comst19.dambom.feature.library.tag.contract.toLibraryTagState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class LibraryTagViewModel
    @Inject
    constructor(
        private val repository: VideoTagRepository,
        private val savedStateHandle: SavedStateHandle,
        private val appEventBus: AppEventBus,
    ) : ViewModel() {
        private val selected = savedStateHandle.getStateFlow(TAG_FILTER_KEY, arrayListOf<String>())
        val state =
            combine(
                repository.tags,
                repository.assignments.map(::indexTagAssignments),
                selected,
            ) { tags, index, selected ->
                toLibraryTagState(tags, index, selected)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(TAG_STOP_TIMEOUT_MILLIS), LibraryTagState())

        fun toggleFilter(id: String) {
            val ids = selected.value.toMutableSet()
            if (!ids.add(id)) ids.remove(id)
            savedStateHandle[TAG_FILTER_KEY] = ArrayList(ids)
        }

        fun create(
            name: String,
            onComplete: (Boolean) -> Unit,
        ) = mutate(onComplete) { repository.create(name) }

        fun rename(
            id: String,
            name: String,
            onComplete: (Boolean) -> Unit,
        ) = mutate(onComplete) { repository.rename(id, name) }

        fun delete(id: String) = mutate { repository.delete(id) }

        fun assign(
            videoIds: Set<String>,
            tagId: String,
            assigned: Boolean,
        ) = mutate { repository.assign(videoIds, tagId, assigned) }

        private fun mutate(
            onComplete: (Boolean) -> Unit = {},
            action: suspend () -> Unit,
        ) {
            viewModelScope.launch {
                val result = suspendRunCatching { action() }
                onComplete(result.isSuccess)
                result.onFailure {
                    appEventBus.send(AppEvent.ShowSnackbar(UiText.Resource(R.string.library_tag_failure)))
                }
            }
        }
    }

private const val TAG_FILTER_KEY = "library-tag-filter"
private const val TAG_STOP_TIMEOUT_MILLIS = 5_000L
