package com.comst19.dambom.core.common.ui.player

import android.view.View
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player

@Composable
fun KeepScreenOnWhilePlaying(
    player: Player,
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val view = remember(context) { View(context) }
    AndroidView(factory = { view }, modifier = Modifier.size(0.dp))
    DisposableEffect(player, lifecycle, view, enabled) {
        fun update() {
            view.keepScreenOn = enabled && player.isPlaying && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        val listener =
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) = update()
            }
        val observer = LifecycleEventObserver { _, _ -> update() }
        player.addListener(listener)
        lifecycle.addObserver(observer)
        update()
        onDispose {
            player.removeListener(listener)
            lifecycle.removeObserver(observer)
            view.keepScreenOn = false
        }
    }
}
