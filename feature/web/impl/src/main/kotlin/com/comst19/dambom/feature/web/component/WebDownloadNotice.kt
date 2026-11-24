package com.comst19.dambom.feature.web.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.comst19.dambom.core.domain.model.UnsupportedReason
import com.comst19.dambom.feature.web.R
import com.comst19.dambom.feature.web.contract.WebDetectionState

@Composable
internal fun WebDownloadNotice(
    detectionState: WebDetectionState,
    modifier: Modifier = Modifier,
) {
    val reason = (detectionState as? WebDetectionState.NotFound)?.reason
    val message =
        when {
            detectionState is WebDetectionState.Found && detectionState.hasProtectedMedia -> R.string.web_drm_excluded
            reason == UnsupportedReason.DRM_PROTECTED -> R.string.web_drm_protected
            reason == UnsupportedReason.UNSUPPORTED_SERVICE -> R.string.web_unsupported_service
            else -> return
        }
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            stringResource(message),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
