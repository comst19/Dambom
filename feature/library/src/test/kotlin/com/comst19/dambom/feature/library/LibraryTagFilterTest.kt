package com.comst19.dambom.feature.library

import com.comst19.dambom.core.domain.model.VideoTag
import com.comst19.dambom.core.domain.model.VideoTagAssignment
import com.comst19.dambom.feature.library.contract.LibrarySourceFilter
import com.comst19.dambom.feature.library.tag.contract.LibraryTagState
import com.comst19.dambom.feature.library.tag.contract.indexTagAssignments
import com.comst19.dambom.feature.library.tag.contract.toLibraryTagState
import kotlinx.collections.immutable.toPersistentList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTagFilterTest {
    private val tags = listOf(VideoTag("a", "A"), VideoTag("b", "B"))

    @Test
    fun `multiple selected tags match any assignment`() {
        val state =
            toLibraryTagState(
                tags,
                indexTagAssignments(listOf(VideoTagAssignment("first", "a"), VideoTagAssignment("second", "b"))),
                listOf("a", "b"),
            )
        assertTrue(state.matches("first"))
        assertTrue(state.matches("second"))
        assertFalse(state.matches("untagged"))
    }

    @Test
    fun `no selected tags keeps untagged videos`() {
        assertTrue(LibraryTagState().matches("untagged"))
    }

    @Test
    fun `removed restored filters are pruned while remaining empty filter still excludes untagged videos`() {
        val index = indexTagAssignments(emptyList())
        val state = toLibraryTagState(tags.drop(1), index, listOf("a", "b"))
        assertEquals(setOf("b"), state.selectedTagIds)
        assertFalse(state.matches("untagged"))
        assertTrue(toLibraryTagState(emptyList(), index, listOf("a", "b")).matches("untagged"))
    }

    @Test
    fun `tag OR intersects search source and favorites without dropping hidden selections`() {
        val videos =
            listOf(
                libraryTestVideo("/one.mp4").copy(id = "first", title = "Trip one", isFavorite = true),
                libraryTestVideo("/two.mp4").copy(id = "second", title = "Trip two", isFavorite = false),
                libraryTestVideo("/three.mp4").copy(id = "third", title = "Other", isFavorite = true),
            )
        val library =
            toLibraryUiState(
                tasks = LibrarySnapshot(videos),
                selectedId = null,
                query = "Trip",
                sourceFilter = LibrarySourceFilter.WEB,
                favoritesOnly = true,
                selection = LibrarySelectionState(true, setOf("first", "second", "third")),
            )
        val tags =
            toLibraryTagState(
                tags,
                indexTagAssignments(listOf(VideoTagAssignment("first", "a"), VideoTagAssignment("second", "b"))),
                listOf("a", "b"),
            )
        val filtered = library.copy(videos = library.videos.filter { tags.matches(it.id) }.toPersistentList())
        assertEquals(listOf("first"), filtered.videos.map { it.id })
        assertEquals(2, filtered.hiddenSelectedCount)
        assertEquals(setOf("first", "second", "third"), filtered.selectedIds)
    }

    @Test
    fun `thousand videos and five thousand assignments are scanned once then queried by set lookup`() {
        val assignments = CountingAssignments(List(5_000) { VideoTagAssignment("video${it % 1_000}", "tag${it % 10}") })
        val index = indexTagAssignments(assignments)
        assertEquals(5_000, assignments.reads)
        val allTags = List(10) { VideoTag("tag$it", "Tag $it") }
        repeat(20) { selection ->
            val state = toLibraryTagState(allTags, index, listOf("tag${selection % 10}"))
            repeat(1_000) { video ->
                assertEquals(video % 10 == selection % 10, state.matches("video$video"))
            }
            assertTrue(state.isAssignedToAll("tag0", setOf("video0", "video10")))
            assertFalse(state.isAssignedToAll("tag0", setOf("video0", "video1")))
        }
        assertEquals(5_000, assignments.reads)
        println("tag-index assignments=5000 videos=1000 selections=20 initial-reads=5000 additional-reads=0")
    }
}

internal class CountingAssignments(
    private val values: List<VideoTagAssignment>,
) : AbstractList<VideoTagAssignment>() {
    var reads = 0
        private set
    override val size: Int get() = values.size

    override fun get(index: Int): VideoTagAssignment {
        reads++
        return values[index]
    }
}
