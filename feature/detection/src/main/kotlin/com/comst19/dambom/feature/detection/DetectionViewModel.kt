package com.comst19.dambom.feature.detection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comst19.dambom.core.common.suspendRunCatching
import com.comst19.dambom.core.domain.model.DownloadRequest
import com.comst19.dambom.core.domain.model.MediaCandidate
import com.comst19.dambom.core.domain.model.MediaDetectionResult
import com.comst19.dambom.core.domain.model.UnsupportedReason
import com.comst19.dambom.core.domain.repository.DownloadRepository
import com.comst19.dambom.core.domain.repository.MediaDetectionRepository
import com.comst19.dambom.core.navigation.NavigationDispatcher
import com.comst19.dambom.core.navigation.NavigationEvent
import com.comst19.dambom.core.navigation.contract.HomeGraph.DownloadsKey
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
        private val downloadRepository: DownloadRepository,
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

        fun setNetworkUnavailable() {
            if (mutableUiState.value !is DetectionUiState.Content) {
                mutableUiState.value = DetectionUiState.NetworkUnavailable
            }
        }

        fun toggleCandidate(id: String) {
            mutableUiState.update { state ->
                if (state !is DetectionUiState.Content) return@update state
                val selectedIds = state.selectedIds.toMutableSet()
                if (!selectedIds.add(id)) selectedIds.remove(id)
                state.copy(selectedIds = selectedIds, enqueueFailed = false)
            }
        }

        fun downloadSelected() {
            val state = mutableUiState.value as? DetectionUiState.Content ?: return
            val sourcePageUrl = loadedUrl ?: return
            if (state.isSubmitting || state.selectedIds.isEmpty()) return
            mutableUiState.value = state.copy(isSubmitting = true)
            viewModelScope.launch {
                val requests =
                    state.candidates
                        .filter { it.id in state.selectedIds }
                        .map { candidate ->
                            DownloadRequest(
                                id = candidate.id,
                                url = candidate.url,
                                sourcePageUrl = sourcePageUrl,
                                title = candidate.title,
                                mimeType = candidate.mimeType,
                                expectedBytes = candidate.contentLength,
                            )
                        }
                suspendRunCatching { downloadRepository.enqueue(requests) }
                    .onSuccess { navigation.dispatch(NavigationEvent.Replace(DownloadsKey)) }
                    .onFailure {
                        mutableUiState.update { current ->
                            if (current is DetectionUiState.Content) {
                                current.copy(isSubmitting = false, enqueueFailed = true)
                            } else {
                                current
                            }
                        }
                    }
            }
        }

        fun goBack() {
            viewModelScope.launch { navigation.dispatch(NavigationEvent.Back) }
        }
    }

internal sealed interface DetectionUiState {
    data object Loading : DetectionUiState

    data object NetworkUnavailable : DetectionUiState

    data class Content(
        val pageTitle: String,
        val candidates: List<MediaCandidate>,
        val selectedIds: Set<String>,
        val isSubmitting: Boolean = false,
        val enqueueFailed: Boolean = false,
    ) : DetectionUiState

    data class Unsupported(
        val reason: UnsupportedReason,
    ) : DetectionUiState
}
