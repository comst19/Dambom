package com.comst19.dambom.feature.library.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
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
    val metadata by rememberLocalVideoMetadata(task.localFilePath)
    val style = libraryVideoItemStyle(task.sourcePageUrl, selected && !isSelecting)
    Surface(
        onClick = onClick,
        enabled = !task.deletePending || isSelecting,
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics {
                    stateDescription = style.sourceDescription
                },
        shape = style.shape,
        color = style.containerColor,
        contentColor = style.contentColor,
    ) {
        Column {
            LibraryVideoThumbnail(
                metadata,
                task.deletePending,
                Modifier.fillMaxWidth().aspectRatio(VIDEO_ASPECT_RATIO),
            )
            VideoItemInfo(
                task = task,
                metadataColor = style.metadataColor,
                source = style.sourceHost ?: style.sourceLabel,
                modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
                trailing = {
                    if (isSelecting) {
                        Checkbox(checked = selectionSelected, onCheckedChange = { onToggleSelection() })
                    } else {
                        Row {
                            VideoFavoriteButton(task, fileActions.onToggleFavorite)
                            VideoActionsButton(task, fileActions)
                        }
                    }
                },
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
    val metadata by rememberLocalVideoMetadata(task.localFilePath)
    val style = libraryVideoItemStyle(task.sourcePageUrl, selected && !isSelecting)
    Surface(
        onClick = onClick,
        enabled = !task.deletePending || isSelecting,
        modifier =
            Modifier.fillMaxWidth().testTag("library-video-${task.id}").semantics {
                stateDescription = style.sourceDescription
                this.selected = selected && !isSelecting
            },
        shape = style.shape,
        color = style.containerColor,
        contentColor = style.contentColor,
    ) {
        BoxWithConstraints {
            val thumbnailWidth = if (maxWidth < 360.dp) 112.dp else 128.dp
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 112.dp)
                        .padding(start = 12.dp, top = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.Top,
            ) {
                VideoListThumbnail(task, metadata, thumbnailWidth)
                VideoListInfo(
                    task = task,
                    source = style.sourceHost ?: style.sourceLabel,
                    modifier = Modifier.weight(1f).padding(start = 12.dp, end = 52.dp),
                )
            }
            if (isSelecting) {
                Checkbox(
                    checked = selectionSelected,
                    onCheckedChange = { onToggleSelection() },
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            } else {
                VideoActionsButton(task, fileActions, Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 4.dp))
                if (!task.deletePending) {
                    VideoFavoriteButton(
                        task,
                        fileActions.onToggleFavorite,
                        Modifier.align(Alignment.BottomEnd).padding(bottom = 4.dp, end = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoListThumbnail(
    task: DownloadTask,
    metadata: LocalVideoMetadata?,
    width: androidx.compose.ui.unit.Dp,
) {
    Box(Modifier.width(width).aspectRatio(VIDEO_ASPECT_RATIO).testTag("library-thumbnail-${task.id}")) {
        LibraryVideoThumbnail(metadata, task.deletePending, Modifier.fillMaxSize())
    }
}

@Composable
private fun VideoListInfo(
    task: DownloadTask,
    source: String,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = task.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = source,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (task.deletePending) {
            Text(
                text = stringResource(com.comst19.dambom.feature.library.R.string.library_delete_pending),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun VideoItemInfo(
    task: DownloadTask,
    metadataColor: Color,
    source: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = task.title,
            modifier = Modifier.padding(end = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text =
                        if (task.deletePending) {
                            stringResource(com.comst19.dambom.feature.library.R.string.library_delete_pending)
                        } else {
                            task.downloadedBytes.formatFileSize()
                        },
                    color = metadataColor,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = source,
                    color = metadataColor,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            trailing()
        }
    }
}

@Composable
private fun LibraryVideoThumbnail(
    metadata: LocalVideoMetadata?,
    deletePending: Boolean,
    modifier: Modifier,
) {
    Box(
        modifier = modifier.clip(THUMBNAIL_SHAPE).background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        val thumbnail = metadata?.thumbnail
        if (thumbnail == null) {
            Icon(
                imageVector = if (deletePending) Icons.Outlined.DeleteOutline else Icons.Outlined.PlayArrow,
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

private val THUMBNAIL_SHAPE = RoundedCornerShape(12.dp)
private const val VIDEO_ASPECT_RATIO = 16f / 9f
