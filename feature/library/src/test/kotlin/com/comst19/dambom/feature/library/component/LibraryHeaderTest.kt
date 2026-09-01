package com.comst19.dambom.feature.library.component

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.feature.library.R
import com.comst19.dambom.feature.library.contract.LibraryViewMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "ko")
class LibraryHeaderTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `library header title uses material top bar typography`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val title = context.getString(R.string.library_title)
        var expectedFontSize = TextUnit.Unspecified

        composeRule.setContent {
            DambomTheme {
                expectedFontSize = MaterialTheme.typography.titleLarge.fontSize
                TestLibraryHeader()
            }
        }

        val textLayouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(title).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
            it(textLayouts)
        }

        assertEquals(
            expectedFontSize,
            textLayouts
                .single()
                .layoutInput.style.fontSize,
        )
    }

    @Test
    fun `library header action uses material top bar center`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val viewModeAction = context.getString(R.string.library_view_as_list)

        composeRule.setContent { DambomTheme { TestLibraryHeader() } }

        val actionBounds = composeRule.onNodeWithContentDescription(viewModeAction).getUnclippedBoundsInRoot()

        assertEquals(32.dp, (actionBounds.top + actionBounds.bottom) / 2)
    }
}

@Composable
private fun TestLibraryHeader() {
    LibraryHeader(
        viewMode = LibraryViewMode.GRID,
        onViewModeChange = {},
        showDetailPaneControl = false,
        isDetailPaneVisible = true,
        onDetailPaneVisibilityChange = {},
        hasVideos = false,
        isSelecting = false,
        selectedCount = 0,
        onStartSelection = {},
        onSelectAll = {},
        onDeleteSelected = {},
        onClearSelection = {},
    )
}
