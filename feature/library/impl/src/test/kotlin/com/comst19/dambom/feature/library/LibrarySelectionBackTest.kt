package com.comst19.dambom.feature.library

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.feature.library.component.LibraryFileActions
import com.comst19.dambom.feature.library.contract.LibraryUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ko")
class LibrarySelectionBackTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `back clears selection before leaving the library`() {
        lateinit var dispatcher: OnBackPressedDispatcher
        var clearCount = 0
        composeRule.setContent {
            dispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            DambomTheme {
                LibraryScreen(
                    uiState = LibraryUiState(isSelecting = true),
                    fileActions =
                        LibraryFileActions(
                            onRename = { _, _ -> },
                            onExport = {},
                            onShareVideo = {},
                            onShareLink = {},
                            onCopyLink = {},
                            onOpenOriginal = {},
                            onDelete = {},
                        ),
                    onQueryChange = {},
                    onViewModeChange = {},
                    onVideoClick = {},
                    onClearSelection = { clearCount++ },
                )
            }
        }
        composeRule.runOnIdle { dispatcher.onBackPressed() }
        assertEquals(1, clearCount)
    }
}
