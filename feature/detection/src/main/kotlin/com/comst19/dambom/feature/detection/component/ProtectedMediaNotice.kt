package com.comst19.dambom.feature.detection.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.comst19.dambom.feature.detection.R

@Composable
internal fun ProtectedMediaNotice(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.detection_drm_excluded),
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
