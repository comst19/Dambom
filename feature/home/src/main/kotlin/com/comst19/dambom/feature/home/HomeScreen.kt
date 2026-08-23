package com.comst19.dambom.feature.home

import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.core.designsystem.FormFactorPreviews

@Composable
internal fun HomeRoute(viewModel: HomeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (uiState.clipboardSuggestionEnabled) {
            viewModel.suggestClipboardText(context.clipboardText())
        }
    }

    HomeScreen(
        uiState = uiState,
        onUrlChange = viewModel::updateUrl,
        onPaste = { viewModel.useClipboardText(context.clipboardText()) },
        onAnalyze = viewModel::analyzeUrl,
        onOpenSettings = viewModel::openSettings,
        onClipboardConsent = viewModel::setClipboardSuggestionEnabled,
        onUseClipboardSuggestion = { viewModel.useClipboardText(uiState.clipboardUrl) },
        onDismissClipboardSuggestion = viewModel::dismissClipboardSuggestion,
        onUseSharedUrl = viewModel::useSharedUrl,
        onAnalyzeSharedUrl = viewModel::analyzeSharedUrl,
        onDismissSharedUrl = viewModel::dismissSharedUrl,
    )
}

@Composable
internal fun HomeScreen(
    uiState: HomeUiState,
    onUrlChange: (String) -> Unit,
    onPaste: () -> Unit,
    onAnalyze: () -> Unit,
    onOpenSettings: () -> Unit,
    onClipboardConsent: (Boolean) -> Unit,
    onUseClipboardSuggestion: () -> Unit,
    onDismissClipboardSuggestion: () -> Unit,
    onUseSharedUrl: () -> Unit,
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
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            HomeHeader(onOpenSettings)
            Spacer(Modifier.height(36.dp))
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
                shape = RoundedCornerShape(16.dp),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onAnalyze,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                enabled = uiState.isUrlValid,
                shape = RoundedCornerShape(16.dp),
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
            Spacer(Modifier.height(36.dp))
            Text(
                text = stringResource(R.string.home_other_methods),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(12.dp))
            MethodRow(
                title = stringResource(R.string.home_share_title),
                description = stringResource(R.string.home_share_description),
            )
            Spacer(Modifier.height(8.dp))
            MethodRow(
                title = stringResource(R.string.home_browser_title),
                description = stringResource(R.string.home_browser_description),
            )
        }
    }

    if (uiState.showClipboardConsent) {
        ClipboardConsentDialog(onClipboardConsent)
    }
    uiState.sharedUrl?.let {
        SharedUrlDialog(
            url = it,
            onUse = onUseSharedUrl,
            onAnalyze = onAnalyzeSharedUrl,
            onDismiss = onDismissSharedUrl,
        )
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
        shape = RoundedCornerShape(18.dp),
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
private fun MethodRow(
    title: String,
    description: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    onUse: () -> Unit,
    onAnalyze: () -> Unit,
    onDismiss: () -> Unit,
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
            TextButton(onClick = onAnalyze) { Text(stringResource(R.string.home_analyze_now)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onUse) { Text(stringResource(R.string.home_put_in_input)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_cancel)) }
            }
        },
    )
}

private fun Context.clipboardText(): String? =
    (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
        .primaryClip
        ?.getItemAt(0)
        ?.coerceToText(this)
        ?.toString()

@Preview
@FormFactorPreviews
@Composable
private fun HomeScreenPreview() {
    DambomTheme {
        HomeScreen(
            uiState = HomeUiState(url = "https://example.com/video", isUrlValid = true),
            onUrlChange = {},
            onPaste = {},
            onAnalyze = {},
            onOpenSettings = {},
            onClipboardConsent = {},
            onUseClipboardSuggestion = {},
            onDismissClipboardSuggestion = {},
            onUseSharedUrl = {},
            onAnalyzeSharedUrl = {},
            onDismissSharedUrl = {},
        )
    }
}
