package com.comst19.dambom.feature.detection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comst19.dambom.core.domain.model.MediaCandidate
import com.comst19.dambom.core.domain.model.MediaDetectionResult
import com.comst19.dambom.core.domain.model.UnsupportedReason
import com.comst19.dambom.core.domain.repository.MediaDetectionRepository
import com.comst19.dambom.core.navigation.NavigationDispatcher
import com.comst19.dambom.core.navigation.NavigationEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class DetectionViewModel
    @Inject
    constructor(
        private val repository: MediaDetectionRepository,
        private val navigation: NavigationDispatcher,
    ) : ViewModel() {
        private val mutableUiState = MutableStateFlow<DetectionUiState>(DetectionUiState.Loading)
        val uiState: StateFlow<DetectionUiState> = mutableUiState.asStateFlow()
        private var loadedUrl: String? = null

        fun detect(url: String) {
            if (loadedUrl == url && mutableUiState.value !is DetectionUiState.Unsupported) return
            loadedUrl = url
            mutableUiState.value = DetectionUiState.Loading
            viewModelScope.launch {
                mutableUiState.value =
                    when (val result = repository.detect(url)) {
                        is MediaDetectionResult.Success -> {
                            DetectionUiState.Content(
                                pageTitle = result.pageTitle,
                                candidates = result.candidates,
                                selectedIds = result.candidates.mapTo(mutableSetOf()) { it.id },
                            )
                        }

                        is MediaDetectionResult.Unsupported -> {
                            DetectionUiState.Unsupported(result.reason)
                        }
                    }
            }
        }

        fun retry() {
            val url = loadedUrl ?: return
            loadedUrl = null
            detect(url)
        }

        fun toggleCandidate(id: String) {
            mutableUiState.update { state ->
                if (state !is DetectionUiState.Content) return@update state
                val selectedIds = state.selectedIds.toMutableSet()
                if (!selectedIds.add(id)) selectedIds.remove(id)
                state.copy(selectedIds = selectedIds)
            }
        }

        fun goBack() {
            viewModelScope.launch { navigation.dispatch(NavigationEvent.Back) }
        }
    }

internal sealed interface DetectionUiState {
    data object Loading : DetectionUiState

    data class Content(
        val pageTitle: String,
        val candidates: List<MediaCandidate>,
        val selectedIds: Set<String>,
    ) : DetectionUiState

    data class Unsupported(
        val reason: UnsupportedReason,
    ) : DetectionUiState
}
