package com.comst19.dambom.feature.settings.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.comst19.dambom.core.domain.model.SharedLinkQuality
import com.comst19.dambom.feature.settings.R
import kotlinx.collections.immutable.toPersistentList

@Composable
internal fun SharedDownloadQualityPreference(
    state: SettingsContentState,
    actions: DownloadSettingsActions,
) {
    var showQuality by remember { mutableStateOf(false) }
    var showResolution by remember { mutableStateOf(false) }
    SettingsRow(
        icon = Icons.Outlined.HighQuality,
        title = stringResource(R.string.settings_shared_quality),
        subtitle = stringResource(state.sharedLinkQuality.labelRes),
        onClick = { showQuality = true },
    )
    if (state.sharedLinkQuality == SharedLinkQuality.PREFERRED_RESOLUTION) {
        SettingsRow(
            icon = Icons.Outlined.HighQuality,
            title = "${stringResource(R.string.settings_shared_resolution)}: ${state.sharedLinkResolution}p",
            subtitle = stringResource(R.string.settings_shared_fallback),
            onClick = { showResolution = true },
        )
    }
    if (showQuality) {
        SettingsChoiceDialog(
            title = stringResource(R.string.settings_shared_quality),
            entries = SharedLinkQuality.entries.toPersistentList(),
            selected = state.sharedLinkQuality,
            label = { stringResource(it.labelRes) },
            onDismiss = { showQuality = false },
            onSelect = {
                showQuality = false
                actions.onSharedLinkQualityChange(it, state.sharedLinkResolution)
            },
        )
    }
    if (showResolution) {
        SettingsChoiceDialog(
            title = stringResource(R.string.settings_shared_resolution),
            entries = SHARED_RESOLUTIONS,
            selected = state.sharedLinkResolution,
            label = { "${it}p" },
            onDismiss = { showResolution = false },
            onSelect = {
                showResolution = false
                actions.onSharedLinkQualityChange(state.sharedLinkQuality, it)
            },
        )
    }
}

private val SharedLinkQuality.labelRes: Int
    get() =
        when (this) {
            SharedLinkQuality.HIGHEST -> R.string.settings_shared_highest
            SharedLinkQuality.LOWEST -> R.string.settings_shared_lowest
            SharedLinkQuality.PREFERRED_RESOLUTION -> R.string.settings_shared_preferred
        }

@Suppress("MagicNumber")
private val SHARED_RESOLUTIONS = listOf(240, 360, 480, 720, 1080, 1440, 2160).toPersistentList()
