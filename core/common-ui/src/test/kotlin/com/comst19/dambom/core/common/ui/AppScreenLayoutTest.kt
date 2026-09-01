package com.comst19.dambom.core.common.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w900dp-h800dp")
class AppScreenLayoutTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `single pane top bar and content share the same centered width`() {
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.width(WINDOW_WIDTH).height(800.dp)) {
                    AppScreen(
                        maxWidth = AppScreenDefaults.SinglePaneMaxWidth,
                        topBar = {
                            Box(Modifier.fillMaxWidth().height(64.dp).testTag(TOP_BAR_TAG))
                        },
                    ) { innerPadding ->
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .testTag(CONTENT_TAG),
                        )
                    }
                }
            }
        }

        val topBarBounds = composeRule.onNodeWithTag(TOP_BAR_TAG).getUnclippedBoundsInRoot()
        val contentBounds = composeRule.onNodeWithTag(CONTENT_TAG).getUnclippedBoundsInRoot()

        assertEquals(EXPECTED_START, topBarBounds.left)
        assertEquals(EXPECTED_END, topBarBounds.right)
        assertEquals(topBarBounds.left, contentBounds.left)
        assertEquals(topBarBounds.right, contentBounds.right)
    }
}

private const val TOP_BAR_TAG = "app-screen-top-bar"
private const val CONTENT_TAG = "app-screen-content"
private val WINDOW_WIDTH = 900.dp
private val EXPECTED_START = 90.dp
private val EXPECTED_END = 810.dp
