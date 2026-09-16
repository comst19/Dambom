@file:Suppress("MagicNumber")

package com.comst19.dambom.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal val SinglePaneIcon = paneLayoutIcon(expanded = false)
internal val DualVideoPaneIcon = paneLayoutIcon(expanded = true)

private fun paneLayoutIcon(expanded: Boolean): ImageVector =
    ImageVector
        .Builder(
            name = if (expanded) "DualVideoPane" else "SinglePane",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = true,
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4f, 4f)
                horizontalLineTo(20f)
                curveTo(21.1f, 4f, 22f, 4.9f, 22f, 6f)
                verticalLineTo(18f)
                curveTo(22f, 19.1f, 21.1f, 20f, 20f, 20f)
                horizontalLineTo(4f)
                curveTo(2.9f, 20f, 2f, 19.1f, 2f, 18f)
                verticalLineTo(6f)
                curveTo(2f, 4.9f, 2.9f, 4f, 4f, 4f)
                close()
                if (expanded) {
                    moveTo(10f, 4f)
                    verticalLineTo(20f)
                }
                moveTo(5f, 9f)
                horizontalLineTo(if (expanded) 7f else 18f)
                moveTo(5f, 13f)
                horizontalLineTo(if (expanded) 7f else 14f)
            }
            if (expanded) {
                path(fill = SolidColor(Color.Black)) {
                    moveTo(14f, 8f)
                    lineTo(19f, 12f)
                    lineTo(14f, 16f)
                    close()
                }
            }
        }.build()
