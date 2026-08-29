package com.comst19.dambom.core.designsystem

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun DambomButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = DambomShapes.Control,
    ) {
        Text(text)
    }
}

@Preview
@Composable
private fun DambomButtonPreview() {
    PreviewTheme {
        DambomButton(
            text = "Continue",
            onClick = ::previewNoOp,
        )
    }
}
