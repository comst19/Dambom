package com.comst19.dambom.core.common.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CommonModifiersTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `throttled clickable ignores rapid clicks and accepts a later click`() {
        var clickCount = 0
        composeRule.setContent {
            Box(
                Modifier
                    .testTag(CLICK_TARGET_TAG)
                    .throttledClickable(throttleMillis = THROTTLE_MILLIS) { clickCount++ },
            )
        }

        composeRule.onNodeWithTag(CLICK_TARGET_TAG).performClick()
        composeRule.onNodeWithTag(CLICK_TARGET_TAG).performClick()
        assertEquals(1, clickCount)

        ShadowSystemClock.advanceBy(Duration.ofMillis(THROTTLE_MILLIS))
        composeRule.onNodeWithTag(CLICK_TARGET_TAG).performClick()
        assertEquals(2, clickCount)
    }

    private companion object {
        const val CLICK_TARGET_TAG = "click-target"
        const val THROTTLE_MILLIS = 1_000L
    }
}
