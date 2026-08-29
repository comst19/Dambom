package com.comst19.dambom.feature.detection

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comst19.dambom.core.common.ui.AppScreen
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.core.designsystem.FormFactorPreviews
import com.comst19.dambom.core.designsystem.previewNoOp
import com.comst19.dambom.core.domain.model.MediaCandidate
import com.comst19.dambom.core.domain.model.NetworkAccessState
import com.comst19.dambom.core.domain.model.NetworkConnection
import com.comst19.dambom.feature.detection.component.DetectionCandidateContent
import com.comst19.dambom.feature.detection.component.LoadingDetectionContent
import com.comst19.dambom.feature.detection.component.NetworkUnavailableDetectionContent
import com.comst19.dambom.feature.detection.component.UnsupportedDetectionContent
import com.comst19.dambom.feature.detection.contract.DetectionUiState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

@Composable
internal fun DetectionRoute(
    url: String,
    networkAccess: NetworkAccessState,
    viewModel: DetectionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.downloadSelected()
        }
    LaunchedEffect(url, networkAccess.canUseInternet) {
        if (networkAccess.canUseInternet) viewModel.detect(url) else viewModel.setNetworkUnavailable()
    }
    DetectionScreen(
        uiState = uiState,
        networkAccess = networkAccess,
        onBack = viewModel::goBack,
        onRetry = viewModel::retry,
        onOpenWeb = viewModel::openInWeb,
        onToggleCandidate = viewModel::toggleCandidate,
        onSelectVariant = viewModel::selectVariant,
        onDownload = {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                viewModel.downloadSelected()
            } else {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun DetectionScreen(
    uiState: DetectionUiState,
    networkAccess: NetworkAccessState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenWeb: () -> Unit,
    onToggleCandidate: (String) -> Unit,
    onSelectVariant: (String, String) -> Unit,
    onDownload: () -> Unit,
) {
    AppScreen(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detection_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.detection_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            when (uiState) {
                DetectionUiState.Loading -> {
                    LoadingDetectionContent()
                }

                DetectionUiState.NetworkUnavailable -> {
                    NetworkUnavailableDetectionContent()
                }

                is DetectionUiState.Content -> {
                    DetectionCandidateContent(
                        state = uiState,
                        networkAccess = networkAccess,
                        onToggleCandidate = onToggleCandidate,
                        onSelectVariant = onSelectVariant,
                        onDownload = onDownload,
                    )
                }

                is DetectionUiState.Unsupported -> {
                    UnsupportedDetectionContent(uiState.reason, onRetry, onOpenWeb)
                }
            }
        }
    }
}

@Preview
@FormFactorPreviews
@Composable
private fun DetectionScreenPreview() {
    DambomTheme {
        DetectionScreen(
            uiState =
                DetectionUiState.Content(
                    pageTitle = "여행 기록",
                    candidates =
                        persistentListOf(
                            MediaCandidate(
                                id = "1",
                                url = "https://example.com/trip.mp4",
                                title = "trip",
                                mimeType = "video/mp4",
                                contentLength = 24_000_000,
                            ),
                        ),
                    selectedIds = persistentSetOf("1"),
                ),
            networkAccess = NetworkAccessState(NetworkConnection.UNMETERED),
            onBack = ::previewNoOp,
            onRetry = ::previewNoOp,
            onOpenWeb = ::previewNoOp,
            onToggleCandidate = ::previewNoOp,
            onSelectVariant = ::previewNoOp,
            onDownload = ::previewNoOp,
        )
    }
}
