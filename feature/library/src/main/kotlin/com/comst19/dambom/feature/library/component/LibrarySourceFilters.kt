package com.comst19.dambom.feature.library.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.comst19.dambom.feature.library.R
import com.comst19.dambom.feature.library.contract.LibrarySourceFilter

@Composable
internal fun LibrarySourceFilters(
    selected: LibrarySourceFilter,
    onSelected: (LibrarySourceFilter) -> Unit,
    favoritesOnly: Boolean,
    onFavoritesOnlyChange: (Boolean) -> Unit,
) {
    FlowRow(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = LibraryHorizontalPadding, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SourceChips(selected, onSelected)
        FavoritesFilterButton(favoritesOnly, onFavoritesOnlyChange)
    }
}

@Composable
private fun SourceChips(
    selected: LibrarySourceFilter,
    onSelected: (LibrarySourceFilter) -> Unit,
) {
    LibrarySourceFilter.entries.forEach { filter ->
        FilterChip(
            selected = selected == filter,
            onClick = { onSelected(filter) },
            label = {
                Text(
                    stringResource(
                        when (filter) {
                            LibrarySourceFilter.ALL -> R.string.library_filter_all
                            LibrarySourceFilter.X -> R.string.library_filter_x
                            LibrarySourceFilter.WEB -> R.string.library_filter_web
                        },
                    ),
                )
            },
        )
    }
}

@Composable
private fun FavoritesFilterButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val label =
        stringResource(
            if (checked) R.string.library_favorites_filter_off else R.string.library_favorites_filter_on,
        )
    FilterChip(
        selected = checked,
        onClick = { onCheckedChange(!checked) },
        modifier = Modifier.semantics { contentDescription = label },
        label = { Text(stringResource(R.string.library_favorites)) },
        leadingIcon = {
            Icon(
                if (checked) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        },
        colors =
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
    )
}
