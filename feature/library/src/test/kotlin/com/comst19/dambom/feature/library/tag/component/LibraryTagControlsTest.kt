package com.comst19.dambom.feature.library.tag.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.core.domain.model.VideoTag
import com.comst19.dambom.feature.library.LibraryScreen
import com.comst19.dambom.feature.library.R
import com.comst19.dambom.feature.library.component.LibraryFileActions
import com.comst19.dambom.feature.library.contract.LibraryUiState
import com.comst19.dambom.feature.library.contract.LibraryViewMode
import com.comst19.dambom.feature.library.libraryTestVideo
import com.comst19.dambom.feature.library.tag.contract.LibraryTagState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-w360dp-h640dp")
class LibraryTagControlsTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `hundred tags leave video and actions visible`() {
        val video = libraryTestVideo("/video.mp4")
        val state = LibraryTagState(tags = List(100) { VideoTag("$it", "Tag $it") }.toPersistentList())
        composeRule.setContent {
            DambomTheme {
                Box(Modifier.size(360.dp, 640.dp)) {
                    LibraryScreen(
                        uiState =
                            LibraryUiState(
                                videos = persistentListOf(video),
                                hasVideos = true,
                                viewMode = LibraryViewMode.LIST,
                            ),
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
                        tagControls = {
                            LibraryTagControls(
                                state,
                                setOf(video.id),
                                {},
                                { _, _ -> },
                                { _, _, _ -> },
                                {},
                                { _, _, _ -> },
                            )
                        },
                    )
                }
            }
        }
        composeRule.onNodeWithText("Manage tags").assertDoesNotExist()
        composeRule.onNodeWithText("Tag 0").assertDoesNotExist()
        composeRule.onNodeWithTag("library-tags-button").assertIsDisplayed()
        composeRule.onNodeWithText("Tag 1 selected videos").assertIsDisplayed()
        val row = composeRule.onNodeWithTag("library-video-${video.id}")
        row.assertIsDisplayed()
        assertTrue(row.getUnclippedBoundsInRoot().height > 0.dp)
        composeRule.onNodeWithTag("library-tags-button").performClick()
        composeRule.onNodeWithTag("library-tag-filters").performScrollToIndex(99)
        composeRule.onNodeWithText("Tag 99").assertIsDisplayed()
    }

    @Test
    fun `filter supports multiple tags and clears all without closing`() {
        val state =
            mutableStateOf(
                LibraryTagState(tags = persistentListOf(VideoTag("a", "Travel"), VideoTag("b", "Trips"))),
            )
        composeRule.setContent {
            DambomTheme {
                LibraryTagControls(state.value, emptySet(), { id ->
                    val selected = state.value.selectedTagIds
                    state.value =
                        state.value.copy(
                            selectedTagIds = if (id in selected) selected.remove(id) else selected.add(id),
                        )
                }, { _, _ -> }, { _, _, _ -> }, {}, { _, _, _ -> })
            }
        }
        composeRule.onNodeWithTag("library-tags-button").performClick()
        composeRule.onNodeWithText("Travel").performClick()
        composeRule.onNodeWithText("Trips").performClick()
        composeRule.onNodeWithText("Travel").assertIsOn()
        composeRule.onNodeWithText("Trips").assertIsOn()
        composeRule.onNodeWithText(text(R.string.library_tags_clear)).performClick()
        composeRule.runOnIdle { assertTrue(state.value.selectedTagIds.isEmpty()) }
        composeRule.onNodeWithText("Travel").assertIsDisplayed()
    }

    private fun text(resource: Int): String = RuntimeEnvironment.getApplication().getString(resource)
}
