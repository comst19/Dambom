package com.comst19.dambom.feature.library.component

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.comst19.dambom.feature.library.R

@Composable
internal fun actionMenuViewport(scroll: ScrollState): Modifier {
    val maxHeight = (LocalConfiguration.current.screenHeightDp.dp * ACTION_MENU_HEIGHT_FRACTION).coerceAtMost(360.dp)
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return Modifier.heightIn(max = maxHeight).drawWithContent {
        drawContent()
        if (scroll.maxValue > 0 && scroll.viewportSize > 0) {
            val inset = 8.dp.toPx()
            val trackHeight = (size.height - inset * 2).coerceAtLeast(0f)
            val contentHeight = scroll.viewportSize.toFloat() + scroll.maxValue
            val thumbHeight =
                (trackHeight * scroll.viewportSize / contentHeight)
                    .coerceAtLeast(24.dp.toPx())
                    .coerceAtMost(trackHeight)
            val top = inset + (trackHeight - thumbHeight) * scroll.value / scroll.maxValue
            val width = 3.dp.toPx()
            val left = if (rtl) 3.dp.toPx() else size.width - width - 3.dp.toPx()
            drawRoundRect(
                color = color.copy(alpha = ACTION_MENU_SCROLL_ALPHA),
                topLeft = Offset(left, top),
                size = Size(width, thumbHeight),
                cornerRadius = CornerRadius(width / 2),
            )
        }
    }
}

@Preview(name = "Phone menu", widthDp = 260, heightDp = 440, locale = "ko")
@Preview(name = "Short window menu", widthDp = 300, heightDp = 300, locale = "ko")
@Composable
private fun VideoActionMenuPreview() {
    val scroll = rememberScrollState()
    val viewport = actionMenuViewport(scroll)
    MaterialTheme {
        Box {
            Surface(Modifier.width(240.dp)) {
                Column(viewport.verticalScroll(scroll)) {
                    listOf(
                        R.string.subtitle_title to Icons.Outlined.Translate,
                        R.string.compress_title to Icons.Outlined.Compress,
                        R.string.trim_title to Icons.Outlined.ContentCut,
                        R.string.library_rename to Icons.Outlined.Edit,
                        R.string.library_share_video to Icons.Outlined.Share,
                        R.string.library_share_link to Icons.Outlined.Share,
                        R.string.library_copy_link to Icons.Outlined.Link,
                        R.string.player_open_website to Icons.AutoMirrored.Outlined.OpenInNew,
                        R.string.library_export to Icons.Outlined.Download,
                        R.string.library_delete to Icons.Outlined.DeleteOutline,
                    ).forEach { (label, icon) ->
                        DropdownMenuItem(
                            text = { Text(stringResource(label)) },
                            leadingIcon = { Icon(icon, null) },
                            onClick = {},
                        )
                    }
                }
            }
        }
    }
}

private const val ACTION_MENU_HEIGHT_FRACTION = 0.55f
private const val ACTION_MENU_SCROLL_ALPHA = 0.6f
