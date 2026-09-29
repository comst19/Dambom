package com.comst19.dambom.feature.library.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.comst19.dambom.core.designsystem.DambomIconTooltip
import com.comst19.dambom.feature.library.R
import com.comst19.dambom.feature.library.contract.LibrarySourceFilter

@Composable
internal fun LibrarySourceFilters(
    selected: LibrarySourceFilter,
    onSelected: (LibrarySourceFilter) -> Unit,
    favoritesOnly: Boolean,
    onFavoritesOnlyChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = LibraryHorizontalPadding, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SourceChips(selected, onSelected)
        }
        VerticalDivider(Modifier.height(20.dp))
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
    DambomIconTooltip(label) {
        IconToggleButton(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors =
                IconButtonDefaults.iconToggleButtonColors(
                    checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
        ) {
            Icon(if (checked) Icons.Filled.Star else Icons.Outlined.StarBorder, contentDescription = label)
        }
    }
}
