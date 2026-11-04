package com.comst19.dambom.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.core.common.ui.AppEventBus
import com.comst19.dambom.core.domain.model.PrivateVideoSession
import com.comst19.dambom.core.testing.MainDispatcherRule
import com.comst19.dambom.core.testing.SpyNavigationDispatcher
import com.comst19.dambom.feature.library.contract.LibrarySourceFilter
import com.comst19.dambom.feature.library.file.LibraryFileManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class PrivateVideoVaultTest {
    @get:Rule val dispatcherRule = MainDispatcherRule()

    private val publicVideo =
        libraryTestVideo("/public.mp4")
            .copy(id = "public", title = "Public", downloadedBytes = 10)
    private val privateVideo =
        publicVideo.copy(
            id = "private",
            title = "Secret",
            isPrivate = true,
            isFavorite = true,
            downloadedBytes = 90,
        )

    @Test
    fun `ordinary library never includes private search results selection or totals even while unlocked`() =
        runTest(dispatcherRule.dispatcher) {
            val session = PrivateVideoSession().apply { unlock() }
            val model = model(LibraryTestDownloads(listOf(publicVideo, privateVideo)), session)
            backgroundScope.launch { model.uiState.collect() }
            runCurrent()
            assertEquals(
                listOf("public"),
                model.uiState.value.videos
                    .map { it.id },
            )
            assertEquals(1, model.uiState.value.totalVideoCount)
            assertEquals(10L, model.uiState.value.totalBytes)
            model.selectAllVisible()
            model.updateQuery("Secret")
            runCurrent()
            assertTrue(
                model.uiState.value.videos
                    .isEmpty(),
            )
            assertEquals(setOf("public"), model.uiState.value.selectedIds)
        }

    @Test
    fun `private library reuses source search and favorite filters and clears all media on lock`() =
        runTest(dispatcherRule.dispatcher) {
            val session = PrivateVideoSession()
            val model = model(LibraryTestDownloads(listOf(publicVideo, privateVideo)), session)
            model.setPrivateVault(true)
            backgroundScope.launch { model.uiState.collect() }
            runCurrent()
            assertTrue(
                model.uiState.value.videos
                    .isEmpty(),
            )
            assertEquals(0, model.uiState.value.totalVideoCount)
            assertEquals(
                VideoDetailState.NotFound,
                model.observeVideo("private").first { it != VideoDetailState.Loading },
            )
            session.unlock()
            model.updateQuery("secret")
            model.setFavoritesOnly(true)
            model.setSourceFilter(LibrarySourceFilter.WEB)
            runCurrent()
            assertEquals(
                listOf("private"),
                model.uiState.value.videos
                    .map { it.id },
            )
            assertEquals(90L, model.uiState.value.totalBytes)
            model.setSourceFilter(LibrarySourceFilter.X)
            runCurrent()
            assertTrue(
                model.uiState.value.videos
                    .isEmpty(),
            )
            model.setSourceFilter(LibrarySourceFilter.ALL)
            model.selectAllVisible(listOf("private"))
            runCurrent()
            model.lockPrivateVault()
            runCurrent()
            assertTrue(
                model.uiState.value.videos
                    .isEmpty(),
            )
            assertTrue(
                model.uiState.value.selectedIds
                    .isEmpty(),
            )
            assertNull(model.uiState.value.selectedVideo)
            assertEquals(0, model.uiState.value.totalVideoCount)
            assertEquals(
                VideoDetailState.NotFound,
                model.observeVideo("private").first { it != VideoDetailState.Loading },
            )
            assertTrue(model.observeVideo("public").first { it != VideoDetailState.Loading } is VideoDetailState.Ready)
        }

    @Test
    fun `privacy changes persist through the shared repository without changing filter preferences`() =
        runTest(dispatcherRule.dispatcher) {
            val session = PrivateVideoSession().apply { unlock() }
            val repository = LibraryTestDownloads(listOf(publicVideo, privateVideo))
            val model = model(repository, session)
            backgroundScope.launch { model.uiState.collect() }
            runCurrent()
            model.updateQuery("Public")
            model.changePrivacy(setOf("public"), true)
            runCurrent()
            assertTrue(repository.downloads.value.all { it.isPrivate })
            assertTrue(
                model.uiState.value.videos
                    .isEmpty(),
            )
            assertEquals("Public", model.uiState.value.query)
            session.lock()
            model.changePrivacy(setOf("private"), false)
            runCurrent()
            assertTrue(
                repository.downloads.value
                    .first { it.id == "private" }
                    .isPrivate,
            )
        }

    @Test
    fun `private video cannot be shared or exported and session starts locked`() =
        runTest(dispatcherRule.dispatcher) {
            val session = PrivateVideoSession()
            assertFalse(session.isUnlocked.value)
            val model = model(LibraryTestDownloads(listOf(privateVideo)), session)
            assertNull(model.createShareIntent(privateVideo))
            assertNull(model.findExportTask("private"))
        }

    @Test
    fun `leaving private library preserves the process session until explicit lock`() =
        runTest(dispatcherRule.dispatcher) {
            val session = PrivateVideoSession().apply { unlock() }
            val model = model(LibraryTestDownloads(listOf(publicVideo, privateVideo)), session)
            model.leavePrivateVault()
            runCurrent()
            assertTrue(session.isUnlocked.value)
            val reopened = model(LibraryTestDownloads(listOf(publicVideo, privateVideo)), session)
            reopened.setPrivateVault(true)
            backgroundScope.launch { reopened.uiState.collect() }
            runCurrent()
            assertEquals(
                listOf("private"),
                reopened.uiState.value.videos
                    .map { it.id },
            )
            reopened.lockPrivateVault()
            runCurrent()
            assertFalse(session.isUnlocked.value)
            assertTrue(
                reopened.uiState.value.videos
                    .isEmpty(),
            )
            assertFalse(PrivateVideoSession().isUnlocked.value)
        }

    private fun model(
        repository: LibraryTestDownloads,
        session: PrivateVideoSession,
    ) = LibraryViewModel(
        repository,
        LibraryTestSettings,
        SpyNavigationDispatcher(),
        SavedStateHandle(),
        LibraryFileManager(ApplicationProvider.getApplicationContext(), dispatcherRule.dispatcher),
        AppEventBus(),
        LibraryTestPlaybackHistory(),
        session,
        defaultDispatcher = dispatcherRule.dispatcher,
    )
}
