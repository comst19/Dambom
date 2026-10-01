package com.comst19.dambom.feature.library.component

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.feature.library.libraryTestVideo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en")
class VideoFavoriteButtonTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `card star toggles favorite without opening video or selection`() {
        val video = mutableStateOf(libraryTestVideo("/video.mp4"))
        var opened = 0
        var selected = 0
        composeRule.setContent {
            DambomTheme {
                VideoCard(
                    task = video.value,
                    selected = false,
                    selectionSelected = false,
                    isSelecting = false,
                    fileActions =
                        LibraryFileActions(
                            onRename = { _, _ -> },
                            onExport = {},
                            onShareVideo = {},
                            onShareLink = {},
                            onCopyLink = {},
                            onOpenOriginal = {},
                            onDelete = {},
                            onToggleFavorite = { video.value = it.copy(isFavorite = !it.isFavorite) },
                        ),
                    onClick = { opened++ },
                    onToggleSelection = { selected++ },
                )
            }
        }
        composeRule.onNodeWithContentDescription("Add to favorites: Saved video").assertIsOff().performClick()
        composeRule.onNodeWithContentDescription("Remove from favorites: Saved video").assertIsOn().performClick()
        composeRule.onNodeWithContentDescription("Add to favorites: Saved video").assertIsOff()
        assertEquals(0, opened)
        assertEquals(0, selected)
    }
}
