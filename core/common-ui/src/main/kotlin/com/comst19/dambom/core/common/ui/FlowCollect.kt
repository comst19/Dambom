package com.comst19.dambom.core.common.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import kotlinx.coroutines.flow.Flow

@Composable
fun <T> CollectEffect(
    flow: Flow<T>,
    onEffect: suspend (T) -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnEffect by rememberUpdatedState(onEffect)
    LaunchedEffect(flow, lifecycle) {
        flow
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .collect { effect -> currentOnEffect(effect) }
    }
}
