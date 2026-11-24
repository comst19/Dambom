package com.comst19.dambom.feature.library

import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.geometry.Offset
import com.comst19.dambom.feature.library.player.PlayerInteractionState
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerInteractionStateTest {
    @Test
    fun `subtitle visibility survives saver restoration and old state defaults to visible`() {
        val scope =
            object : SaverScope {
                override fun canBeSaved(value: Any) = true
            }
        val state = PlayerInteractionState(subtitlesEnabled = false).apply { hasSubtitles = true }
        val saved = with(PlayerInteractionState.Saver) { scope.save(state) }
        val restored = checkNotNull(PlayerInteractionState.Saver.restore(checkNotNull(saved)))
        assertEquals(false, restored.subtitlesEnabled)
        assertEquals(false, restored.hasSubtitles)
        val legacy = checkNotNull(PlayerInteractionState.Saver.restore(listOf(1f, 0f, 0f, false)))
        assertEquals(true, legacy.subtitlesEnabled)
    }

    @Test
    fun `smaller viewport clamps saved pan while keeping zoom and lock`() {
        val state = PlayerInteractionState(scale = 2f, panX = 300f, panY = -200f, locked = true)

        state.updateViewport(200f, 100f)

        assertEquals(100f, state.panX, 0.001f)
        assertEquals(-50f, state.panY, 0.001f)
        assertEquals(2f, state.scale, 0.001f)
        assertEquals(true, state.locked)
    }

    @Test
    fun `pinch zoom and pan are bounded and returning to fit resets pan`() {
        val state = PlayerInteractionState()
        state.transform(2f, Offset(500f, -500f), 200f, 100f)
        assertEquals(2f, state.scale, 0.001f)
        assertEquals(100f, state.panX, 0.001f)
        assertEquals(-50f, state.panY)
        state.transform(0.1f, Offset.Zero, 200f, 100f)
        assertEquals(1f, state.scale, 0.001f)
        assertEquals(0f, state.panX, 0.001f)
        assertEquals(0f, state.panY, 0.001f)
    }

    @Test
    fun `lock prevents zoom and pan`() {
        val state = PlayerInteractionState(locked = true)
        state.transform(4f, Offset(100f, 100f), 200f, 100f)
        assertEquals(1f, state.scale, 0.001f)
        assertEquals(0f, state.panX, 0.001f)
    }

    @Test
    fun `zoom never exceeds four times`() {
        val state = PlayerInteractionState()
        state.transform(100f, Offset.Zero, 200f, 100f)
        assertEquals(4f, state.scale, 0.001f)
    }
}
