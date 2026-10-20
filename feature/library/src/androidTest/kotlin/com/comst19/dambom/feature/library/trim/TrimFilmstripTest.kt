package com.comst19.dambom.feature.library.trim

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.comst19.dambom.feature.library.trim.component.TrimFilmstrip
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class TrimFilmstripTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun playhead_redraws_when_position_changes_without_changing_range() {
        val position = mutableLongStateOf(2_000L)
        compose.setContent {
            MaterialTheme {
                Box(Modifier.size(300.dp, 56.dp).testTag("filmstrip")) {
                    TrimFilmstrip("/missing/trim-fixture.mp4", 20_000L, 0f..20_000f) { position.longValue }
                }
            }
        }
        val before = compose.onNodeWithTag("filmstrip").captureToImage().toPixelMap()
        compose.runOnIdle { position.longValue = 4_000L }
        val after = compose.onNodeWithTag("filmstrip").captureToImage().toPixelMap()
        val oldX = (before.width * 0.1f).roundToInt()
        val newX = (before.width * 0.2f).roundToInt()
        val middle = before.height / 2
        assertNotEquals(before[oldX, middle], after[oldX, middle])
        assertNotEquals(before[newX, middle], after[newX, middle])
    }
}
