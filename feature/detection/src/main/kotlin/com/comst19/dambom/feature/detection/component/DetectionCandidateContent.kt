package com.comst19.dambom.feature.detection.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comst19.dambom.core.common.ui.PreloadVideoThumbnails
import com.comst19.dambom.core.designsystem.DambomButton
import com.comst19.dambom.core.domain.model.MediaCandidate
import com.comst19.dambom.core.domain.model.NetworkAccessState
import com.comst19.dambom.core.domain.model.NetworkRestriction
import com.comst19.dambom.feature.detection.R
import com.comst19.dambom.feature.detection.contract.DetectionUiState

@Composable
internal fun DetectionCandidateContent(
    state: DetectionUiState.Content,
    networkAccess: NetworkAccessState,
    onToggleCandidate: (String) -> Unit,
    onSelectVariant: (String, String) -> Unit,
    onDownload: () -> Unit,
) {
    var previewCandidate by remember { mutableStateOf<MediaCandidate?>(null) }
    var pendingDownloadQualityIds by remember { mutableStateOf(emptyList<String>()) }
    val gridState = rememberLazyGridState()
    val preloadUrls by
        remember(gridState, state.candidates) {
            derivedStateOf {
                val lastVisibleItemIndex =
                    gridState.layoutInfo.visibleItemsInfo
                        .lastOrNull()
                        ?.index
                if (lastVisibleItemIndex == null) {
                    emptyList()
                } else {
                    state.candidates
                        .drop(lastVisibleItemIndex.coerceIn(0, state.candidates.size))
                        .take(PRELOAD_CANDIDATE_COUNT)
                        .mapNotNull(MediaCandidate::thumbnailUrl)
                }
            }
        }
    PreloadVideoThumbnails(preloadUrls)
    Column(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(MIN_CANDIDATE_WIDTH),
            modifier = Modifier.weight(1f),
            state = gridState,
            contentPadding = PaddingValues(start = SCREEN_HORIZONTAL_PADDING, end = SCREEN_HORIZONTAL_PADDING, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    modifier = Modifier.padding(bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = state.pageTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(R.string.detection_found_count, state.candidates.size),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (state.candidates.size > 1) {
                        Text(
                            stringResource(R.string.detection_choose_videos),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            itemsIndexed(
                items = state.candidates,
                key = { _, candidate -> candidate.id },
                contentType = { _, _ -> CANDIDATE_CONTENT_TYPE },
            ) { index, candidate ->
                val selectedVariant =
                    candidate.downloadVariants.firstOrNull {
                        it.url == state.selectedVariantUrls[candidate.id]
                    } ?: candidate.downloadVariants.first()
                val previewTitle = candidate.displayTitle(index + 1)
                DetectionCandidateItem(
                    candidate = candidate,
                    selectedVariant = selectedVariant,
                    index = index + 1,
                    selected = candidate.id in state.selectedIds,
                    onClick = { onToggleCandidate(candidate.id) },
                    onSelectVariant = { onSelectVariant(candidate.id, it) },
                    onPreview = {
                        previewCandidate =
                            candidate.copy(
                                url = selectedVariant.url,
                                title = previewTitle,
                            )
                    },
                )
            }
        }
        DetectionActions(
            state = state,
            networkAccess = networkAccess,
            onDownload = {
                pendingDownloadQualityIds =
                    state.candidates
                        .filter { it.id in state.selectedIds && it.downloadVariants.size > 1 }
                        .map(MediaCandidate::id)
                if (pendingDownloadQualityIds.isEmpty()) {
                    onDownload()
                }
            },
        )
    }
    previewCandidate?.let { candidate ->
        CandidatePreviewDialog(candidate = candidate, onDismiss = { previewCandidate = null })
    }
    val pendingDownloadQuality =
        pendingDownloadQualityIds.firstOrNull()?.let { id -> state.candidates.firstOrNull { it.id == id } }
    pendingDownloadQuality?.let { candidate ->
        val selectedVariant =
            candidate.downloadVariants.firstOrNull {
                it.url == state.selectedVariantUrls[candidate.id]
            } ?: candidate.downloadVariants.first()
        DetectionQualitySheet(
            candidate = candidate,
            selectedVariant = selectedVariant,
            title = candidate.displayTitle(state.candidates.indexOf(candidate) + 1),
            onDismiss = { pendingDownloadQualityIds = emptyList() },
            onSelect = { variant ->
                onSelectVariant(candidate.id, variant.url)
                pendingDownloadQualityIds = pendingDownloadQualityIds.drop(1)
                if (pendingDownloadQualityIds.isEmpty()) onDownload()
            },
        )
    }
}

@Composable
private fun DetectionActions(
    state: DetectionUiState.Content,
    networkAccess: NetworkAccessState,
    onDownload: () -> Unit,
) {
    Column(Modifier.padding(horizontal = SCREEN_HORIZONTAL_PADDING)) {
        Spacer(Modifier.height(8.dp))
        DambomButton(
            text =
                stringResource(
                    if (state.isSubmitting) R.string.detection_adding_to_queue else R.string.detection_download_selected,
                    state.selectedIds.size,
                ),
            onClick = onDownload,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = state.selectedIds.isNotEmpty() && !state.isSubmitting && networkAccess.canDownload,
        )
        if (state.enqueueFailed) {
            Text(
                text = stringResource(R.string.detection_enqueue_failed),
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        networkAccess.restriction?.let { restriction ->
            Text(
                text =
                    stringResource(
                        if (restriction == NetworkRestriction.OFFLINE) {
                            R.string.detection_download_offline
                        } else {
                            R.string.detection_download_wifi_required
                        },
                    ),
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

private val MIN_CANDIDATE_WIDTH = 320.dp
private val SCREEN_HORIZONTAL_PADDING = 16.dp
private const val CANDIDATE_CONTENT_TYPE = "candidate"
private const val PRELOAD_CANDIDATE_COUNT = 2
