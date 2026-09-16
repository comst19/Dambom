package com.comst19.dambom.feature.web.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comst19.dambom.core.designsystem.DambomShapes
import com.comst19.dambom.feature.web.R
import com.comst19.dambom.feature.web.contract.WebTab
import com.comst19.dambom.feature.web.contract.WebUiState

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun WebTabsSheet(
    state: WebUiState,
    onDismiss: () -> Unit,
    onCreateTab: () -> Unit,
    onSelectTab: (Long) -> Unit,
    onCloseTab: (Long) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                pluralStringResource(R.plurals.web_tabs_title, state.tabs.size, state.tabs.size),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
            )
            IconButton(onClick = onCreateTab, enabled = state.canCreateTab) {
                Icon(Icons.Outlined.Add, stringResource(R.string.web_new_tab))
            }
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            items(state.tabs, key = WebTab::id) { tab ->
                val selected = tab.id == state.currentTabId
                val title = tab.title.ifBlank { stringResource(R.string.web_new_tab) }
                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .selectable(
                                selected = selected,
                                role = Role.Tab,
                                onClick = { onSelectTab(tab.id) },
                            ),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                    shape = DambomShapes.Control,
                ) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                tab.url ?: stringResource(R.string.web_empty_tab),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = { onCloseTab(tab.id) }) {
                            Icon(Icons.Outlined.Close, stringResource(R.string.web_close_tab, title))
                        }
                    }
                }
            }
        }
    }
}
