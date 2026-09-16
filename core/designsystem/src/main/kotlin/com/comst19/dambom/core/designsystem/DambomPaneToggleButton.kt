package com.comst19.dambom.core.designsystem

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun DambomPaneToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    actionLabel: String,
    modifier: Modifier = Modifier,
) {
    DambomIconTooltip(actionLabel) {
        IconToggleButton(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            colors =
                IconButtonDefaults.iconToggleButtonColors(
                    checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
        ) {
            Icon(
                imageVector = if (checked) DualVideoPaneIcon else SinglePaneIcon,
                contentDescription = actionLabel,
            )
        }
    }
}
