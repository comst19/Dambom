package com.comst19.dambom.feature.library.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.comst19.dambom.feature.library.R
import com.comst19.dambom.feature.library.VideoSourceKind
import com.comst19.dambom.feature.library.videoSourcePresentation

@Immutable
internal data class LibraryVideoItemStyle(
    val shape: Shape,
    val containerColor: Color,
    val contentColor: Color,
    val metadataColor: Color,
    val sourceBadgeContainerColor: Color,
    val sourceBadgeContentColor: Color,
    val border: BorderStroke?,
    val sourceLabel: String,
    val sourceHost: String,
    val sourceDescription: String,
)

@Composable
internal fun libraryVideoItemStyle(
    sourcePageUrl: String,
    selected: Boolean,
): LibraryVideoItemStyle {
    val source = videoSourcePresentation(sourcePageUrl)
    val isXSource = source.kind == VideoSourceKind.X
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < DARK_THEME_LUMINANCE_THRESHOLD
    val contentColor =
        when {
            isXSource -> X_CARD_CONTENT
            isDarkTheme -> WEB_DARK_CONTENT
            else -> WEB_LIGHT_CONTENT
        }
    return LibraryVideoItemStyle(
        shape = RoundedCornerShape(if (isXSource) 8.dp else 16.dp),
        containerColor =
            when {
                isXSource -> X_CARD_CONTAINER
                isDarkTheme -> WEB_DARK_CONTAINER
                else -> WEB_LIGHT_CONTAINER
            },
        contentColor = contentColor,
        metadataColor = contentColor.copy(alpha = 0.72f),
        sourceBadgeContainerColor =
            when {
                isXSource -> X_CARD_BADGE
                isDarkTheme -> WEB_DARK_BADGE
                else -> WEB_LIGHT_BADGE
            },
        sourceBadgeContentColor =
            when {
                isXSource -> X_CARD_CONTENT
                isDarkTheme -> WEB_DARK_BADGE_CONTENT
                else -> WEB_LIGHT_BADGE_CONTENT
            },
        border =
            when {
                selected -> BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
                isXSource -> BorderStroke(2.dp, X_CARD_BORDER)
                isDarkTheme -> BorderStroke(1.dp, WEB_DARK_BORDER)
                else -> BorderStroke(1.dp, WEB_LIGHT_BORDER)
            },
        sourceLabel =
            stringResource(
                if (isXSource) R.string.library_source_x_label else R.string.library_source_website_label,
            ),
        sourceHost = source.host,
        sourceDescription =
            if (isXSource) {
                stringResource(R.string.library_source_x_accessibility)
            } else {
                stringResource(R.string.library_source_website_accessibility, source.host)
            },
    )
}

private val X_CARD_CONTAINER = Color(0xFF16181C)
private val X_CARD_CONTENT = Color(0xFFF2F2F2)
private val X_CARD_BADGE = Color(0xFF34373C)
private val X_CARD_BORDER = Color(0xFF5C6068)
private val WEB_LIGHT_CONTAINER = Color(0xFFEAF2FF)
private val WEB_LIGHT_CONTENT = Color(0xFF17233A)
private val WEB_LIGHT_BADGE = Color(0xFFD4E4FF)
private val WEB_LIGHT_BADGE_CONTENT = Color(0xFF134A8E)
private val WEB_LIGHT_BORDER = Color(0xFF9AB9E6)
private val WEB_DARK_CONTAINER = Color(0xFF1E314A)
private val WEB_DARK_CONTENT = Color(0xFFE6EEF9)
private val WEB_DARK_BADGE = Color(0xFF2C4B70)
private val WEB_DARK_BADGE_CONTENT = Color(0xFFD6E8FF)
private val WEB_DARK_BORDER = Color(0xFF587AA6)
private const val DARK_THEME_LUMINANCE_THRESHOLD = 0.5f
