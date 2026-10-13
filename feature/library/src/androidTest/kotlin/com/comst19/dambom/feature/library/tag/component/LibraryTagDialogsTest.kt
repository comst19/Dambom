package com.comst19.dambom.feature.library.tag.component

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.comst19.dambom.core.designsystem.DambomTheme
import com.comst19.dambom.core.domain.model.VideoTag
import com.comst19.dambom.feature.library.R
import com.comst19.dambom.feature.library.tag.contract.LibraryTagState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryTagDialogsTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun deleting_edited_tag_resets_editor_before_creating_next_tag() {
        val state = mutableStateOf(LibraryTagState(tags = persistentListOf(VideoTag("a", "Travel"))))
        val created = mutableListOf<String>()
        val renamed = mutableListOf<String>()
        composeRule.setContent {
            DambomTheme {
                LibraryTagControls(
                    state = state.value,
                    selectedVideoIds = emptySet(),
                    onToggleFilter = {},
                    onCreate = { name, complete ->
                        created.add(name)
                        complete(true)
                    },
                    onRename = { id, _, complete ->
                        renamed.add(id)
                        complete(true)
                    },
                    onDelete = { state.value = LibraryTagState() },
                    onAssign = { _, _, _ -> },
                )
            }
        }
        composeRule.onNodeWithTag("library-tags-button").performClick()
        composeRule.onNodeWithText(text(R.string.library_manage_tags)).performClick()
        composeRule.onNodeWithText(text(R.string.library_rename_tag)).performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("Old draft")
        composeRule.runOnIdle { state.value = LibraryTagState() }
        composeRule.onNode(hasSetTextAction()).assertDoesNotExist()
        composeRule.onNodeWithText(text(R.string.library_new_tag)).performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("Trips")
        composeRule.onNodeWithText(text(R.string.library_tag_add)).performClick()
        assertEquals(listOf("Trips"), created)
        assertEquals(emptyList<String>(), renamed)
    }

    @Test
    fun failed_create_keeps_draft_and_allows_retry() {
        var requests = 0
        composeRule.setContent {
            DambomTheme {
                TagManagerDialog(
                    tags = persistentListOf(),
                    onCreate = { _, complete ->
                        requests++
                        complete(requests > 1)
                    },
                    onRename = { _, _, _ -> },
                    onDelete = {},
                    onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithText(text(R.string.library_new_tag)).performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("Trips")
        composeRule.onNodeWithText(text(R.string.library_tag_add)).performClick()
        composeRule.onNode(hasSetTextAction()).assertTextContains("Trips")
        composeRule.onNodeWithText(text(R.string.library_tag_add)).performClick()
        composeRule.onNode(hasSetTextAction()).assertDoesNotExist()
        assertEquals(2, requests)
    }

    @Test
    fun failed_rename_retains_edited_identity_and_draft() {
        val requests = mutableListOf<Pair<String, String>>()
        composeRule.setContent {
            DambomTheme {
                TagManagerDialog(
                    tags = persistentListOf(VideoTag("a", "Travel")),
                    onCreate = { _, _ -> error("Must rename existing tag") },
                    onRename = { id, name, complete ->
                        requests.add(id to name)
                        complete(false)
                    },
                    onDelete = {},
                    onDismiss = {},
                )
            }
        }
        composeRule.onNodeWithText(text(R.string.library_rename_tag)).performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("Trips")
        composeRule.onNodeWithText(text(R.string.library_tag_save)).performClick()
        composeRule.onNode(hasSetTextAction()).assertTextContains("Trips")
        composeRule.onNodeWithText(text(R.string.library_tag_save)).performClick()
        assertEquals(listOf("a" to "Trips", "a" to "Trips"), requests)
    }

    @Test
    fun external_deletion_clears_editing_identity_and_draft() {
        val tags = mutableStateOf(persistentListOf(VideoTag("a", "Travel")))
        composeRule.setContent {
            DambomTheme {
                TagManagerDialog(tags.value, { _, _ -> }, { _, _, _ -> }, {}, {})
            }
        }
        composeRule.onNodeWithText(text(R.string.library_rename_tag)).performClick()
        composeRule.runOnIdle { tags.value = persistentListOf() }
        composeRule.onNodeWithText(text(R.string.library_new_tag)).assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).assertDoesNotExist()
    }

    @Test
    fun manager_scrolls_to_last_of_hundred_tags_and_opens_separate_editor() {
        composeRule.setContent {
            DambomTheme {
                TagManagerDialog(
                    tags = List(100) { VideoTag("$it", "Tag $it") }.toPersistentList(),
                    onCreate = { _, _ -> },
                    onRename = { _, _, _ -> },
                    onDelete = {},
                    onDismiss = {},
                )
            }
        }
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(99)
        composeRule.onNodeWithText("Tag 99").assertIsDisplayed()
        composeRule.onNodeWithTag("library-tag-rename-99").performClick()
        composeRule.onNode(hasSetTextAction()).assertIsDisplayed().assertTextContains("Tag 99")
        composeRule.onNodeWithText(text(R.string.library_tag_save)).assertIsDisplayed()
    }

    @Test
    fun assignment_scrolls_to_last_tag_and_preserves_selected_video_set() {
        val selectedIds = setOf("first", "hidden")
        var assigned: Triple<Set<String>, String, Boolean>? = null
        composeRule.setContent {
            DambomTheme {
                TagAssignmentDialog(
                    state = LibraryTagState(tags = List(100) { VideoTag("$it", "Tag $it") }.toPersistentList()),
                    selectedVideoIds = selectedIds,
                    onAssign = { videos, tagId, value -> assigned = Triple(videos, tagId, value) },
                    onDismiss = {},
                )
            }
        }
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(99)
        composeRule.onNodeWithText("Tag 99").assertIsDisplayed().performClick()
        assertEquals(Triple(selectedIds, "99", true), assigned)
    }

    @Test
    fun rename_draft_restores_and_external_deletion_closes_editor() {
        val tags = mutableStateOf(persistentListOf(VideoTag("a", "Travel")))
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            DambomTheme { TagManagerDialog(tags.value, { _, _ -> }, { _, _, _ -> }, {}, {}) }
        }
        composeRule.onNodeWithText(text(R.string.library_rename_tag)).performClick()
        composeRule.onNode(hasSetTextAction()).performTextReplacement("Trips")
        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNode(hasSetTextAction()).assertTextContains("Trips")
        composeRule.runOnIdle { tags.value = persistentListOf() }
        composeRule.onNode(hasSetTextAction()).assertDoesNotExist()
        composeRule.onNodeWithText(text(R.string.library_new_tag)).performClick()
        composeRule.onNode(hasSetTextAction()).assertTextContains("")
    }

    private fun text(resource: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(resource)
}
