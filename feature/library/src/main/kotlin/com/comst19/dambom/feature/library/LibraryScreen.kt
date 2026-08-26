package com.comst19.dambom.feature.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.automirrored.outlined.ViewSidebar
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import com.comst19.dambom.core.common.ui.appScaffoldPadding
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.core.designsystem.FormFactorPreviews
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.feature.library.contract.LibraryUiState
import com.comst19.dambom.feature.library.contract.LibraryViewMode
import kotlinx.collections.immutable.persistentListOf
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems

@Composable
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal fun LibraryRoute(
    isDetailPaneVisible: Boolean,
    onDetailPaneVisibilityChange: (Boolean) -> Unit,
) {
    val viewModel: LibraryViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val fileActions = rememberLibraryFileActions(viewModel)
    val multiplePanes = currentWindowAdaptiveInfoV2().windowSizeClass.supportsMultiplePanes

    LibraryScreen(
        uiState = uiState,
        fileActions = fileActions,
        onQueryChange = viewModel::updateQuery,
        onViewModeChange = viewModel::setViewMode,
        onVideoClick = { task ->
            if (multiplePanes && !isDetailPaneVisible) onDetailPaneVisibilityChange(true)
            viewModel.openVideo(task.id)
        },
        showInlineEmptyState = !multiplePanes || !isDetailPaneVisible,
        showDetailPaneControl = multiplePanes,
        isDetailPaneVisible = isDetailPaneVisible,
        onDetailPaneVisibilityChange = onDetailPaneVisibilityChange,
    )
}

@Composable
internal fun LibraryScreen(
    uiState: LibraryUiState,
    fileActions: LibraryFileActions,
    onQueryChange: (String) -> Unit,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onVideoClick: (DownloadTask) -> Unit,
    showInlineEmptyState: Boolean = true,
    showDetailPaneControl: Boolean = false,
    isDetailPaneVisible: Boolean = true,
    onDetailPaneVisibilityChange: (Boolean) -> Unit = {},
) {
    LibraryPane(
        uiState = uiState,
        fileActions = fileActions,
        onQueryChange = onQueryChange,
        onViewModeChange = onViewModeChange,
        onVideoClick = onVideoClick,
        showInlineEmptyState = showInlineEmptyState,
        showDetailPaneControl = showDetailPaneControl,
        isDetailPaneVisible = isDetailPaneVisible,
        onDetailPaneVisibilityChange = onDetailPaneVisibilityChange,
        modifier = Modifier.fillMaxSize().appScaffoldPadding(),
    )
}

@Composable
private fun LibraryPane(
    uiState: LibraryUiState,
    fileActions: LibraryFileActions,
    onQueryChange: (String) -> Unit,
    onViewModeChange: (LibraryViewMode) -> Unit,
    onVideoClick: (DownloadTask) -> Unit,
    showInlineEmptyState: Boolean,
    showDetailPaneControl: Boolean,
    isDetailPaneVisible: Boolean,
    onDetailPaneVisibilityChange: (Boolean) -> Unit,
    modifier: Modifier,
) {
    Column(modifier) {
        LibraryHeader(
            viewMode = uiState.viewMode,
            onViewModeChange = onViewModeChange,
            showDetailPaneControl = showDetailPaneControl,
            isDetailPaneVisible = isDetailPaneVisible,
            onDetailPaneVisibilityChange = onDetailPaneVisibilityChange,
        )
        LibrarySearchField(
            query = uiState.query,
            onQueryChange = onQueryChange,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
        )
        if (!uiState.hasVideos) {
            if (showInlineEmptyState) {
                EmptyLibrary(Modifier.weight(1f))
            }
        } else if (uiState.videos.isEmpty()) {
            EmptySearchResults(
                query = uiState.query,
                modifier = Modifier.weight(1f),
            )
        } else if (uiState.viewMode == LibraryViewMode.GRID) {
            VideoGrid(
                uiState = uiState,
                fileActions = fileActions,
                onVideoClick = onVideoClick,
            )
        } else {
            VideoList(
                uiState = uiState,
                fileActions = fileActions,
                onVideoClick = onVideoClick,
            )
        }
    }
}

@Composable
private fun LibraryHeader(
    viewMode: LibraryViewMode,
    onViewModeChange: (LibraryViewMode) -> Unit,
    showDetailPaneControl: Boolean,
    isDetailPaneVisible: Boolean,
    onDetailPaneVisibilityChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 16.dp, end = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.library_title),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        IconButton(
            onClick = {
                onViewModeChange(
                    if (viewMode == LibraryViewMode.GRID) LibraryViewMode.LIST else LibraryViewMode.GRID,
                )
            },
        ) {
            Icon(
                imageVector =
                    if (viewMode == LibraryViewMode.GRID) {
                        Icons.AutoMirrored.Outlined.ViewList
                    } else {
                        Icons.Outlined.GridView
                    },
                contentDescription =
                    stringResource(
                        if (viewMode == LibraryViewMode.GRID) {
                            R.string.library_view_as_list
                        } else {
                            R.string.library_view_as_grid
                        },
                    ),
            )
        }
        if (showDetailPaneControl) {
            IconToggleButton(
                checked = isDetailPaneVisible,
                onCheckedChange = onDetailPaneVisibilityChange,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ViewSidebar,
                    contentDescription =
                        stringResource(
                            if (isDetailPaneVisible) {
                                R.string.library_hide_details
                            } else {
                                R.string.library_show_details
                            },
                        ),
                    tint =
                        if (isDetailPaneVisible) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
    }
}

