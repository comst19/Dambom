package com.comst19.dambom.feature.detection.component

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comst19.dambom.core.common.ui.VideoThumbnail
import com.comst19.dambom.core.designsystem.DambomShapes
import com.comst19.dambom.core.domain.model.MediaCandidate
import com.comst19.dambom.core.domain.model.MediaVariant
import com.comst19.dambom.feature.detection.R

@Composable
internal fun DetectionCandidateItem(
    candidate: MediaCandidate,
    selectedVariant: MediaVariant,
    index: Int,
    selected: Boolean,
    onClick: () -> Unit,
    onSelectVariant: (String) -> Unit,
    onPreview: () -> Unit,
) {
    var qualityMenuExpanded by remember(candidate.id) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
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
                candidate.thumbnailUrl?.let { thumbnailUrl ->
                    VideoThumbnail(
                        data = thumbnailUrl,
                        contentDescription = stringResource(R.string.detection_thumbnail, index),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
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
                    Box {
                        TextButton(onClick = { qualityMenuExpanded = true }) {
                            Text(selectedVariant.quality)
                        }
                        DropdownMenu(
                            expanded = qualityMenuExpanded,
                            onDismissRequest = { qualityMenuExpanded = false },
                        ) {
                            candidate.downloadVariants.forEach { variant ->
                                DropdownMenuItem(
                                    text = { Text(variant.quality) },
                                    onClick = {
                                        qualityMenuExpanded = false
                                        onSelectVariant(variant.url)
                                    },
                                )
                            }
                        }
                    }
                    selectedVariant.contentLength?.let {
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
private fun MediaCandidate.displayTitle(index: Int): String =
    if (title.isBlank() || UUID_TITLE_REGEX.matches(title)) {
        stringResource(R.string.detection_video_number, index)
    } else {
        title
    }

private fun MediaCandidate.sourceLabel(): String = Uri.parse(url).host?.removePrefix("www.") ?: url

private fun Long.formatBytes(): String {
    val megabytes = this / (1024.0 * 1024.0)
    return "%.1f MB".format(megabytes)
}

private val UUID_TITLE_REGEX = Regex("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
private const val VIDEO_ASPECT_RATIO = 16f / 9f
