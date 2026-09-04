package com.comst19.dambom.feature.detection.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsToggleable
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.core.domain.model.MediaCandidate
import com.comst19.dambom.core.domain.model.MediaVariant
import com.comst19.dambom.core.domain.model.NetworkAccessState
import com.comst19.dambom.core.domain.model.NetworkConnection
import com.comst19.dambom.feature.detection.contract.DetectionUiState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en")
class DetectionCandidateContentTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `card exposes its title and selection once while play stays independent`() {
        val candidate = candidate("video-1", "Video title", "High", "Low")
        var toggles = 0
        var previews = 0
        composeRule.setContent {
            DambomTheme {
                DetectionCandidateItem(
                    candidate = candidate,
                    selectedVariant = candidate.downloadVariants.first(),
                    index = 1,
                    selected = true,
                    onClick = { toggles++ },
                    onSelectVariant = {},
                    onPreview = { previews++ },
                )
            }
        }
        composeRule.onAllNodes(isToggleable()).assertCountEquals(1)
        composeRule.onNodeWithText("Video title").assertIsToggleable().assertIsOn()
        composeRule.onNodeWithText("Play").performClick()
        composeRule.runOnIdle {
            assertEquals(1, previews)
            assertEquals(0, toggles)
        }
    }

    @Test
    fun `select all preserves existing selection then clears all on next tap`() {
        val first = candidate("video-1", "First", "High", "Low")
        val second = candidate("video-2", "Second", "High", "Low")
        val state =
            mutableStateOf(
                DetectionUiState.Content(
                    pageTitle = "Videos",
                    candidates = persistentListOf(first, second),
                    selectedIds = persistentSetOf(first.id),
                ),
            )
        composeRule.setContent {
            DambomTheme {
                DetectionCandidateContent(
                    state = state.value,
                    networkAccess = NetworkAccessState(NetworkConnection.UNMETERED),
                    onToggleCandidate = { id ->
                        val current = state.value
                        val selected = current.selectedIds.toMutableSet()
                        if (!selected.add(id)) selected.remove(id)
                        state.value = current.copy(selectedIds = selected.toPersistentSet())
                    },
                    onSelectVariant = { _, _ -> },
                    onDownload = {},
                )
            }
        }

        composeRule.onNodeWithText("Select all").performClick()
        composeRule.runOnIdle { assertEquals(setOf(first.id, second.id), state.value.selectedIds) }
        composeRule.onNodeWithText("Select all").performClick()
        composeRule.runOnIdle { assertEquals(emptySet<String>(), state.value.selectedIds) }
    }

    @Test
    fun `candidate text follows the grid and visible checkbox aligns with file size`() {
        val candidate = candidate("video-1", "Video title", "High", "Low")

        composeRule.setContent {
            DambomTheme {
                Box(Modifier.width(360.dp)) {
                    DetectionCandidateItem(
                        candidate = candidate,
                        selectedVariant = candidate.downloadVariants.first(),
                        index = 1,
                        selected = true,
                        onClick = {},
                        onSelectVariant = {},
                        onPreview = {},
                    )
                }
            }
        }

        val titleLeft = composeRule.onNodeWithText("Video title", useUnmergedTree = true).getUnclippedBoundsInRoot().left
        val sourceLeft = composeRule.onNodeWithText("example.com", useUnmergedTree = true).getUnclippedBoundsInRoot().left
        val qualityLeft = composeRule.onNodeWithText("High", useUnmergedTree = true).getUnclippedBoundsInRoot().left

        val checkboxBounds =
            composeRule.onNodeWithTag(SELECTION_CHECKBOX_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val fileSizeRight =
            composeRule
                .onNodeWithTag("detection-candidate-size", useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
                .right
        assertEquals(titleLeft, sourceLeft)
        assertEquals(sourceLeft, qualityLeft)
        assertEquals(fileSizeRight, checkboxBounds.right - 2.dp)
        val qualityBounds = composeRule.onNodeWithText("High", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val playBounds = composeRule.onNodeWithText("Play", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(qualityBounds.bottom, playBounds.bottom)
        assertEquals(fileSizeRight, playBounds.right)
    }

    @Test
    fun `detection content uses the app horizontal grid`() {
        val candidate = candidate("video-1", "Video title", "High", "Low")

        composeRule.setContent {
            DambomTheme {
                Box(Modifier.width(360.dp)) {
                    DetectionCandidateContent(
                        state =
                            DetectionUiState.Content(
                                pageTitle = "Videos",
                                candidates = persistentListOf(candidate),
                                selectedIds = persistentSetOf(candidate.id),
                            ),
                        networkAccess = NetworkAccessState(NetworkConnection.UNMETERED),
                        onToggleCandidate = {},
                        onSelectVariant = { _, _ -> },
                        onDownload = {},
                    )
                }
            }
        }

        val pageTitleLeft = composeRule.onNodeWithText("Videos").getUnclippedBoundsInRoot().left

        assertEquals(16.dp, pageTitleLeft)
    }

    @Test
    fun `checkbox center matches the candidate title center`() {
        val candidate = candidate("video-1", "Video title", "High", "Low")

        composeRule.setContent {
            DambomTheme {
                Box(Modifier.width(360.dp)) {
                    DetectionCandidateItem(
                        candidate = candidate,
                        selectedVariant = candidate.downloadVariants.first(),
                        index = 1,
                        selected = true,
                        onClick = {},
                        onSelectVariant = {},
                        onPreview = {},
                    )
                }
            }
        }

        val checkboxBounds =
            composeRule.onNodeWithTag(SELECTION_CHECKBOX_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val titleBounds =
            composeRule.onNodeWithText("Video title", useUnmergedTree = true).getUnclippedBoundsInRoot()

        assertEquals(
            (titleBounds.top + titleBounds.bottom) / 2,
            (checkboxBounds.top + checkboxBounds.bottom) / 2,
        )
    }

    @Test
    fun `multiple selected videos confirm each quality before download`() {
        val first = candidate("video-1", "First video", "High 1", "Low 1")
        val second = candidate("video-2", "Second video", "High 2", "Low 2")
        val selections = mutableListOf<Pair<String, String>>()
        var downloadCount = 0

        composeRule.setContent {
            DambomTheme {
                DetectionCandidateContent(
                    state =
                        DetectionUiState.Content(
                            pageTitle = "Videos",
                            candidates = persistentListOf(first, second),
                            selectedIds = persistentSetOf(first.id, second.id),
                            selectedVariantUrls =
                                persistentMapOf(
                                    first.id to first.downloadVariants.first().url,
                                    second.id to second.downloadVariants.first().url,
                                ),
                        ),
                    networkAccess = NetworkAccessState(NetworkConnection.UNMETERED),
                    onToggleCandidate = {},
                    onSelectVariant = { id, url -> selections += id to url },
                    onDownload = { downloadCount++ },
                )
            }
        }

        composeRule.onNodeWithText("Download 2").performClick()
        composeRule.onNodeWithText("Low 1").performClick()
        assertEquals(0, downloadCount)
        composeRule.onNodeWithText("Low 2").performClick()

        assertEquals(
            listOf(
                first.id to first.downloadVariants.last().url,
                second.id to second.downloadVariants.last().url,
            ),
            selections,
        )
        assertEquals(1, downloadCount)
    }
}

private const val SELECTION_CHECKBOX_TAG = "detection-selection-checkbox"

private fun candidate(
    id: String,
    title: String,
    highQuality: String,
    lowQuality: String,
): MediaCandidate =
    MediaCandidate(
        id = id,
        url = "https://example.com/$id-high.mp4",
        title = title,
        mimeType = "video/mp4",
        contentLength = 2_048L,
        quality = highQuality,
        variants =
            listOf(
                MediaVariant("https://example.com/$id-high.mp4", "video/mp4", 2_048L, highQuality),
                MediaVariant("https://example.com/$id-low.mp4", "video/mp4", 1_024L, lowQuality),
            ),
    )
