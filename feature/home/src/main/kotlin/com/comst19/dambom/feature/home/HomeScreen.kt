package com.comst19.dambom.feature.home

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comst19.dambom.core.common.ui.appScaffoldPadding
import com.comst19.dambom.core.designsystem.DambomShapes
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.core.designsystem.FormFactorPreviews
import com.comst19.dambom.core.domain.model.NetworkAccessState
import com.comst19.dambom.feature.home.contract.HomeDownloadSummary
import com.comst19.dambom.feature.home.contract.HomeUiState

@Composable
internal fun HomeRoute(
    networkAccess: NetworkAccessState,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (uiState.clipboardSuggestionEnabled) {
            viewModel.suggestClipboardText(context.clipboardText())
        }
    }

    HomeScreen(
        uiState = uiState,
        canUseInternet = networkAccess.canUseInternet,
        onUrlChange = viewModel::updateUrl,
        onPaste = { viewModel.useClipboardText(context.clipboardText()) },
        onAnalyze = viewModel::analyzeUrl,
        onOpenWeb = viewModel::openWeb,
        onOpenDownloads = viewModel::openDownloads,
        onOpenSettings = viewModel::openSettings,
        onClipboardConsent = viewModel::setClipboardSuggestionEnabled,
        onUseClipboardSuggestion = { viewModel.useClipboardText(uiState.clipboardUrl) },
        onDismissClipboardSuggestion = viewModel::dismissClipboardSuggestion,
        onAnalyzeSharedUrl = viewModel::analyzeSharedUrl,
        onDismissSharedUrl = viewModel::dismissSharedUrl,
    )
}

@Composable
internal fun HomeScreen(
    uiState: HomeUiState,
    canUseInternet: Boolean,
    onUrlChange: (String) -> Unit,
    onPaste: () -> Unit,
    onAnalyze: () -> Unit,
    onOpenWeb: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
    onClipboardConsent: (Boolean) -> Unit,
    onUseClipboardSuggestion: () -> Unit,
    onDismissClipboardSuggestion: () -> Unit,
    onAnalyzeSharedUrl: () -> Unit,
    onDismissSharedUrl: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .appScaffoldPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = HOME_CONTENT_MAX_WIDTH)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            HomeHeader(onOpenSettings)
            Spacer(Modifier.height(28.dp))
            HomePrimarySection(
                uiState = uiState,
                canUseInternet = canUseInternet,
                onUrlChange = onUrlChange,
                onPaste = onPaste,
                onAnalyze = onAnalyze,
                onUseClipboardSuggestion = onUseClipboardSuggestion,
                onDismissClipboardSuggestion = onDismissClipboardSuggestion,
            )
            Spacer(Modifier.height(28.dp))
            HomeSupportingSection(
                downloadSummary = uiState.downloadSummary,
                canUseInternet = canUseInternet,
                onOpenWeb = onOpenWeb,
                onOpenDownloads = onOpenDownloads,
            )
        }
    }

    if (uiState.showClipboardConsent) {
        ClipboardConsentDialog(onClipboardConsent)
    }
    uiState.sharedUrl?.let {
        SharedUrlDialog(
            url = it,
            onAnalyze = onAnalyzeSharedUrl,
            onDismiss = onDismissSharedUrl,
            canUseInternet = canUseInternet,
        )
    }
}

@Composable
private fun HomePrimarySection(
    uiState: HomeUiState,
    canUseInternet: Boolean,
    onUrlChange: (String) -> Unit,
    onPaste: () -> Unit,
    onAnalyze: () -> Unit,
    onUseClipboardSuggestion: () -> Unit,
    onDismissClipboardSuggestion: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.home_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = uiState.url,
            onValueChange = onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.home_url_label)) },
            placeholder = { Text(stringResource(R.string.home_url_placeholder)) },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = onPaste) {
                    Icon(
                        imageVector = Icons.Outlined.ContentPaste,
                        contentDescription = stringResource(R.string.home_paste),
                    )
                }
            },
            shape = DambomShapes.Control,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onAnalyze,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            enabled = uiState.isUrlValid && canUseInternet,
            shape = DambomShapes.Control,
        ) {
            Text(stringResource(R.string.home_analyze), style = MaterialTheme.typography.labelLarge)
        }
        uiState.clipboardUrl?.let {
            Spacer(Modifier.height(20.dp))
            ClipboardSuggestion(
                url = it,
                onUse = onUseClipboardSuggestion,
                onDismiss = onDismissClipboardSuggestion,
            )
        }
    }
}

