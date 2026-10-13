package com.comst19.dambom.feature.library.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.feature.library.contract.LibraryUiState
import com.comst19.dambom.feature.library.contract.LibraryViewMode
import org.junit.Rule
import org.junit.Test

class LibraryViewportTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun grid_filters_remain_reachable_in_short_viewport() = verifyFilters(LibraryViewMode.GRID)

    @Test
    fun list_filters_remain_reachable_in_short_viewport() = verifyFilters(LibraryViewMode.LIST)

    private fun verifyFilters(mode: LibraryViewMode) {
        composeRule.setContent {
            DambomTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                    Box(Modifier.size(280.dp, 180.dp)) {
                        LibraryPane(
                            uiState = LibraryUiState(hasVideos = true, viewMode = mode),
                            fileActions = LibraryFileActions({ _, _ -> }, {}, {}, {}, {}, {}, onDelete = {}),
                            onQueryChange = {},
                            onSourceFilterChange = {},
                            onVideoClick = {},
                            onToggleSelection = {},
                            showInlineEmptyState = true,
                            modifier = Modifier,
                            tagControls = { FilterChip(false, {}, label = { Text("Tags reachable") }) },
                        )
                    }
                }
            }
        }
        repeat(12) {
            if (!composeRule.onNodeWithText("Tags reachable").isDisplayed()) {
                composeRule.onNodeWithTag("library-collection").performTouchInput {
                    swipeUp(startY = height * 0.8f, endY = height * 0.6f, durationMillis = 500)
                }
            }
        }
        composeRule.onNodeWithText("Tags reachable").assertIsDisplayed()
    }
}
