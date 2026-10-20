package com.comst19.dambom.core.common.ui.player

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en")
class DambomPlayerControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `timeline observes updated providers and seek uses latest duration`() {
        val position = mutableLongStateOf(10_000L)
        val duration = mutableLongStateOf(100_000L)
        var seekPosition = -1L
        val player = player { seekPosition = it }
        compose.setContent {
            MaterialTheme {
                DambomPlayerControls(
                    player = player,
                    showPlay = true,
                    playEnabled = true,
                    onPlayPause = {},
                    positionMillis = { position.longValue },
                    durationMillis = { duration.longValue },
                    modifier = Modifier.size(300.dp),
                )
            }
        }
        compose.onNodeWithText("00:10 / 01:40").assertIsDisplayed()
        compose.runOnIdle {
            position.longValue = 60_000L
            duration.longValue = 120_000L
        }
        compose.onNodeWithText("01:00 / 02:00").assertIsDisplayed()
        val seek = compose.onNodeWithContentDescription("Playback position")
        assertEquals(0.5f, seek.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current)
        seek.performSemanticsAction(SemanticsActions.SetProgress) { it(0.75f) }
        compose.runOnIdle { assertEquals(90_000L, seekPosition) }
    }

    @Test
    fun `progress updates keep play control available`() {
        val position = mutableLongStateOf(0L)
        var clicks = 0
        val player = player {}
        compose.setContent {
            MaterialTheme {
                DambomPlayerControls(
                    player = player,
                    showPlay = true,
                    playEnabled = true,
                    onPlayPause = { clicks++ },
                    positionMillis = { position.longValue },
                    durationMillis = { 100_000L },
                    modifier = Modifier.size(300.dp),
                )
            }
        }
        compose.runOnIdle { position.longValue = 50_000L }
        compose.onNodeWithContentDescription("Play").performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    private fun player(onSeek: (Long) -> Unit): Player =
        Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { proxy, method, args ->
            when (method.name) {
                "isCommandAvailable" -> {
                    true
                }

                "seekTo" -> {
                    onSeek(args!![0] as Long)
                    null
                }

                "hashCode" -> {
                    System.identityHashCode(proxy)
                }

                "equals" -> {
                    proxy === args!![0]
                }

                "toString" -> {
                    "ControlsTestPlayer"
                }

                else -> {
                    null
                }
            }
        } as Player
}
