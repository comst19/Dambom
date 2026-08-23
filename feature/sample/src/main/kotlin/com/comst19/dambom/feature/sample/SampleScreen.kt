package com.comst19.dambom.feature.sample

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comst19.dambom.core.common.ui.AppScreen
import com.comst19.dambom.core.common.ui.throttledClickable
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.core.designsystem.LoadingContent
import com.comst19.dambom.core.designsystem.MessageContent
import com.comst19.dambom.feature.sample.contract.SampleIntent
import com.comst19.dambom.feature.sample.contract.SampleState
import kotlinx.collections.immutable.persistentListOf

@Composable
internal fun SampleMvvmRoute(viewModel: SampleMvvmViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SampleScreen(
        state = state,
        onRefresh = viewModel::refresh,
        onItemClick = viewModel::onItemClick,
    )
}

@Composable
internal fun SampleMviRoute(viewModel: SampleMviViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SampleScreen(
        state = state,
        onRefresh = { viewModel.onIntent(SampleIntent.Refresh) },
        onItemClick = { viewModel.onIntent(SampleIntent.ClickItem(it)) },
    )
}

@Composable
internal fun SampleScreen(
    state: SampleState,
    onRefresh: () -> Unit,
    onItemClick: (Long) -> Unit,
) {
    AppScreen { innerPadding ->
        val standardContentModifier =
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)

        when {
            state.isLoading -> {
                LoadingContent(standardContentModifier)
            }

            state.errorMessage != null && state.items.isEmpty() -> {
                MessageContent(
                    message = state.errorMessage,
                    actionLabel = "Retry",
                    onAction = onRefresh,
                    modifier = standardContentModifier,
                )
            }

            state.items.isEmpty() -> {
                MessageContent(
                    message = "No samples",
                    actionLabel = "Refresh",
                    onAction = onRefresh,
                    modifier = standardContentModifier,
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().consumeWindowInsets(innerPadding),
                    contentPadding = innerPadding,
                ) {
                    item {
                        ListItem(
                            headlineContent = { Text("Scrollable screen") },
                            supportingContent = {
                                Text("The Scaffold background continues behind the transparent status bar.")
                            },
                        )
                    }
                    items(state.items, key = SampleUiModel::id) { item ->
                        ListItem(
                            headlineContent = { Text(item.title) },
                            supportingContent = { Text(item.description) },
                            modifier = Modifier.throttledClickable { onItemClick(item.id) },
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SampleScreenPreview() {
    DambomTheme {
        SampleScreen(
            state =
                SampleState(
                    items =
                        persistentListOf(
                            SampleUiModel(1, "Sample 1", "Offline-first sample data"),
                        ),
                ),
            onRefresh = {},
            onItemClick = {},
        )
    }
}
