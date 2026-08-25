package com.comst19.dambom.feature.detection

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comst19.dambom.core.common.ui.AppScreen
import com.comst19.dambom.core.common.ui.PreloadVideoThumbnails
import com.comst19.dambom.core.common.ui.VideoThumbnail
import com.comst19.dambom.core.designsystem.DambomShapes
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.core.designsystem.FormFactorPreviews
import com.comst19.dambom.core.domain.model.MediaCandidate
import com.comst19.dambom.core.domain.model.NetworkAccessState
import com.comst19.dambom.core.domain.model.NetworkConnection
import com.comst19.dambom.core.domain.model.NetworkRestriction
import com.comst19.dambom.core.domain.model.UnsupportedReason
import com.comst19.dambom.feature.detection.contract.DetectionUiState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

@Composable
internal fun DetectionRoute(
    url: String,
    networkAccess: NetworkAccessState,
    viewModel: DetectionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.downloadSelected()
        }
    LaunchedEffect(url, networkAccess.canUseInternet) {
        if (networkAccess.canUseInternet) viewModel.detect(url) else viewModel.setNetworkUnavailable()
    }
    DetectionScreen(
        uiState = uiState,
        networkAccess = networkAccess,
        onBack = viewModel::goBack,
        onRetry = viewModel::retry,
        onOpenWeb = viewModel::openInWeb,
        onToggleCandidate = viewModel::toggleCandidate,
        onDownload = {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                viewModel.downloadSelected()
            } else {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun DetectionScreen(
    uiState: DetectionUiState,
    networkAccess: NetworkAccessState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenWeb: () -> Unit,
    onToggleCandidate: (String) -> Unit,
    onDownload: () -> Unit,
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
                DetectionUiState.NetworkUnavailable -> NetworkUnavailableContent()
                is DetectionUiState.Content -> DetectionContent(uiState, networkAccess, onToggleCandidate, onDownload)
                is DetectionUiState.Unsupported -> UnsupportedContent(uiState.reason, onRetry, onOpenWeb)
            }
        }
    }
}

@Composable
private fun NetworkUnavailableContent() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.detection_network_unavailable), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.detection_network_unavailable_description),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
    networkAccess: NetworkAccessState,
    onToggleCandidate: (String) -> Unit,
    onDownload: () -> Unit,
) {
    var previewCandidate by remember { mutableStateOf<MediaCandidate?>(null) }
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
                        .map { it.thumbnailUrl ?: it.url }
                }
            }
        }
    PreloadVideoThumbnails(preloadUrls)
    Column(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(MIN_CANDIDATE_WIDTH),
            modifier = Modifier.weight(1f),
            state = gridState,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text(state.pageTitle, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.detection_found_count, state.candidates.size),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.candidates.size > 1) {
                        Text(
                            stringResource(R.string.detection_choose_videos),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
            itemsIndexed(
                items = state.candidates,
                key = { _, candidate -> candidate.id },
                contentType = { _, _ -> CANDIDATE_CONTENT_TYPE },
            ) { index, candidate ->
                CandidateItem(
                    candidate = candidate,
                    index = index + 1,
                    selected = candidate.id in state.selectedIds,
                    onClick = { onToggleCandidate(candidate.id) },
                    onPreview = { previewCandidate = candidate },
                )
            }
        }
        DetectionActions(state, networkAccess, onDownload)
    }
    previewCandidate?.let { candidate ->
        CandidatePreviewDialog(candidate = candidate, onDismiss = { previewCandidate = null })
    }
}

