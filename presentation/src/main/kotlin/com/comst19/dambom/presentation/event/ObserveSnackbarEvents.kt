package com.comst19.dambom.presentation.event

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.comst19.dambom.core.common.ui.SnackbarDuration
import com.comst19.dambom.core.common.ui.SnackbarEvent
import com.comst19.dambom.core.common.ui.SnackbarEventBus
import com.comst19.dambom.core.common.ui.resolve
import androidx.compose.material3.SnackbarDuration as MaterialSnackbarDuration

@Composable
internal fun ObserveSnackbarEvents(
    eventBus: SnackbarEventBus,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    LaunchedEffect(eventBus, snackbarHostState) {
        eventBus.events.collect { event ->
            snackbarHostState.showSnackbar(
                message = event.message.resolve(context),
                duration = event.duration.toMaterialDuration(),
                withDismissAction = event.duration == SnackbarDuration.Indefinite,
            )
        }
    }
}

private fun SnackbarDuration.toMaterialDuration(): MaterialSnackbarDuration =
    when (this) {
        SnackbarDuration.Short -> MaterialSnackbarDuration.Short
        SnackbarDuration.Long -> MaterialSnackbarDuration.Long
        SnackbarDuration.Indefinite -> MaterialSnackbarDuration.Indefinite
    }
