package com.comst19.dambom.feature.library.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

@Stable
internal class PlayerControlsState {
    var isVisible by mutableStateOf(true)
        private set
    var isInteracting by mutableStateOf(false)
        private set
    var interactionRevision by mutableIntStateOf(0)
        private set

    fun toggle() {
        isVisible = !isVisible
    }

    fun onInteraction() {
        isVisible = true
        interactionRevision++
    }

    fun onInteractionChanged(interacting: Boolean) {
        isInteracting = interacting
    }

    fun hide() {
        isVisible = false
    }
}

@Composable
internal fun rememberPlayerControlsState(
    mediaId: String,
    isPlaying: Boolean,
): PlayerControlsState {
    val state = remember(mediaId) { PlayerControlsState() }
    LaunchedEffect(state, state.isVisible, isPlaying, state.isInteracting, state.interactionRevision) {
        if (shouldAutoHidePlayerControls(state.isVisible, isPlaying, state.isInteracting)) {
            delay(CONTROLS_AUTO_HIDE_MILLIS)
            state.hide()
        }
    }
    return state
}

internal fun shouldAutoHidePlayerControls(
    controlsVisible: Boolean,
    isPlaying: Boolean,
    isControlsInteracting: Boolean,
): Boolean = controlsVisible && isPlaying && !isControlsInteracting

private const val CONTROLS_AUTO_HIDE_MILLIS = 3_000L
