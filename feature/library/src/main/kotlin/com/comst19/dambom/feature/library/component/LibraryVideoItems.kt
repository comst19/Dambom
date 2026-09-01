package com.comst19.dambom.feature.library.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comst19.dambom.core.common.ui.format.formatFileSize
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.feature.library.media.LocalVideoMetadata
import com.comst19.dambom.feature.library.media.rememberLocalVideoMetadata
import com.comst19.dambom.feature.library.toTimeText

@Composable
internal fun VideoCard(
    task: DownloadTask,
    selected: Boolean,
    selectionSelected: Boolean,
    isSelecting: Boolean,
    fileActions: LibraryFileActions,
    onClick: () -> Unit,
    onToggleSelection: () -> Unit,
) {
    val metadata by rememberLocalVideoMetadata(task.localFilePath, task.updatedAtMillis)
    val style = libraryVideoItemStyle(task.sourcePageUrl, selected || selectionSelected)
    Surface(
        onClick = onClick,
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics {
                    stateDescription = style.sourceDescription
                },
        shape = style.shape,
        color = style.containerColor,
        contentColor = style.contentColor,
        border = style.border,
    ) {
        Column {
            LibraryVideoThumbnail(
                metadata = metadata,
                modifier = Modifier.fillMaxWidth().aspectRatio(VIDEO_ASPECT_RATIO),
            )
            VideoItemInfo(
                task = task,
                fileActions = fileActions,
                selectionSelected = selectionSelected,
                isSelecting = isSelecting,
                onToggleSelection = onToggleSelection,
                metadataColor = style.metadataColor,
                sourceBadgeContainerColor = style.sourceBadgeContainerColor,
                sourceBadgeContentColor = style.sourceBadgeContentColor,
                sourceLabel = style.sourceLabel,
                sourceHost = style.sourceHost,
                modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
            )
        }
    }
}

@Composable
internal fun VideoListItem(
    task: DownloadTask,
    selected: Boolean,
    selectionSelected: Boolean,
    isSelecting: Boolean,
    fileActions: LibraryFileActions,
    onClick: () -> Unit,
    onToggleSelection: () -> Unit,
) {
    val metadata by rememberLocalVideoMetadata(task.localFilePath, task.updatedAtMillis)
    val style = libraryVideoItemStyle(task.sourcePageUrl, selected || selectionSelected)
    Surface(
        onClick = onClick,
        modifier =
            Modifier.semantics {
                stateDescription = style.sourceDescription
            },
        shape = style.shape,
        color = style.containerColor,
        contentColor = style.contentColor,
        border = style.border,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LibraryVideoThumbnail(
                metadata = metadata,
                modifier = Modifier.width(LIST_THUMBNAIL_WIDTH).aspectRatio(VIDEO_ASPECT_RATIO),
            )
            VideoItemInfo(
                task = task,
                fileActions = fileActions,
                selectionSelected = selectionSelected,
                isSelecting = isSelecting,
                onToggleSelection = onToggleSelection,
                metadataColor = style.metadataColor,
                sourceBadgeContainerColor = style.sourceBadgeContainerColor,
                sourceBadgeContentColor = style.sourceBadgeContentColor,
                sourceLabel = style.sourceLabel,
                sourceHost = style.sourceHost,
                modifier = Modifier.weight(1f).padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun VideoItemInfo(
    task: DownloadTask,
    fileActions: LibraryFileActions,
    selectionSelected: Boolean,
    isSelecting: Boolean,
    onToggleSelection: () -> Unit,
    metadataColor: Color,
    sourceBadgeContainerColor: Color,
    sourceBadgeContentColor: Color,
    sourceLabel: String,
    sourceHost: String?,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = sourceBadgeContainerColor,
                    contentColor = sourceBadgeContentColor,
                ) {
                    Text(
                        text = sourceLabel,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                sourceHost?.let { host ->
                    Text(
                        text = host,
                        color = metadataColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = task.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = task.downloadedBytes.formatFileSize(),
                color = metadataColor,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (isSelecting) {
            Checkbox(
                checked = selectionSelected,
                onCheckedChange = { onToggleSelection() },
            )
        } else {
            VideoActionsButton(
                task = task,
                actions = fileActions,
                iconOffsetY = (-8).dp,
            )
        }
    }
}

@Composable
private fun LibraryVideoThumbnail(
    metadata: LocalVideoMetadata?,
    modifier: Modifier,
) {
    Box(
        modifier = modifier.clip(THUMBNAIL_SHAPE).background(Color.Black),
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

private val LIST_THUMBNAIL_WIDTH = 112.dp
private val THUMBNAIL_SHAPE = RoundedCornerShape(12.dp)
private const val VIDEO_ASPECT_RATIO = 16f / 9f
