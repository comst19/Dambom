package com.comst19.dambom.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.comst19.dambom.core.common.ui.AppScreen
import com.comst19.dambom.core.common.ui.AppScreenDefaults

@Composable
internal fun HelpRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    HelpScreen(onBack = viewModel::goBack)
}

@Composable
private fun HelpScreen(onBack: () -> Unit) {
    SettingsSubScreen(
        title = stringResource(R.string.settings_help),
        onBack = onBack,
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.help_intro),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            items(
                items = HELP_STEPS,
                key = HelpStep::titleRes,
            ) { step ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = stringResource(step.titleRes),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(step.descriptionRes),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SettingsSubScreen(
    title: String,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    AppScreen(
        maxWidth = AppScreenDefaults.SinglePaneMaxWidth,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        content(
            PaddingValues(
                start = 24.dp,
                top = innerPadding.calculateTopPadding(),
                end = 24.dp,
                bottom = innerPadding.calculateBottomPadding() + 32.dp,
            ),
        )
    }
}

private data class HelpStep(
    val titleRes: Int,
    val descriptionRes: Int,
)

private val HELP_STEPS =
    listOf(
        HelpStep(R.string.help_step_url_title, R.string.help_step_url_description),
        HelpStep(R.string.help_step_share_title, R.string.help_step_share_description),
        HelpStep(R.string.help_step_web_title, R.string.help_step_web_description),
        HelpStep(R.string.help_step_download_title, R.string.help_step_download_description),
        HelpStep(R.string.help_step_library_title, R.string.help_step_library_description),
    )
