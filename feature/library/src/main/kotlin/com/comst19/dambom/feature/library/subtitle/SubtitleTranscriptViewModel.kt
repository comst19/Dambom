package com.comst19.dambom.feature.library.subtitle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comst19.dambom.core.common.util.suspendRunCatching
import com.comst19.dambom.core.domain.repository.DownloadRepository
import com.comst19.dambom.core.navigation.NavigationDispatcher
import com.comst19.dambom.core.navigation.NavigationEvent
import com.comst19.dambom.feature.library.subtitle.data.SubtitleFiles
import com.comst19.dambom.feature.library.subtitle.model.SubtitleTranscriptSection
import com.comst19.dambom.feature.library.subtitle.model.subtitleTranscriptSections
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class SubtitleTranscriptState(
    val title: String = "",
    val sections: ImmutableList<SubtitleTranscriptSection> = persistentListOf(),
    val cueCount: Int = 0,
    val loading: Boolean = true,
    val failed: Boolean = false,
)

@HiltViewModel
internal class SubtitleTranscriptViewModel
    @Inject
    constructor(
        private val repository: DownloadRepository,
        private val files: SubtitleFiles,
        private val navigation: NavigationDispatcher,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(SubtitleTranscriptState())
        val state = mutableState.asStateFlow()
        private var loadedId: String? = null

        fun load(id: String) {
            if (loadedId == id) return
            loadedId = id
            viewModelScope.launch {
                suspendRunCatching {
                    val task = checkNotNull(repository.observeDownload(id).first())
                    check(!task.deletePending)
                    val cues = files.read(checkNotNull(task.localFilePath))
                    mutableState.value =
                        SubtitleTranscriptState(
                            title = task.title,
                            sections = subtitleTranscriptSections(cues),
                            cueCount = cues.size,
                            loading = false,
                        )
                }.onFailure {
                    mutableState.value = SubtitleTranscriptState(loading = false, failed = true)
                }
            }
        }

        fun goBack() {
            viewModelScope.launch { navigation.dispatch(NavigationEvent.Back) }
        }
    }
