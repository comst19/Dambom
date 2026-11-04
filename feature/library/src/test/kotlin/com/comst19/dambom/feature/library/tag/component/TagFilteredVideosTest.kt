package com.comst19.dambom.feature.library.tag.component

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.feature.library.libraryTestVideo
import com.comst19.dambom.feature.library.tag.contract.LibraryTagState
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TagFilteredVideosTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `source change clears previous videos while a new filter result is pending`() {
        val first = persistentListOf(libraryTestVideo("/first.mp4").copy(id = "first"))
        val second = persistentListOf(first.single().copy(id = "second"))
        val source = mutableStateOf(first)
        val firstResult = CompletableDeferred<PersistentList<DownloadTask>>()
        val secondResult = CompletableDeferred<PersistentList<DownloadTask>>()
        var visible = persistentListOf<DownloadTask>()
        compose.setContent {
            visible =
                rememberTagFilteredVideos(
                    videos = source.value,
                    tags = LibraryTagState(selectedTagIds = persistentSetOf("selected")),
                ) { videos, _ ->
                    if (videos == first) firstResult.await() else secondResult.await()
                }
        }
        compose.runOnIdle { assertTrue(visible.isEmpty()) }
        firstResult.complete(first)
        compose.waitUntil { visible == first }

        compose.runOnIdle { source.value = second }
        compose.waitForIdle()

        compose.runOnIdle { assertTrue(visible.isEmpty()) }
        secondResult.complete(second)
        compose.waitUntil { visible == second }
    }

    @Test
    fun `unfiltered videos are reused without starting a background filter`() {
        val videos = persistentListOf(libraryTestVideo("/video.mp4"))
        var visible = persistentListOf<DownloadTask>()
        compose.setContent {
            visible = rememberTagFilteredVideos(videos, LibraryTagState()) { _, _ -> error("Unexpected filter") }
        }

        compose.runOnIdle { assertEquals(videos, visible) }
    }
}
