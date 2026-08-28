package com.comst19.dambom.feature.library.component

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay

internal fun Modifier.fullscreenPlayerGestures(
    toggleControlsLabel: String,
    onToggleControls: () -> Unit,
    onContentModeChanged: (FullscreenContentMode) -> Unit,
): Modifier =
    semantics {
        onClick(label = toggleControlsLabel) {
            onToggleControls()
            true
        }
    }.pointerInput(onToggleControls, onContentModeChanged) {
        awaitEachGesture {
            val firstDown = awaitFirstDown(requireUnconsumed = false)
            val initialPositions = mutableMapOf<PointerId, androidx.compose.ui.geometry.Offset>()
            initialPositions[firstDown.id] = firstDown.position
            val initialUptimeMillis = firstDown.uptimeMillis
            var initialDistance: Float? = null
            var contentModeChange: FullscreenContentMode? = null
            var hadMultiplePointers = false
            var movedBeyondTouchSlop = false
            var consumed = firstDown.isConsumed
            var released = false
            var durationMillis = 0L
            do {
                val event = awaitPointerEvent()
                durationMillis = event.changes.maxOfOrNull { it.uptimeMillis - initialUptimeMillis } ?: durationMillis
                val pressedPointers = event.changes.filter { it.pressed }
                hadMultiplePointers = hadMultiplePointers || pressedPointers.size > 1
                consumed = consumed || event.changes.any { it.isConsumed }
                event.changes.filter { it.pressed }.forEach { change ->
                    val initialPosition = initialPositions.getOrPut(change.id) { change.position }
                    if ((change.position - initialPosition).getDistance() > viewConfiguration.touchSlop) movedBeyondTouchSlop = true
                }
                if (pressedPointers.size >= 2 && contentModeChange == null) {
                    val distance = (pressedPointers[0].position - pressedPointers[1].position).getDistance()
                    val startDistance = initialDistance ?: distance.also { initialDistance = it }
                    contentModeChange =
                        when {
                            distance - startDistance > viewConfiguration.touchSlop -> FullscreenContentMode.ExpandedCrop
                            startDistance - distance > viewConfiguration.touchSlop -> FullscreenContentMode.Fit
                            else -> null
                        }
                }
                released = event.changes.any { it.changedToUp() }
            } while (event.changes.any { it.pressed })

            when {
                contentModeChange != null -> onContentModeChanged(contentModeChange)

                !hadMultiplePointers && !movedBeyondTouchSlop && !consumed && released &&
                    durationMillis < viewConfiguration.longPressTimeoutMillis -> onToggleControls()
            }
        }
    }

internal enum class FullscreenContentMode(
    val contentScale: ContentScale,
) {
    Fit(ContentScale.Fit),
    ExpandedCrop(ContentScale.Crop),
    ;

    fun toggled(): FullscreenContentMode = if (this == Fit) ExpandedCrop else Fit
}

internal fun fullscreenContentModeFor(
    selected: FullscreenContentMode,
    isPipContentOnly: Boolean,
): FullscreenContentMode = if (isPipContentOnly) FullscreenContentMode.Fit else selected

internal fun shouldAutoHideFullscreenControls(
    controlsVisible: Boolean,
    isPlaying: Boolean,
    isControlsInteracting: Boolean,
): Boolean = controlsVisible && isPlaying && !isControlsInteracting

@Composable
internal fun rememberFullscreenControlsEntryEligible(
    deferOnEntry: Boolean,
    isPipContentOnly: Boolean,
): Boolean {
    var eligible by remember(deferOnEntry, isPipContentOnly) { mutableStateOf(!deferOnEntry && !isPipContentOnly) }
    LaunchedEffect(deferOnEntry, isPipContentOnly) {
        eligible = !deferOnEntry && !isPipContentOnly
        if (deferOnEntry && !isPipContentOnly) {
            delay(FULLSCREEN_CONTROLS_ENTRY_DELAY_MILLIS)
            eligible = true
        }
    }
    return eligible
}

internal const val FULLSCREEN_CONTROLS_ENTRY_DELAY_MILLIS = 750L
