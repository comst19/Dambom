package com.comst19.dambom.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackVisibilityStateTest {
    @Test
    fun `paused media stays paused after an ordinary stop`() {
        val state = PlaybackVisibilityState()

        state.onStopped(wasPlayWhenReady = false)

        assertEquals(PlaybackCommand.None, state.onStarted())
    }

    @Test
    fun `ordinary stop records one resume which start consumes`() {
        val state = PlaybackVisibilityState()

        assertEquals(PlaybackCommand.Pause, state.onStopped(wasPlayWhenReady = true))
        assertEquals(PlaybackCommand.Resume, state.onStarted())
        assertEquals(PlaybackCommand.None, state.onStarted())
    }

    @Test
    fun `PiP exit followed by resume keeps playback running`() {
        val state = PlaybackVisibilityState()

        assertEquals(PlaybackCommand.None, state.onPictureInPictureEntered())
        assertEquals(PlaybackCommand.None, state.onPictureInPictureExited())
        assertEquals(PlaybackCommand.None, state.onResumed())
    }

    @Test
    fun `PiP exit and resume leave a later ordinary stop pausable`() {
        val state = PlaybackVisibilityState()

        state.onPictureInPictureEntered()
        state.onPictureInPictureExited()
        state.onResumed()

        assertEquals(PlaybackCommand.Pause, state.onStopped(wasPlayWhenReady = true))
        assertEquals(PlaybackCommand.Resume, state.onStarted())
    }

    @Test
    fun `PiP entry does not pause on a following stop`() {
        val state = PlaybackVisibilityState()

        state.onPictureInPictureEntered()

        assertEquals(PlaybackCommand.None, state.onStopped(wasPlayWhenReady = true))
    }

    @Test
    fun `pause does not pause playback`() {
        val state = PlaybackVisibilityState()

        assertEquals(PlaybackCommand.None, state.onPaused())
    }

    @Test
    fun `PiP exit followed by stop pauses without scheduling a resume`() {
        val state = PlaybackVisibilityState()

        state.onPictureInPictureEntered()
        state.onPictureInPictureExited()

        assertEquals(PlaybackCommand.Pause, state.onStopped(wasPlayWhenReady = true))
        assertEquals(PlaybackCommand.None, state.onStarted())
    }

    @Test
    fun `PiP close reports stop before exit and pauses without scheduling a resume`() {
        val state = PlaybackVisibilityState()

        state.onPictureInPictureEntered()

        assertEquals(PlaybackCommand.None, state.onStopped(wasPlayWhenReady = true))
        assertEquals(PlaybackCommand.Pause, state.onPictureInPictureExited())
        assertEquals(PlaybackCommand.None, state.onStarted())
    }

    @Test
    fun `dispose clears a pending resume and pauses`() {
        val state = PlaybackVisibilityState()

        state.onStopped(wasPlayWhenReady = true)

        assertEquals(PlaybackCommand.Pause, state.onDisposed())
        assertEquals(PlaybackCommand.None, state.onStarted())
    }

    @Test
    fun `explicit media stop clears a pending resume`() {
        val state = PlaybackVisibilityState()

        state.onStopped(wasPlayWhenReady = true)
        state.onPlaybackStopped()

        assertEquals(PlaybackCommand.None, state.onStarted())
    }
}
