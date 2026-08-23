package com.comst19.dambom.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val ColorScheme.sampleDetailBackground: Color
    get() = primaryContainer

@Composable
fun DambomTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme =
        if (darkTheme) {
            DambomDarkColorScheme
        } else {
            DambomLightColorScheme
        }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

@Composable
fun PreviewTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    DambomTheme(
        darkTheme = darkTheme,
        content = content,
    )
}
