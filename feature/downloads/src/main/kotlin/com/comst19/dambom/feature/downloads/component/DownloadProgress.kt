package com.comst19.dambom.feature.downloads.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.comst19.dambom.core.designsystem.DambomLinearProgressIndicator
import com.comst19.dambom.feature.downloads.R

@Composable
internal fun DownloadProgress(
    progress: Float?,
    running: Boolean,
) {
    if (progress != null) {
        Text(
            stringResource(R.string.downloads_progress_percent, (progress * PERCENT_SCALE).toInt()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (progress != null || running) {
        DambomLinearProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private const val PERCENT_SCALE = 100
