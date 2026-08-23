package com.comst19.dambom.feature.sample

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.comst19.dambom.feature.sample.contract.SampleState
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SampleScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `list state renders and click callback receives id`() {
        var clickedId: Long? = null
        composeRule.setContent {
            SampleScreen(
                state =
                    SampleState(
                        isLoading = false,
                        items = persistentListOf(SampleUiModel(3, "Sample 3", "Description")),
                    ),
                onRefresh = {},
                onItemClick = { clickedId = it },
            )
        }

        composeRule.onNodeWithText("Sample 3").assertIsDisplayed().performClick()
        assertEquals(3L, clickedId)
    }
}
