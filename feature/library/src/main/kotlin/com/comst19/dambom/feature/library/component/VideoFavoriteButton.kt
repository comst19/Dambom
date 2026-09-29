package com.comst19.dambom.feature.library.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.comst19.dambom.core.designsystem.DambomIconTooltip
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.feature.library.R

@Composable
internal fun VideoFavoriteButton(
    task: DownloadTask,
    onToggle: (DownloadTask) -> Unit,
    modifier: Modifier = Modifier,
    onVideoSurface: Boolean = false,
    onThumbnail: Boolean = false,
) {
    val actionLabel =
        stringResource(if (task.isFavorite) R.string.library_favorite_remove else R.string.library_favorite_add)
    val iconBackground =
        when {
            !onThumbnail -> Color.Transparent
            task.isFavorite -> MaterialTheme.colorScheme.primaryContainer
            else -> Color.Black.copy(alpha = THUMBNAIL_FAVORITE_SCRIM)
        }
    DambomIconTooltip(actionLabel) {
        IconToggleButton(
            checked = task.isFavorite,
            onCheckedChange = { onToggle(task) },
            modifier = modifier.size(48.dp),
            enabled = !task.deletePending && task.localFilePath != null,
            colors =
                IconButtonDefaults.iconToggleButtonColors(
                    containerColor = Color.Transparent,
                    contentColor =
                        if (onVideoSurface || onThumbnail) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    checkedContainerColor = Color.Transparent,
                    checkedContentColor =
                        when {
                            onThumbnail -> MaterialTheme.colorScheme.onPrimaryContainer
                            onVideoSurface -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.primary
                        },
                ),
        ) {
            Icon(
                modifier =
                    if (onThumbnail) {
                        Modifier.size(24.dp).background(iconBackground, CircleShape).padding(3.dp)
                    } else {
                        Modifier.size(20.dp)
                    },
                imageVector = if (task.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription =
                    stringResource(R.string.library_favorite_action_description, actionLabel, task.title),
            )
        }
    }
}

private const val THUMBNAIL_FAVORITE_SCRIM = 0.6f
