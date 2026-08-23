package com.comst19.dambom.core.designsystem

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview(
    name = "Dambom Theme - Light",
    uiMode = Configuration.UI_MODE_NIGHT_NO,
    heightDp = 760,
)
@Composable
private fun DambomLightThemePreview() {
    DambomTheme(darkTheme = false) { DambomThemePreviewContent() }
}

@Preview(
    name = "Dambom Theme - Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    heightDp = 760,
)
@Composable
private fun DambomDarkThemePreview() {
    DambomTheme(darkTheme = true) { DambomThemePreviewContent() }
}

@Composable
private fun DambomThemePreviewContent() {
    val colors = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colors.background,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Dambom Theme", style = MaterialTheme.typography.headlineSmall)
            Text("Body text", color = colors.onBackground)
            Text("Supporting text", color = colors.onSurfaceVariant)
            Button(onClick = {}) { Text("Primary action") }
            ThemeColorContainer(
                color = colors.primaryContainer,
                contentColor = colors.onPrimaryContainer,
                text = "Primary container",
            )
            ThemeColorContainer(
                color = colors.secondaryContainer,
                contentColor = colors.onSecondaryContainer,
                text = "Secondary container",
            )
            ThemeColorContainer(
                color = colors.tertiaryContainer,
                contentColor = colors.onTertiaryContainer,
                text = "Tertiary container",
            )
            DambomCard(modifier = Modifier.fillMaxWidth()) {
                Text("Surface container card")
            }
            HorizontalDivider(color = colors.outline)
            Text("Error color", color = colors.error)
        }
    }
}

@Composable
private fun ThemeColorContainer(
    color: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    text: String,
) {
    Text(
        text = text,
        color = contentColor,
        modifier =
            Modifier
                .fillMaxWidth()
                .background(color)
                .padding(12.dp),
    )
}
