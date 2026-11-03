package com.comst19.dambom.core.designsystem

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun DambomButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = DambomShapes.Control,
        content = content,
    )
}

@Preview
@Composable
private fun DambomButtonPreview() {
    PreviewTheme {
        DambomButton(
            onClick = ::previewNoOp,
        ) {
            Text("Continue")
        }
    }
}
