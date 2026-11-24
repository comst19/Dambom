package com.comst19.dambom.feature.library.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

@Stable
internal class PlayerInteractionState(
    scale: Float = 1f,
    panX: Float = 0f,
    panY: Float = 0f,
    locked: Boolean = false,
    subtitlesEnabled: Boolean = true,
) {
    var scale by mutableFloatStateOf(scale)
        private set
    var panX by mutableFloatStateOf(panX)
        private set
    var panY by mutableFloatStateOf(panY)
        private set
    var locked by mutableStateOf(locked)
    var subtitlesEnabled by mutableStateOf(subtitlesEnabled)
    var hasSubtitles by mutableStateOf(false)

    fun resetZoom() {
        scale = 1f
        panX = 0f
        panY = 0f
    }

    fun transform(
        zoom: Float,
        pan: Offset,
        width: Float,
        height: Float,
    ) {
        if (locked) return
        scale = (scale * zoom).coerceIn(1f, MAX_PLAYER_ZOOM)
        panX += pan.x
        panY += pan.y
        updateViewport(width, height)
    }

    fun updateViewport(
        width: Float,
        height: Float,
    ) {
        val horizontalLimit = width * (scale - 1f) / 2f
        val verticalLimit = height * (scale - 1f) / 2f
        panX = panX.coerceIn(-horizontalLimit, horizontalLimit)
        panY = panY.coerceIn(-verticalLimit, verticalLimit)
    }

    companion object {
        val Saver =
            listSaver<PlayerInteractionState, Any>(
                save = { listOf(it.scale, it.panX, it.panY, it.locked, it.subtitlesEnabled) },
                restore = {
                    PlayerInteractionState(
                        it[0] as Float,
                        it[1] as Float,
                        it[2] as Float,
                        it[3] as Boolean,
                        it.getOrNull(SUBTITLE_VISIBILITY_INDEX) as? Boolean ?: true,
                    )
                },
            )
    }
}

private const val MAX_PLAYER_ZOOM = 4f
private const val SUBTITLE_VISIBILITY_INDEX = 4