@Composable
private fun HomeSupportingSection(
    downloadSummary: HomeDownloadSummary,
    canUseInternet: Boolean,
    onOpenWeb: () -> Unit,
    onOpenDownloads: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            text = stringResource(R.string.home_web_prompt_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.home_web_prompt_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = onOpenWeb,
            enabled = canUseInternet,
        ) {
            Icon(imageVector = Icons.Outlined.Language, contentDescription = null)
            Text(
                text = stringResource(R.string.home_browse_web),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        if (downloadSummary.isVisible) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.home_current_activity),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            HomeDownloadStatus(summary = downloadSummary, onClick = onOpenDownloads)
        }
    }
}

@Composable
private fun HomeDownloadStatus(
    summary: HomeDownloadSummary,
    onClick: () -> Unit,
) {
    val title =
        when {
            summary.activeCount > 0 -> stringResource(R.string.home_download_active, summary.activeCount)
            summary.pausedCount > 0 -> stringResource(R.string.home_download_paused, summary.pausedCount)
            else -> stringResource(R.string.home_download_failed, summary.failedCount)
        }
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = DambomShapes.Card,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.home_download_open),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (summary.activeCount > 0) {
                LinearProgressIndicator(
                    progress = { summary.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun HomeHeader(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.home_brand),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        IconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.home_open_settings),
            )
        }
    }
}

@Composable
private fun ClipboardSuggestion(
    url: String,
    onUse: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = DambomShapes.Card,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.home_clipboard_found), style = MaterialTheme.typography.titleMedium)
            Text(
                text = url,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onUse) { Text(stringResource(R.string.home_use_link)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_dismiss)) }
            }
        }
    }
}

@Composable
private fun ClipboardConsentDialog(onDecision: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = { onDecision(false) },
        title = { Text(stringResource(R.string.home_clipboard_consent_title)) },
        text = { Text(stringResource(R.string.home_clipboard_consent_description)) },
        confirmButton = {
            TextButton(onClick = { onDecision(true) }) {
                Text(stringResource(R.string.home_clipboard_consent_allow))
            }
        },
        dismissButton = {
            TextButton(onClick = { onDecision(false) }) {
                Text(stringResource(R.string.home_clipboard_consent_deny))
            }
        },
    )
}

@Composable
private fun SharedUrlDialog(
    url: String,
    onAnalyze: () -> Unit,
    onDismiss: () -> Unit,
    canUseInternet: Boolean,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_shared_url_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.home_shared_url_description))
                Text(
                    url,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAnalyze, enabled = canUseInternet) { Text(stringResource(R.string.home_analyze_now)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_cancel)) }
        },
    )
}

private fun Context.clipboardText(): String? =
    (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
        .primaryClip
        ?.getItemAt(0)
        ?.coerceToText(this)
        ?.toString()

private val HOME_CONTENT_MAX_WIDTH = 720.dp

@Preview
@FormFactorPreviews
@Composable
private fun HomeScreenPreview() {
    DambomTheme {
        HomeScreen(
            uiState = HomeUiState(url = "https://example.com/video", isUrlValid = true),
            canUseInternet = true,
            onUrlChange = {},
            onPaste = {},
            onAnalyze = {},
            onOpenWeb = {},
            onOpenDownloads = {},
            onOpenSettings = {},
            onClipboardConsent = {},
            onUseClipboardSuggestion = {},
            onDismissClipboardSuggestion = {},
            onAnalyzeSharedUrl = {},
            onDismissSharedUrl = {},
        )
    }
}
