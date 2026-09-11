package com.comst19.dambom.feature.home.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.comst19.dambom.core.common.ui.currentAdaptiveLayoutInfo
import com.comst19.dambom.core.designsystem.DambomPaneToggleButton
import com.comst19.dambom.feature.home.R

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun HomeHeader(
    isResultPaneVisible: Boolean,
    onResultPaneVisibilityChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.home_brand),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        actions = {
            if (currentAdaptiveLayoutInfo().supportsMultiplePanes) {
                val label = if (isResultPaneVisible) R.string.home_hide_results else R.string.home_show_results
                DambomPaneToggleButton(
                    checked = isResultPaneVisible,
                    onCheckedChange = onResultPaneVisibilityChange,
                    actionLabel = stringResource(label),
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.home_open_settings),
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(titleContentColor = MaterialTheme.colorScheme.primary),
    )
}
