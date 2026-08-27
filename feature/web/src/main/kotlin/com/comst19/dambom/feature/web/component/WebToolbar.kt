package com.comst19.dambom.feature.web.component

import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ScreenSearchDesktop
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.comst19.dambom.core.domain.model.UnsupportedReason
import com.comst19.dambom.feature.web.R
import com.comst19.dambom.feature.web.contract.WebDetectionState

@Composable
internal fun WebToolbar(
    webView: WebView?,
    detectionState: WebDetectionState,
    onOpenDetectedMedia: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { webView?.goBack() }, enabled = webView?.canGoBack() == true) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.web_go_back))
        }
        IconButton(onClick = { webView?.goForward() }, enabled = webView?.canGoForward() == true) {
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, stringResource(R.string.web_go_forward))
        }
        IconButton(onClick = { webView?.reload() }) {
            Icon(Icons.Outlined.Refresh, stringResource(R.string.web_refresh))
        }
        Spacer(Modifier.weight(1f))
        TextButton(
            onClick = onOpenDetectedMedia,
            enabled = detectionState is WebDetectionState.Found,
        ) {
            Text(detectionState.resultLabel())
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun WebRescanButton(
    detectionState: WebDetectionState,
    onScan: () -> Unit,
) {
    val tooltipState = rememberTooltipState()
    LaunchedEffect(detectionState) {
        if (detectionState.shouldShowPlaybackHint()) tooltipState.show()
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(stringResource(R.string.web_playback_hint)) } },
        state = tooltipState,
    ) {
        FloatingActionButton(onClick = onScan) {
            Icon(
                imageVector = Icons.Outlined.ScreenSearchDesktop,
                contentDescription = stringResource(R.string.web_rescan),
            )
        }
    }
}

@Composable
internal fun WebNavigationErrorContent(
    onRetry: () -> Unit,
    onOpenExternal: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.Language,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.web_connection_error_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.web_connection_error_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onRetry) {
                Icon(Icons.Outlined.Refresh, contentDescription = null)
                Text(text = stringResource(R.string.web_retry), modifier = Modifier.padding(start = 8.dp))
            }
            TextButton(onClick = onOpenExternal) { Text(stringResource(R.string.web_open_external)) }
            Text(
                text = stringResource(R.string.web_external_download_notice),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

internal fun WebDetectionState.shouldShowPlaybackHint(): Boolean =
    this == WebDetectionState.Idle ||
        (
            this is WebDetectionState.NotFound &&
                (reason == UnsupportedReason.NO_MEDIA || reason == UnsupportedReason.UNSUPPORTED_FORMAT)
        )

@Composable
private fun WebDetectionState.resultLabel(): String =
    when (this) {
        WebDetectionState.Idle -> stringResource(R.string.web_no_detected_video)
        WebDetectionState.Scanning -> stringResource(R.string.web_detecting)
        is WebDetectionState.Found -> stringResource(R.string.web_open_detected_count, count)
        is WebDetectionState.NotFound -> stringResource(R.string.web_no_detected_video)
    }
