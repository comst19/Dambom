package com.comst19.dambom.feature.home

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comst19.dambom.core.common.ui.appScaffoldPadding
import com.comst19.dambom.core.common.ui.currentAdaptiveLayoutInfo
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.core.designsystem.FormFactorPreviews
import com.comst19.dambom.core.designsystem.previewNoOp
import com.comst19.dambom.core.domain.model.NetworkAccessState
import com.comst19.dambom.feature.home.component.ClipboardConsentDialog
import com.comst19.dambom.feature.home.component.HomeHeader
import com.comst19.dambom.feature.home.component.HomePrimarySection
import com.comst19.dambom.feature.home.component.HomeSupportingSection
import com.comst19.dambom.feature.home.component.SharedUrlDialog
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
    val compactHeight = currentAdaptiveLayoutInfo().isCompactHeight
    val screenVerticalPadding = if (compactHeight) 8.dp else 16.dp
    val sectionSpacing = if (compactHeight) 16.dp else 28.dp

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
                    .padding(horizontal = 20.dp, vertical = screenVerticalPadding),
        ) {
            HomeHeader(onOpenSettings)
            Spacer(Modifier.height(sectionSpacing))
            HomePrimarySection(
                uiState = uiState,
                canUseInternet = canUseInternet,
                onUrlChange = onUrlChange,
                onPaste = onPaste,
                onAnalyze = onAnalyze,
                onUseClipboardSuggestion = onUseClipboardSuggestion,
                onDismissClipboardSuggestion = onDismissClipboardSuggestion,
                compactHeight = compactHeight,
            )
            Spacer(Modifier.height(sectionSpacing))
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
            onUrlChange = ::previewNoOp,
            onPaste = ::previewNoOp,
            onAnalyze = ::previewNoOp,
            onOpenWeb = ::previewNoOp,
            onOpenDownloads = ::previewNoOp,
            onOpenSettings = ::previewNoOp,
            onClipboardConsent = ::previewNoOp,
            onUseClipboardSuggestion = ::previewNoOp,
            onDismissClipboardSuggestion = ::previewNoOp,
            onAnalyzeSharedUrl = ::previewNoOp,
            onDismissSharedUrl = ::previewNoOp,
        )
    }
}
