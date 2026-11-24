package com.comst19.dambom.feature.web.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.comst19.dambom.core.designsystem.DambomShapes
import com.comst19.dambom.feature.web.R
import com.comst19.dambom.feature.web.contract.RecentPage
import kotlinx.collections.immutable.PersistentList

@Composable
internal fun WebStartPage(
    address: String,
    recentPages: PersistentList<RecentPage>,
    onNavigate: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(stringResource(R.string.web_start_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.web_start_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onNavigate(address) },
            enabled = address.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            shape = DambomShapes.Control,
        ) {
            Text(stringResource(R.string.web_open))
        }
        Spacer(Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.web_recent),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(12.dp))
        if (recentPages.isEmpty()) {
            Text(
                stringResource(R.string.web_recent_empty),
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            recentPages.forEach { page -> RecentPageRow(page = page, onClick = { onNavigate(page.url) }) }
        }
    }
}

@Composable
private fun RecentPageRow(
    page: RecentPage,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
    ) {
        Text(page.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            page.url,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
