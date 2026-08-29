package com.comst19.dambom.presentation.event

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.comst19.dambom.core.common.ui.AppEvent
import com.comst19.dambom.core.common.ui.AppEventBus
import com.comst19.dambom.core.common.ui.SnackbarDuration
import com.comst19.dambom.core.common.ui.resolve
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.material3.SnackbarDuration as MaterialSnackbarDuration

@Composable
internal fun ObserveAppEvents(
    eventBus: AppEventBus,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    LaunchedEffect(eventBus, snackbarHostState) {
        eventBus.events.collectLatest { event ->
            when (event) {
                is AppEvent.ShowSnackbar -> {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(
                        message = event.message.resolve(context),
                        duration = event.duration.toMaterialDuration(),
                        withDismissAction = event.duration == SnackbarDuration.Indefinite,
                    )
                }
            }
        }
    }
}

private fun SnackbarDuration.toMaterialDuration(): MaterialSnackbarDuration =
    when (this) {
        SnackbarDuration.Short -> MaterialSnackbarDuration.Short
        SnackbarDuration.Long -> MaterialSnackbarDuration.Long
        SnackbarDuration.Indefinite -> MaterialSnackbarDuration.Indefinite
    }
