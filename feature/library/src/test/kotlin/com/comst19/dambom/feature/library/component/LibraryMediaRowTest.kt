package com.comst19.dambom.feature.library.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.feature.library.LibraryScreen
import com.comst19.dambom.feature.library.contract.LibraryUiState
import com.comst19.dambom.feature.library.contract.LibraryViewMode
import com.comst19.dambom.feature.library.libraryTestVideo
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en")
class LibraryMediaRowTest {
    @get:Rule val composeRule = createComposeRule()
    private val video = libraryTestVideo("/video.mp4")
    private val actions =
        LibraryFileActions(
            onRename = { _, _ -> },
            onExport = {},
            onShareVideo = {},
            onShareLink = {},
            onCopyLink = {},
            onOpenOriginal = {},
            onDelete = {},
        )

    @Test
    fun `favorite and overflow stay inside the card without overlapping thumbnail`() {
        var favoriteClicks = 0
        var videoClicks = 0
        composeRule.setContent {
            DambomTheme {
                Box(Modifier.width(411.dp)) {
                    VideoListItem(
                        video,
                        false,
                        false,
                        false,
                        actions.copy(onToggleFavorite = { favoriteClicks++ }),
                        { videoClicks++ },
                        {},
                    )
                }
            }
        }
        val thumbnail = composeRule.onNodeWithTag("library-thumbnail-${video.id}", true).getUnclippedBoundsInRoot()
        val favorite =
            composeRule.onNodeWithContentDescription("Add to favorites: Saved video").getUnclippedBoundsInRoot()
        val more = composeRule.onNodeWithContentDescription("More actions for Saved video").getUnclippedBoundsInRoot()
        val row = composeRule.onNodeWithTag("library-video-${video.id}").getUnclippedBoundsInRoot()
        assertTrue(favorite.left >= thumbnail.right)
        assertTrue(favorite.top >= more.bottom)
        assertTrue(favorite.bottom <= row.bottom && favorite.right <= row.right)
        assertTrue(more.top >= row.top && more.right <= row.right)
        assertTrue(row.bottom - row.top >= 112.dp)
        assertEquals(48.dp, favorite.right - favorite.left)
        assertEquals(48.dp, favorite.bottom - favorite.top)
        composeRule.onNodeWithContentDescription("Add to favorites: Saved video").performClick()
        assertEquals(1, favoriteClicks)
        assertEquals(0, videoClicks)
    }

    @Test
    fun `phone return does not retain active-row selection`() {
        showLibrary(false)
        composeRule.onNodeWithTag("library-video-${video.id}").assertIsNotSelected()
    }

    @Test
    fun `visible fold detail identifies its active row`() {
        showLibrary(true)
        composeRule.onNodeWithTag("library-video-${video.id}").assertIsSelected()
    }

    private fun showLibrary(showDetail: Boolean) {
        composeRule.setContent {
            DambomTheme {
                LibraryScreen(
                    uiState =
                        LibraryUiState(
                            videos = persistentListOf(video),
                            selectedVideo = video,
                            hasVideos = true,
                            viewMode = LibraryViewMode.LIST,
                        ),
                    fileActions = actions,
                    onQueryChange = {},
                    onViewModeChange = {},
                    onVideoClick = {},
                    showDetailPaneControl = showDetail,
                    isDetailPaneVisible = showDetail,
                )
            }
        }
    }
}
