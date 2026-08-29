package com.comst19.dambom.feature.library.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.comst19.dambom.feature.library.R
import com.comst19.dambom.feature.library.contract.LibrarySourceFilter

@Composable
internal fun LibrarySourceFilters(
    selected: LibrarySourceFilter,
    onSelected: (LibrarySourceFilter) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
}
