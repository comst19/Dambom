package com.comst19.dambom.feature.detection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comst19.dambom.core.common.ui.AppScreen
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.core.designsystem.FormFactorPreviews
import com.comst19.dambom.core.domain.model.MediaCandidate
import com.comst19.dambom.core.domain.model.UnsupportedReason

@Composable
internal fun DetectionRoute(
    url: String,
    viewModel: DetectionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(url) { viewModel.detect(url) }
    DetectionScreen(
        uiState = uiState,
        onBack = viewModel::goBack,
        onRetry = viewModel::retry,
        onToggleCandidate = viewModel::toggleCandidate,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun DetectionScreen(
    uiState: DetectionUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onToggleCandidate: (String) -> Unit,
) {
    AppScreen(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detection_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.detection_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            when (uiState) {
                DetectionUiState.Loading -> LoadingContent()
                is DetectionUiState.Content -> DetectionContent(uiState, onToggleCandidate)
                is DetectionUiState.Unsupported -> UnsupportedContent(uiState.reason, onRetry)
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.detection_loading), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.detection_loading_description),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DetectionContent(
    state: DetectionUiState.Content,
    onToggleCandidate: (String) -> Unit,
) {
    LazyColumn(
        modifier =
            Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Spacer(Modifier.height(12.dp))
            Text(state.pageTitle, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.detection_found_count, state.candidates.size),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
        }
        items(state.candidates, key = MediaCandidate::id) { candidate ->
            CandidateItem(
                candidate = candidate,
                selected = candidate.id in state.selectedIds,
                onClick = { onToggleCandidate(candidate.id) },
            )
        }
        item {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {},
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                enabled = false,
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    stringResource(
                        R.string.detection_selected_count,
                        state.selectedIds.size,
                    ),
                )
            }
            Text(
                text = stringResource(R.string.detection_download_next),
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CandidateItem(
    candidate: MediaCandidate,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
            ),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(selected, onCheckedChange = { onClick() })
            Column(Modifier.weight(1f)) {
                Text(
                    candidate.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    candidate.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                candidate.contentLength?.let {
                    Text(
                        it.formatBytes(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun UnsupportedContent(
    reason: UnsupportedReason,
    onRetry: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text(stringResource(R.string.detection_not_found), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(10.dp))
        Text(
            reason.description(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.detection_retry_hint),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRetry) { Text(stringResource(R.string.detection_retry)) }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun UnsupportedReason.description(): String =
    stringResource(
        when (this) {
            UnsupportedReason.INVALID_URL -> R.string.detection_invalid_url
            UnsupportedReason.ACCESS_RESTRICTED -> R.string.detection_access_restricted
            UnsupportedReason.NO_MEDIA -> R.string.detection_no_media
            UnsupportedReason.NETWORK_ERROR -> R.string.detection_network_error
            UnsupportedReason.UNSUPPORTED_FORMAT -> R.string.detection_unsupported_format
        },
    )

private fun Long.formatBytes(): String {
    val megabytes = this / (1024.0 * 1024.0)
    return "%.1f MB".format(megabytes)
}

@Preview
@FormFactorPreviews
@Composable
private fun DetectionScreenPreview() {
    DambomTheme {
        DetectionScreen(
            uiState =
                DetectionUiState.Content(
                    pageTitle = "여행 기록",
                    candidates =
                        listOf(
                            MediaCandidate(
                                id = "1",
                                url = "https://example.com/trip.mp4",
                                title = "trip",
                                mimeType = "video/mp4",
                                contentLength = 24_000_000,
                            ),
                        ),
                    selectedIds = setOf("1"),
                ),
            onBack = {},
            onRetry = {},
            onToggleCandidate = {},
        )
    }
}
