package com.comst19.dambom.core.common.ui.player

import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KeepScreenOnWhilePlayingTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `screen retention follows actual playback foreground visibility and disposal`() {
        val listeners = mutableSetOf<Player.Listener>()
        var playing = false
        val player = createPlayer(listeners) { playing }
        val owner =
            object : LifecycleOwner {
                override val lifecycle = LifecycleRegistry.createUnsafe(this)
            }
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        val enabled = mutableStateOf(true)
        val visible = mutableStateOf(true)
        lateinit var root: View
        compose.setContent {
            root = LocalView.current.rootView
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                if (visible.value) KeepScreenOnWhilePlaying(player, enabled.value)
            }
        }

        fun assertRetained(expected: Boolean) = compose.runOnIdle { assertEquals(expected, root.hasRetainingView()) }
        assertRetained(false)
        compose.runOnIdle {
            playing = true
            listeners.toList().forEach { it.onIsPlayingChanged(true) }
        }
        assertRetained(true)
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.STARTED }
        assertRetained(false)
        compose.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        assertRetained(true)
        compose.runOnIdle { enabled.value = false }
        assertRetained(false)
        compose.runOnIdle { enabled.value = true }
        assertRetained(true)
        compose.runOnIdle {
            playing = false
            listeners.toList().forEach { it.onIsPlayingChanged(false) }
        }
        assertRetained(false)
        compose.runOnIdle {
            playing = true
            listeners.toList().forEach { it.onIsPlayingChanged(true) }
            visible.value = false
        }
        assertRetained(false)
        compose.runOnIdle { assertEquals(0, listeners.size) }
    }

    private fun createPlayer(
        listeners: MutableSet<Player.Listener>,
        isPlaying: () -> Boolean,
    ): Player =
        Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, args ->
            when (method.name) {
                "isPlaying" -> {
                    isPlaying()
                }

                "addListener" -> {
                    listeners.add(args!![0] as Player.Listener)
                    null
                }

                "removeListener" -> {
                    listeners.remove(args!![0] as Player.Listener)
                    null
                }

                "hashCode" -> {
                    1
                }

                "equals" -> {
                    false
                }

                else -> {
                    null
                }
            }
        } as Player

    private fun View.hasRetainingView(): Boolean =
        keepScreenOn ||
            (this is ViewGroup && (0 until childCount).any { getChildAt(it).hasRetainingView() })
}
