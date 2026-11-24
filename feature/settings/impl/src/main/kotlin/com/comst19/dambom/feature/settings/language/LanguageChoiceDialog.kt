package com.comst19.dambom.feature.settings.language

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.comst19.dambom.core.common.locale.AppLanguage
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.feature.settings.R

@Composable
internal fun LanguageChoiceDialog(
    selected: AppLanguage,
    onDismiss: () -> Unit,
    onSelect: (AppLanguage) -> Unit,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = AppLanguage.entries.indexOf(selected))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_language)) },
        text = {
            Box {
                LazyColumn(
                    modifier =
                        Modifier
                            .heightIn(max = 320.dp)
                            .fillMaxWidth()
                            .padding(end = 16.dp)
                            .selectableGroup(),
                    state = listState,
                ) {
                    items(AppLanguage.entries, key = { it.languageTag }) { language ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = language == selected,
                                        role = Role.RadioButton,
                                        onClick = { onSelect(language) },
                                    ).heightIn(min = 56.dp)
                                    .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = language == selected, onClick = null)
                            Text(stringResource(language.labelRes))
                        }
                    }
                }
                LanguageScrollIndicator(listState, Modifier.matchParentSize())
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}

@Composable
private fun LanguageScrollIndicator(
    state: LazyListState,
    modifier: Modifier = Modifier,
) {
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f)
    val thumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    Canvas(modifier) {
        val indicator = state.scrollIndicatorState ?: return@Canvas
        val content = indicator.contentSize.toFloat()
        val viewport = indicator.viewportSize.toFloat()
        if (content <= viewport || viewport <= 0f) return@Canvas
        val width = 4.dp.toPx()
        val x = if (layoutDirection == LayoutDirection.Rtl) 0f else size.width - width
        val thumbHeight =
            (size.height * viewport / content)
                .coerceIn(24.dp.toPx().coerceAtMost(size.height), size.height)
        val progress = (indicator.scrollOffset / (content - viewport)).coerceIn(0f, 1f)
        val radius = CornerRadius(width / 2)
        drawRoundRect(trackColor, Offset(x, 0f), Size(width, size.height), radius)
        drawRoundRect(thumbColor, Offset(x, (size.height - thumbHeight) * progress), Size(width, thumbHeight), radius)
    }
}

@Preview(name = "English", locale = "en", widthDp = 320, heightDp = 640)
@Preview(name = "Arabic", locale = "ar", widthDp = 320, heightDp = 640)
@Preview(name = "French", locale = "fr", widthDp = 320, heightDp = 640)
@Preview(name = "Large text", locale = "en", fontScale = 1.5f, widthDp = 320, heightDp = 640)
@Preview(name = "Compact", locale = "en", widthDp = 640, heightDp = 320)
@Composable
private fun LanguageChoiceDialogPreview() {
    DambomTheme {
        LanguageChoiceDialog(selected = AppLanguage.SYSTEM, onDismiss = {}, onSelect = {})
    }
}

@Preview(name = "Selected last", locale = "en", widthDp = 320, heightDp = 640)
@Composable
private fun LastLanguageChoiceDialogPreview() {
    DambomTheme {
        LanguageChoiceDialog(selected = AppLanguage.RUSSIAN, onDismiss = {}, onSelect = {})
    }
}
