package com.comst19.dambom.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.feature.library.player.component.PlaybackOptionsMenuItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ko-w280dp-h480dp")
class PlaybackOptionsUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `all seven speeds remain reachable and selectable with enlarged text`() {
        val state = OptionsPlayer()
        showOptions(state)
        val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
        speeds.forEach { speed ->
            val currentLabel = state.speed.toString().removeSuffix(".0")
            composeRule.onNodeWithText("재생 배속 · ${currentLabel}배").performClick()
            val label = speed.toString().removeSuffix(".0")
            val option = composeRule.onNodeWithText("${label}배")
            option.performScrollTo().assertIsDisplayed()
            val bounds = option.getUnclippedBoundsInRoot()
            assertTrue(bounds.bottom - bounds.top >= 48.dp)
            option.performClick()
            composeRule.runOnIdle { assertEquals(speed, state.speed) }
        }
        composeRule.onNodeWithText("재생 배속 · 2배").performClick()
        composeRule.onNodeWithText("2배").performScrollTo().assertIsSelected()
        composeRule.onNodeWithText("닫기").assertIsDisplayed().performClick()
    }

    @Test
    fun `repeat announces checked state and toggles current player`() {
        val state = OptionsPlayer()
        showOptions(state)
        composeRule.onNodeWithText("한 영상 반복").assertIsOff().performClick()
        composeRule.onNodeWithText("한 영상 반복").assertIsOn()
        composeRule.runOnIdle { assertEquals(Player.REPEAT_MODE_ONE, state.repeatMode) }
        composeRule.onNodeWithText("한 영상 반복").performClick().assertIsOff()
        composeRule.runOnIdle { assertEquals(Player.REPEAT_MODE_OFF, state.repeatMode) }
    }

    private fun showOptions(state: OptionsPlayer) {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                DambomTheme {
                    Column(Modifier.width(280.dp)) { PlaybackOptionsMenuItems(state.player) }
                }
            }
        }
    }
}

private class OptionsPlayer {
    var speed = 1f
    var repeatMode = Player.REPEAT_MODE_OFF
    private val listeners = mutableSetOf<Player.Listener>()
    val player =
        Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { proxy, method, args ->
            when (method.name) {
                "hashCode" -> {
                    System.identityHashCode(proxy)
                }

                "equals" -> {
                    proxy === args?.firstOrNull()
                }

                "toString" -> {
                    "OptionsPlayer"
                }

                "getPlaybackParameters" -> {
                    PlaybackParameters(speed)
                }

                "getRepeatMode" -> {
                    repeatMode
                }

                "addListener" -> {
                    listeners.add(args!![0] as Player.Listener).let { Unit }
                }

                "removeListener" -> {
                    listeners.remove(args!![0] as Player.Listener).let { Unit }
                }

                "setPlaybackSpeed" -> {
                    speed = args!![0] as Float
                    listeners.toList().forEach { it.onPlaybackParametersChanged(PlaybackParameters(speed)) }
                    Unit
                }

                "setRepeatMode" -> {
                    repeatMode = args!![0] as Int
                    listeners.toList().forEach { it.onRepeatModeChanged(repeatMode) }
                    Unit
                }

                else -> {
                    error("Unexpected Player call: ${method.name}")
                }
            }
        } as Player
}
