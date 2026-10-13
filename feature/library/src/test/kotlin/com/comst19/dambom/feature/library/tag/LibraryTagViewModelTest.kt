package com.comst19.dambom.feature.library.tag

import androidx.lifecycle.SavedStateHandle
import com.comst19.dambom.core.common.ui.AppEvent
import com.comst19.dambom.core.common.ui.AppEventBus
import com.comst19.dambom.core.common.ui.UiText
import com.comst19.dambom.core.domain.model.VideoTag
import com.comst19.dambom.core.domain.model.VideoTagAssignment
import com.comst19.dambom.core.domain.repository.VideoTagRepository
import com.comst19.dambom.core.testing.MainDispatcherRule
import com.comst19.dambom.feature.library.CountingAssignments
import com.comst19.dambom.feature.library.R
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryTagViewModelTest {
    @get:Rule val dispatcherRule = MainDispatcherRule()

    @Test
    fun `selection and tag changes reuse index until assignments emit`() =
        runTest(dispatcherRule.dispatcher) {
            val assignments = CountingAssignments(List(5_000) { VideoTagAssignment("video${it % 1_000}", "a") })
            val repository = TagRepository()
            repository.assignments.value = assignments
            val handle = SavedStateHandle(mapOf("library-tag-filter" to arrayListOf("a", "deleted")))
            val viewModel = LibraryTagViewModel(repository, handle, AppEventBus())
            backgroundScope.launch { viewModel.state.collect() }
            runCurrent()
            assertEquals(5_000, assignments.reads)
            assertEquals(setOf("a"), viewModel.state.value.selectedTagIds)
            repeat(20) {
                viewModel.toggleFilter("a")
                runCurrent()
                repeat(1_000) { assertTrue(viewModel.state.value.matches("video$it")) }
            }
            repository.tags.value = listOf(VideoTag("a", "Renamed"))
            runCurrent()
            assertEquals(5_000, assignments.reads)
            repository.assignments.value = emptyList()
            runCurrent()
            assertFalse(viewModel.state.value.matches("video0"))
            repository.tags.value = emptyList()
            runCurrent()
            assertTrue(viewModel.state.value.matches("untagged"))
            println("tag-viewmodel selection-emissions=20 original-assignment-reads=5000 additional-reads=0")
        }

    @Test
    fun `create and rename report failures while keeping snackbar and success callbacks`() =
        runTest(dispatcherRule.dispatcher) {
            val repository = TagRepository()
            val events = AppEventBus()
            val received = mutableListOf<AppEvent>()
            backgroundScope.launch { events.events.collect { received.add(it) } }
            val viewModel = LibraryTagViewModel(repository, SavedStateHandle(), events)
            val results = mutableListOf<Boolean>()
            repository.fail = true
            viewModel.create("Trips") { results.add(it) }
            runCurrent()
            viewModel.rename("a", "Trips") { results.add(it) }
            runCurrent()
            assertEquals(listOf(false, false), results)
            assertEquals(List(2) { AppEvent.ShowSnackbar(UiText.Resource(R.string.library_tag_failure)) }, received)
            repository.fail = false
            viewModel.create("Trips") { results.add(it) }
            viewModel.rename("a", "Trips") { results.add(it) }
            runCurrent()
            assertEquals(listOf(false, false, true, true), results)
        }
}

private class TagRepository : VideoTagRepository {
    override val tags = MutableStateFlow(listOf(VideoTag("a", "Travel")))
    override val assignments = MutableStateFlow<List<VideoTagAssignment>>(emptyList())
    var fail = false

    override suspend fun create(name: String) {
        if (fail) throw IOException("Duplicate name")
    }

    override suspend fun rename(
        id: String,
        name: String,
    ) {
        if (fail) throw IOException("Duplicate name")
    }

    override suspend fun delete(id: String) = Unit

    override suspend fun assign(
        videoIds: Set<String>,
        tagId: String,
        assigned: Boolean,
    ) = Unit
}
