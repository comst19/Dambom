package com.comst19.dambom.feature.detection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comst19.dambom.core.common.net.hasVideoFileExtension
import com.comst19.dambom.core.common.ui.AppEvent
import com.comst19.dambom.core.common.ui.AppEventBus
import com.comst19.dambom.core.common.ui.UiText
import com.comst19.dambom.core.common.util.suspendRunCatching
import com.comst19.dambom.core.domain.model.DownloadRequest
import com.comst19.dambom.core.domain.model.EnqueueDownloadsResult
import com.comst19.dambom.core.domain.model.MediaDetectionResult
import com.comst19.dambom.core.domain.model.MediaVariant
import com.comst19.dambom.core.domain.model.UnsupportedReason
import com.comst19.dambom.core.domain.repository.DownloadRepository
import com.comst19.dambom.core.domain.repository.MediaDetectionRepository
import com.comst19.dambom.core.domain.repository.MediaDetectionSnapshots
import com.comst19.dambom.core.domain.repository.SettingsRepository
import com.comst19.dambom.core.navigation.NavigationDispatcher
import com.comst19.dambom.core.navigation.NavigationEvent
import com.comst19.dambom.core.navigation.contract.HomeGraph.DownloadsKey
import com.comst19.dambom.core.navigation.contract.HomeGraph.WebKey
import com.comst19.dambom.core.network.header.mediaHeadersForUrl
import com.comst19.dambom.feature.detection.contract.DetectionUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
internal class DetectionViewModel
    @Inject
    constructor(
        private val repository: MediaDetectionRepository,
        private val settingsRepository: SettingsRepository,
        private val downloadRepository: DownloadRepository,
        private val navigation: NavigationDispatcher,
        private val appEventBus: AppEventBus,
        private val snapshots: MediaDetectionSnapshots = MediaDetectionSnapshots(),
    ) : ViewModel() {
        private val mutableUiState = MutableStateFlow<DetectionUiState>(DetectionUiState.Loading)
        val uiState: StateFlow<DetectionUiState> = mutableUiState.asStateFlow()
        private var loadedUrl: String? = null
        private var detectionJob: Job? = null
        private var requestGeneration = 0L
        private var loadedSharedLink = false

        fun detect(
            url: String,
            snapshotId: String? = null,
            sharedLink: Boolean = false,
        ) {
            val hasActiveResult =
                mutableUiState.value is DetectionUiState.Loading || mutableUiState.value is DetectionUiState.Content
            if (loadedUrl == url && loadedSharedLink == sharedLink && hasActiveResult) {
                return
            }
            val generation = ++requestGeneration
            detectionJob?.cancel()
            loadedUrl = url
            loadedSharedLink = sharedLink
            mutableUiState.value = DetectionUiState.Loading
            detectionJob =
                viewModelScope.launch {
                    suspendRunCatching { snapshotId?.let { snapshots.get(it, url) } ?: repository.detect(url) }
                        .onSuccess { result ->
                            if (generation == requestGeneration) applyDetectionResult(url, result, generation)
                        }.onFailure {
                            if (generation == requestGeneration) {
                                appEventBus.send(AppEvent.ShowSnackbar(UiText.Resource(R.string.detection_failed)))
                                navigation.dispatch(NavigationEvent.Replace(WebKey(url, sharedLink = loadedSharedLink)))
                            }
                        }
                }
        }

        fun retry() {
            val url = loadedUrl ?: return
            loadedUrl = null
            detect(url, sharedLink = loadedSharedLink)
        }

        fun openInWeb() {
            val url = loadedUrl ?: return
            viewModelScope.launch {
                navigation.dispatch(NavigationEvent.Replace(WebKey(url, sharedLink = loadedSharedLink)))
            }
        }

        fun setNetworkUnavailable() {
            requestGeneration++
            detectionJob?.cancel()
            if (mutableUiState.value !is DetectionUiState.Content) {
                mutableUiState.value = DetectionUiState.NetworkUnavailable
            }
        }

        private suspend fun applyDetectionResult(
            url: String,
            result: MediaDetectionResult,
            generation: Long,
        ) {
            when (result) {
                is MediaDetectionResult.Success -> {
                    val settings = settingsRepository.settings.first()
                    if (generation != requestGeneration) return
                    val autoDownload = loadedSharedLink && settings.sharedLinkAutoDownload
                    mutableUiState.value =
                        DetectionUiState.Content(
                            pageTitle = result.pageTitle,
                            automaticQuality = autoDownload,
                            automaticDownloadPending = autoDownload && result.candidates.size == 1,
                            candidates = result.candidates.toPersistentList(),
                            selectedIds =
                                result.candidates
                                    .takeIf { it.size == 1 }
                                    .orEmpty()
                                    .map { it.id }
                                    .toPersistentSet(),
                            selectedVariantUrls =
                                result.candidates
                                    .associate { candidate ->
                                        candidate.id to
                                            if (autoDownload) {
                                                candidate.preferredSharedVariant(settings).selectionId
                                            } else {
                                                candidate.url
                                            }
                                    }.toPersistentMap(),
                        )
                }

                is MediaDetectionResult.Unsupported -> {
                    if (result.reason == UnsupportedReason.INVALID_URL) {
                        mutableUiState.value = DetectionUiState.Unsupported(result.reason)
                    } else {
                        appEventBus.send(AppEvent.ShowSnackbar(UiText.Resource(result.reason.messageRes())))
                        navigation.dispatch(NavigationEvent.Replace(WebKey(url, sharedLink = loadedSharedLink)))
                    }
                }
            }
        }

        fun toggleCandidate(id: String) {
            mutableUiState.update { state ->
                if (state !is DetectionUiState.Content) return@update state
                val selectedIds =
                    state.selectedIds.mutate { ids ->
                        if (!ids.add(id)) ids.remove(id)
                    }
                state.copy(selectedIds = selectedIds, enqueueFailed = false)
            }
        }

        fun selectVariant(
            candidateId: String,
            url: String,
        ) {
            mutableUiState.update { state ->
                if (state !is DetectionUiState.Content) return@update state
                val candidate = state.candidates.firstOrNull { it.id == candidateId } ?: return@update state
                if (candidate.downloadVariants.none { it.selectionId == url }) return@update state
                state.copy(selectedVariantUrls = state.selectedVariantUrls.putting(candidateId, url))
            }
        }

        fun consumeAutomaticDownload() {
            mutableUiState.update { state ->
                if (state is DetectionUiState.Content) state.copy(automaticDownloadPending = false) else state
            }
        }

        fun downloadSelected(fallbackTitle: String) {
            val state = mutableUiState.value as? DetectionUiState.Content
            val sourcePageUrl = loadedUrl
            if (state == null || sourcePageUrl == null) return
            if (state.isSubmitting || state.selectedIds.isEmpty()) return
            mutableUiState.value = state.copy(isSubmitting = true)
            viewModelScope.launch {
                suspendRunCatching {
                    val requests =
                        state.candidates
                            .filter { it.id in state.selectedIds }
                            .map { candidate ->
                                val selectedUrl = state.selectedVariantUrls[candidate.id]
                                val selected =
                                    candidate.downloadVariants.firstOrNull { it.selectionId == selectedUrl }
                                        ?: candidate.downloadVariants.first()
                                val headers =
                                    mediaHeadersForUrl(
                                        selected.requestHeaders.ifEmpty { candidate.requestHeaders },
                                        if (selected.requestHeaders.isEmpty()) candidate.url else selected.url,
                                        selected.url,
                                    )
                                val variant = repository.resolveDownloadVariant(selected.copy(requestHeaders = headers))
                                DownloadRequest(
                                    id = UUID.randomUUID().toString(),
                                    url = variant.url,
                                    sourcePageUrl = sourcePageUrl,
                                    title =
                                        candidate.displayTitle(
                                            fallbackTitle,
                                            state.candidates.indexOf(candidate),
                                            state.candidates.size,
                                        ),
                                    mimeType = variant.mimeType,
                                    expectedBytes = variant.contentLength,
                                    quality = variant.quality,
                                    videoHeight = variant.videoHeight,
                                    requestHeaders = variant.requestHeaders,
                                )
                            }
                    downloadRepository.enqueue(requests)
                }.onSuccess { result ->
                    appEventBus.send(AppEvent.ShowSnackbar(result.feedbackMessage))
                    navigation.dispatch(NavigationEvent.Replace(DownloadsKey))
                }.onFailure {
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

private suspend fun MediaDetectionRepository.resolveDownloadVariant(variant: MediaVariant): MediaVariant {
    if (variant.mimeType != null || variant.url.hasVideoFileExtension()) return variant
    val result = detectRequest(variant.url, variant.requestHeaders) as? MediaDetectionResult.Success
    val resolved =
        result
            ?.candidates
            ?.firstOrNull()
            ?.downloadVariants
            ?.firstOrNull()
    val headers =
        resolved?.let {
            it.requestHeaders.ifEmpty { mediaHeadersForUrl(variant.requestHeaders, variant.url, it.url) }
        }
    return resolved?.copy(
        quality = variant.quality,
        videoHeight = variant.videoHeight ?: resolved.videoHeight,
        requestHeaders = headers.orEmpty(),
    ) ?: variant
}

private val EnqueueDownloadsResult.feedbackMessage: UiText
    get() =
        when {
            addedCount > 0 && duplicateCount > 0 -> {
                UiText.Resource(
                    R.string.detection_enqueue_added_with_duplicates,
                    listOf(addedCount, duplicateCount),
                )
            }

            addedCount > 0 -> {
                UiText.Resource(R.string.detection_enqueue_added, listOf(addedCount))
            }

            else -> {
                UiText.Resource(R.string.detection_enqueue_duplicates)
            }
        }
