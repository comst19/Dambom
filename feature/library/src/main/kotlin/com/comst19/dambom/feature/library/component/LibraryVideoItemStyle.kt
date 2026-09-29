package com.comst19.dambom.feature.library.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import com.comst19.dambom.core.designsystem.DambomShapes
import com.comst19.dambom.feature.library.R
import com.comst19.dambom.feature.library.VideoSourceKind
import com.comst19.dambom.feature.library.videoSourcePresentation

@Immutable
internal data class LibraryVideoItemStyle(
    val shape: Shape,
    val containerColor: Color,
    val contentColor: Color,
    val metadataColor: Color,
    val sourceLabel: String,
    val sourceHost: String?,
    val sourceDescription: String,
)

@Composable
internal fun libraryVideoItemStyle(
    sourcePageUrl: String,
    selected: Boolean,
): LibraryVideoItemStyle {
    val source = videoSourcePresentation(sourcePageUrl)
    val isXSource = source.kind == VideoSourceKind.X
    return LibraryVideoItemStyle(
        shape = DambomShapes.Media,
        containerColor =
            if (selected) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        metadataColor = MaterialTheme.colorScheme.onSurfaceVariant,
        sourceLabel =
            stringResource(
                if (isXSource) R.string.library_source_x_label else R.string.library_source_website_label,
            ),
        sourceHost = source.host,
        sourceDescription =
            if (isXSource) {
                stringResource(R.string.library_source_x_accessibility)
            } else {
                stringResource(
                    R.string.library_source_website_accessibility,
                    source.host ?: stringResource(R.string.library_source_website_label),
                )
            },
    )
}
