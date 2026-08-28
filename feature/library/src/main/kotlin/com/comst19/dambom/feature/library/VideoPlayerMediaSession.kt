package com.comst19.dambom.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.Player
import androidx.media3.session.MediaSession

@Composable
internal fun VideoPlayerMediaSessionEffect(player: Player) {
    val context = LocalContext.current.applicationContext

    DisposableEffect(context, player) {
        val mediaSession = MediaSession.Builder(context, player).build()
        onDispose { mediaSession.release() }
    }
}