@Composable
private fun VideoGrid(
    uiState: LibraryUiState,
    fileActions: LibraryFileActions,
    onVideoClick: (DownloadTask) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(MIN_VIDEO_CARD_WIDTH),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        gridItems(
            items = uiState.videos,
            key = DownloadTask::id,
            contentType = { VIDEO_ITEM_CONTENT_TYPE },
        ) { task ->
            VideoCard(
                task = task,
                selected = task.id == uiState.selectedVideo?.id,
                fileActions = fileActions,
                onClick = { onVideoClick(task) },
            )
        }
    }
}

@Composable
private fun VideoList(
    uiState: LibraryUiState,
    fileActions: LibraryFileActions,
    onVideoClick: (DownloadTask) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        listItems(
            items = uiState.videos,
            key = DownloadTask::id,
            contentType = { VIDEO_ITEM_CONTENT_TYPE },
        ) { task ->
            VideoListItem(
                task = task,
                selected = task.id == uiState.selectedVideo?.id,
                fileActions = fileActions,
                onClick = { onVideoClick(task) },
            )
        }
    }
}

@Composable
private fun LibrarySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.library_search_placeholder)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
            )
        },
        trailingIcon =
            if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.library_search_clear),
                        )
                    }
                }
            } else {
                null
            },
        singleLine = true,
    )
}

@Composable
private fun EmptyLibrary(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.VideoLibrary,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.library_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.library_empty_description),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun EmptySearchResults(
    query: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.library_search_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.library_search_empty_description, query),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun VideoCard(
    task: DownloadTask,
    selected: Boolean,
    fileActions: LibraryFileActions,
    onClick: () -> Unit,
) {
    val metadata by rememberLocalVideoMetadata(task.localFilePath)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
            ),
    ) {
        VideoThumbnail(
            metadata = metadata,
            modifier = Modifier.fillMaxWidth().aspectRatio(VIDEO_ASPECT_RATIO),
        )
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleSmall,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = task.downloadedBytes.formatBytes(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            VideoActionsButton(task = task, actions = fileActions)
        }
    }
}

@Composable
private fun VideoListItem(
    task: DownloadTask,
    selected: Boolean,
    fileActions: LibraryFileActions,
    onClick: () -> Unit,
) {
    val metadata by rememberLocalVideoMetadata(task.localFilePath)
    Card(
        onClick = onClick,
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VideoThumbnail(
                metadata = metadata,
                modifier = Modifier.width(LIST_THUMBNAIL_WIDTH).aspectRatio(VIDEO_ASPECT_RATIO),
            )
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = task.downloadedBytes.formatBytes(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            VideoActionsButton(task = task, actions = fileActions)
        }
    }
}

@Composable
private fun VideoThumbnail(
    metadata: LocalVideoMetadata?,
    modifier: Modifier,
) {
    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        val thumbnail = metadata?.thumbnail
        if (thumbnail == null) {
            Icon(
                imageVector = Icons.Outlined.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = Color.White,
            )
        } else {
            Image(
                bitmap = thumbnail.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        metadata?.durationMillis?.let { durationMillis ->
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                shape = RoundedCornerShape(6.dp),
                color = Color.Black.copy(alpha = 0.72f),
            ) {
                Text(
                    text = durationMillis.toTimeText(),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
internal fun LibraryDetailPlaceholderRoute() {
    val viewModel: LibraryViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.hasVideos) {
        EmptyLibraryDetail()
    } else {
        EmptyLibrary(Modifier.fillMaxSize())
    }
}

@Composable
private fun EmptyLibraryDetail() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Icon(
            imageVector = Icons.Outlined.VideoLibrary,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.library_select_video_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.library_select_video_description),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

internal fun Long.formatBytes(): String {
    val megabytes = this / (1024.0 * 1024.0)
    return if (megabytes >= 1.0) "%.1f MB".format(megabytes) else "%.0f KB".format(this / 1024.0)
}

@FormFactorPreviews
@Composable
private fun LibraryScreenPreview() {
    DambomTheme {
        LibraryScreen(
            uiState =
                LibraryUiState(
                    videos = persistentListOf(previewTask("여행 영상"), previewTask("인터뷰")),
                    hasVideos = true,
                ),
            fileActions =
                LibraryFileActions(
                    onRename = { _, _ -> },
                    onExport = {},
                    onShare = {},
                    onCopyLink = {},
                    onOpenOriginal = {},
                    onDelete = {},
                ),
            onQueryChange = {},
            onViewModeChange = {},
            onVideoClick = {},
        )
    }
}

private fun previewTask(title: String) =
    DownloadTask(
        id = title,
        url = "https://example.com/video.mp4",
        sourcePageUrl = "https://example.com",
        title = title,
        mimeType = "video/mp4",
        expectedBytes = 4_000_000L,
        downloadedBytes = 4_000_000L,
        quality = "원본",
        status = DownloadStatus.COMPLETED,
        failureReason = null,
        localFileName = "video.mp4",
        localFilePath = "/data/user/0/example/files/videos/video.mp4",
        createdAtMillis = 1L,
        updatedAtMillis = 1L,
    )

private val MIN_VIDEO_CARD_WIDTH = 156.dp
private val LIST_THUMBNAIL_WIDTH = 112.dp
private const val VIDEO_ASPECT_RATIO = 16f / 9f
private const val VIDEO_ITEM_CONTENT_TYPE = "video"

private val WindowSizeClass.supportsMultiplePanes: Boolean
    get() = isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
