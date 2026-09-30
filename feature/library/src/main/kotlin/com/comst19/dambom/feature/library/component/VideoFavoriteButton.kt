package com.comst19.dambom.feature.library.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
    iconAlignment: Alignment = Alignment.Center,
) {
    val actionLabel =
        stringResource(if (task.isFavorite) R.string.library_favorite_remove else R.string.library_favorite_add)
    Box(modifier) {
        DambomIconTooltip(actionLabel) {
            IconToggleButton(
                checked = task.isFavorite,
                onCheckedChange = { onToggle(task) },
                modifier = Modifier.size(48.dp),
                enabled = !task.deletePending && task.localFilePath != null,
                colors =
                    IconButtonDefaults.iconToggleButtonColors(
                        containerColor = Color.Transparent,
                        contentColor =
                            if (onVideoSurface) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        checkedContainerColor = Color.Transparent,
                        checkedContentColor =
                            when {
                                onVideoSurface -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.primary
                            },
                    ),
            ) {
                Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = iconAlignment) {
                    Icon(
                        modifier = Modifier.size(20.dp),
                        imageVector = if (task.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription =
                            stringResource(R.string.library_favorite_action_description, actionLabel, task.title),
                    )
                }
            }
        }
    }
}