@Composable
private fun CandidateItem(
    candidate: MediaCandidate,
    index: Int,
    selected: Boolean,
    onClick: () -> Unit,
    onPreview: () -> Unit,
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
        shape = DambomShapes.Card,
    ) {
        Column {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(VIDEO_ASPECT_RATIO)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.VideoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                VideoThumbnail(
                    data = candidate.thumbnailUrl ?: candidate.url,
                    contentDescription = stringResource(R.string.detection_thumbnail, index),
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 12.dp, top = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(selected, onCheckedChange = { onClick() })
                Column(Modifier.weight(1f)) {
                    Text(
                        candidate.displayTitle(index),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        candidate.sourceLabel(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        candidate.quality,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
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
            TextButton(
                onClick = onPreview,
                modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
            ) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                Text(
                    stringResource(R.string.detection_preview),
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun DetectionActions(
    state: DetectionUiState.Content,
    networkAccess: NetworkAccessState,
    onDownload: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onDownload,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            enabled = state.selectedIds.isNotEmpty() && !state.isSubmitting && networkAccess.canDownload,
            shape = DambomShapes.Control,
        ) {
            Text(
                stringResource(
                    if (state.isSubmitting) R.string.detection_adding_to_queue else R.string.detection_download_selected,
                    state.selectedIds.size,
                ),
            )
        }
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

@Composable
private fun CandidatePreviewDialog(
    candidate: MediaCandidate,
    onDismiss: () -> Unit,
) {
    var videoAspectRatio by remember(candidate.id) { mutableFloatStateOf(VIDEO_ASPECT_RATIO) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            val maxVideoHeight = (maxHeight - PREVIEW_ACTIONS_HEIGHT).coerceAtLeast(1.dp)
            val videoWidth = minOf(maxWidth, maxVideoHeight * videoAspectRatio)
            val videoHeight = videoWidth / videoAspectRatio
            Surface(
                modifier = Modifier.width(videoWidth),
                color = Color.Black,
                shape = DambomShapes.Card,
            ) {
                Column {
                    Box(Modifier.fillMaxWidth().height(videoHeight)) {
                        AndroidView(
                            factory = { context ->
                                VideoView(context).apply {
                                    setVideoURI(Uri.parse(candidate.url))
                                    setOnPreparedListener { player ->
                                        if (player.videoWidth > 0 && player.videoHeight > 0) {
                                            videoAspectRatio = player.videoWidth.toFloat() / player.videoHeight
                                        }
                                        player.isLooping = true
                                        start()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize().background(Color.Black),
                            onRelease = VideoView::stopPlayback,
                        )
                        FilledIconButton(
                            onClick = onDismiss,
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                            colors =
                                IconButtonDefaults.filledIconButtonColors(
                                    containerColor = Color.Black.copy(alpha = 0.64f),
                                    contentColor = Color.White,
                                ),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.detection_close_preview),
                            )
                        }
                    }
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            candidate.title,
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaCandidate.displayTitle(index: Int): String =
    if (title.isBlank() || UUID_TITLE_REGEX.matches(title)) {
        stringResource(R.string.detection_video_number, index)
    } else {
        title
    }

private fun MediaCandidate.sourceLabel(): String = Uri.parse(url).host?.removePrefix("www.") ?: url

@Composable
private fun UnsupportedContent(
    reason: UnsupportedReason,
    onRetry: () -> Unit,
    onOpenWeb: () -> Unit,
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
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        if (reason.canContinueInWeb()) {
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.detection_open_web_hint),
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onOpenWeb,
                modifier = Modifier.fillMaxWidth(),
                shape = DambomShapes.Control,
            ) {
                Text(stringResource(R.string.detection_open_web))
            }
        }
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

private fun UnsupportedReason.canContinueInWeb(): Boolean =
    this == UnsupportedReason.NO_MEDIA || this == UnsupportedReason.UNSUPPORTED_FORMAT

private fun Long.formatBytes(): String {
    val megabytes = this / (1024.0 * 1024.0)
    return "%.1f MB".format(megabytes)
}

private val UUID_TITLE_REGEX = Regex("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
private val MIN_CANDIDATE_WIDTH = 320.dp
private const val VIDEO_ASPECT_RATIO = 16f / 9f
private val PREVIEW_ACTIONS_HEIGHT = 64.dp
private const val CANDIDATE_CONTENT_TYPE = "candidate"
private const val PRELOAD_CANDIDATE_COUNT = 2

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
                        persistentListOf(
                            MediaCandidate(
                                id = "1",
                                url = "https://example.com/trip.mp4",
                                title = "trip",
                                mimeType = "video/mp4",
                                contentLength = 24_000_000,
                            ),
                        ),
                    selectedIds = persistentSetOf("1"),
                ),
            networkAccess = NetworkAccessState(NetworkConnection.UNMETERED),
            onBack = {},
            onRetry = {},
            onOpenWeb = {},
            onToggleCandidate = {},
            onDownload = {},
        )
    }
}
