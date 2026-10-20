package com.comst19.dambom.feature.library.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.feature.library.contract.LibraryUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ko")
class LibraryContentLayoutTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `recently played chip wraps and remains selectable at narrow enlarged text`() {
        var checked = false
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                DambomTheme {
                    Box(Modifier.width(280.dp).height(800.dp)) {
                        LibrarySourceFilters(
                            selected = com.comst19.dambom.feature.library.contract.LibrarySourceFilter.ALL,
                            onSelected = {},
                            favoritesOnly = false,
                            onFavoritesOnlyChange = {},
                            recentlyPlayedOnly = true,
                            onRecentlyPlayedOnlyChange = { checked = !it },
                        )
                    }
                }
            }
        }

        composeRule
            .onNodeWithText("최근 본 영상")
            .assertIsDisplayed()
            .assertIsSelected()
            .performClick()
        composeRule.runOnIdle { org.junit.Assert.assertTrue(checked) }
    }

    @Test
    fun `library content starts on the material top bar horizontal grid`() {
        composeRule.setContent {
            DambomTheme {
                Box(Modifier.width(SCREEN_WIDTH).height(800.dp)) {
                    LibraryPane(
                        uiState = LibraryUiState(),
                        fileActions = fileActions(),
                        onQueryChange = {},
                        onSourceFilterChange = {},
                        onVideoClick = {},
                        onToggleSelection = {},
                        showInlineEmptyState = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        val searchBounds =
            composeRule
                .onNodeWithTag(LIBRARY_SEARCH_FIELD_TAG, useUnmergedTree = true)
                .getUnclippedBoundsInRoot()

        assertEquals(CONTENT_PADDING, searchBounds.left)
    }
}

private fun fileActions() =
    LibraryFileActions(
        onRename = { _, _ -> },
        onExport = {},
        onShareVideo = {},
        onShareLink = {},
        onCopyLink = {},
        onOpenOriginal = {},
        onDelete = {},
    )

private val SCREEN_WIDTH = 411.dp
private val CONTENT_PADDING = 16.dp
