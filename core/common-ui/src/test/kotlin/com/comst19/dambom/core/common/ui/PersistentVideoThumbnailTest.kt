package com.comst19.dambom.core.common.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class PersistentVideoThumbnailTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `existing thumbnail is reused without generating another frame`() {
        val videoFile = temporaryFolder.newFile("video.mp4").apply { writeBytes(byteArrayOf(1)) }
        var generatedCount = 0
        val writer: (java.io.File) -> Boolean = { output ->
            generatedCount++
            output.writeBytes(byteArrayOf(2))
            true
        }

        val first = ensureVideoThumbnailFile(videoFile, writer)
        val second = ensureVideoThumbnailFile(videoFile, writer)

        assertTrue(first?.isFile == true)
        assertEquals(first, second)
        assertEquals(1, generatedCount)
    }

    @Test
    fun `unavailable thumbnail is not retried until the video changes`() {
        val videoFile = temporaryFolder.newFile("unsupported.mp4").apply { writeBytes(byteArrayOf(1)) }

        rememberVideoThumbnailUnavailable(videoFile)

        assertTrue(isVideoThumbnailUnavailable(videoFile))

        videoFile.setLastModified(videoFile.lastModified() + 1_000L)

        assertFalse(isVideoThumbnailUnavailable(videoFile))
    }

    @Test
    fun `concurrent requests for the same path share one generation`() =
        runTest {
            val coordinator = coordinator()
            val videoFile = temporaryFolder.newFile("shared.mp4")
            val generatedFile = temporaryFolder.newFile("shared.jpg")
            val generationGate = CompletableDeferred<Unit>()
            val generationCount = AtomicInteger()

            val requests =
                List(10) {
                    async {
                        coordinator.load(videoFile, noThumbnail, available) {
                            generationCount.incrementAndGet()
                            generationGate.await()
                            generatedFile
                        }
                    }
                }
            runCurrent()

            assertEquals(1, generationCount.get())
            generationGate.complete(Unit)
            assertTrue(requests.awaitAll().all { it == generatedFile })
        }

    @Test
    fun `different paths run concurrently without exceeding two generations`() =
        runTest {
            val coordinator = coordinator()
            val generationGate = CompletableDeferred<Unit>()
            val activeCount = AtomicInteger()
            val maximumActiveCount = AtomicInteger()
            val generationCount = AtomicInteger()
            val videoFiles = List(4) { temporaryFolder.newFile("parallel-$it.mp4") }

            val requests =
                videoFiles.map { videoFile ->
                    async {
                        coordinator.load(videoFile, noThumbnail, available) {
                            generationCount.incrementAndGet()
                            val active = activeCount.incrementAndGet()
                            maximumActiveCount.updateAndGet { maximum -> maxOf(maximum, active) }
                            generationGate.await()
                            activeCount.decrementAndGet()
                            videoFile
                        }
                    }
                }
            runCurrent()

            assertEquals(2, activeCount.get())
            assertEquals(2, maximumActiveCount.get())
            generationGate.complete(Unit)
            requests.awaitAll()
            assertEquals(4, generationCount.get())
            assertEquals(2, maximumActiveCount.get())
        }

    @Test
    fun `cancelling one waiter does not cancel shared generation`() =
        runTest {
            val coordinator = coordinator()
            val videoFile = temporaryFolder.newFile("cancelled-waiter.mp4")
            val generatedFile = temporaryFolder.newFile("cancelled-waiter.jpg")
            val generationGate = CompletableDeferred<Unit>()
            val generationCount = AtomicInteger()

            suspend fun load() =
                coordinator.load(videoFile, noThumbnail, available) {
                    generationCount.incrementAndGet()
                    generationGate.await()
                    generatedFile
                }

            val cancelledWaiter = async { load() }
            val remainingWaiter = async { load() }
            runCurrent()

            cancelledWaiter.cancelAndJoin()
            generationGate.complete(Unit)

            assertEquals(generatedFile, remainingWaiter.await())
            assertEquals(1, generationCount.get())
        }

    @Test
    fun `failed generation is removed so the next request can retry`() =
        runTest {
            val coordinator = coordinator()
            val videoFile = temporaryFolder.newFile("retry.mp4")
            val generatedFile = temporaryFolder.newFile("retry.jpg")
            val generationCount = AtomicInteger()
            var failed = false

            try {
                coordinator.load(videoFile, noThumbnail, available) {
                    generationCount.incrementAndGet()
                    error("generation failed")
                }
            } catch (_: IllegalStateException) {
                failed = true
            }

            val result =
                coordinator.load(videoFile, noThumbnail, available) {
                    generationCount.incrementAndGet()
                    generatedFile
                }

            assertTrue(failed)
            assertEquals(generatedFile, result)
            assertEquals(2, generationCount.get())
        }

    @Test
    fun `cached thumbnail bypasses saturated generation permits`() =
        runTest {
            val coordinator = coordinator()
            val generationGate = CompletableDeferred<Unit>()
            val blockers = startBlockingGenerations(coordinator, generationGate)
            val cachedVideo = temporaryFolder.newFile("cached.mp4")
            val cachedThumbnail = temporaryFolder.newFile("cached.jpg")

            val request =
                async {
                    coordinator.load(
                        cachedVideo,
                        existingThumbnail = { cachedThumbnail },
                        isUnavailable = available,
                    ) { error("cache hit must not generate") }
                }
            runCurrent()

            assertTrue(request.isCompleted)
            assertEquals(cachedThumbnail, request.await())
            generationGate.complete(Unit)
            blockers.awaitAll()
        }

    @Test
    fun `unavailable marker bypasses saturated generation permits`() =
        runTest {
            val coordinator = coordinator()
            val generationGate = CompletableDeferred<Unit>()
            val blockers = startBlockingGenerations(coordinator, generationGate)
            val unavailableVideo = temporaryFolder.newFile("unavailable.mp4")

            val request =
                async {
                    coordinator.load(
                        unavailableVideo,
                        existingThumbnail = noThumbnail,
                        isUnavailable = { true },
                    ) { error("unavailable video must not generate") }
                }
            runCurrent()

            assertTrue(request.isCompleted)
            assertNull(request.await())
            generationGate.complete(Unit)
            blockers.awaitAll()
        }

    @Test
    fun `cache is checked again after waiting for a generation permit`() =
        runTest {
            val coordinator = VideoThumbnailGenerationCoordinator(1, StandardTestDispatcher(testScheduler))
            val generationGate = CompletableDeferred<Unit>()
            val blockingVideo = temporaryFolder.newFile("permit-blocker.mp4")
            val waitingVideo = temporaryFolder.newFile("permit-waiter.mp4")
            val cachedThumbnail = temporaryFolder.newFile("permit-waiter.jpg")
            var thumbnailAvailable = false

            val blocker =
                async {
                    coordinator.load(blockingVideo, noThumbnail, available) {
                        generationGate.await()
                        blockingVideo
                    }
                }
            val waiting =
                async {
                    coordinator.load(
                        waitingVideo,
                        existingThumbnail = { cachedThumbnail.takeIf { thumbnailAvailable } },
                        isUnavailable = available,
                    ) { error("second cache check must skip generation") }
                }
            runCurrent()

            thumbnailAvailable = true
            generationGate.complete(Unit)

            assertEquals(blockingVideo, blocker.await())
            assertEquals(cachedThumbnail, waiting.await())
        }

    @Test
    fun `unavailable marker is checked again after waiting for a generation permit`() =
        runTest {
            val coordinator = VideoThumbnailGenerationCoordinator(1, StandardTestDispatcher(testScheduler))
            val generationGate = CompletableDeferred<Unit>()
            val blockingVideo = temporaryFolder.newFile("marker-blocker.mp4")
            val waitingVideo = temporaryFolder.newFile("marker-waiter.mp4")
            var unavailable = false

            val blocker =
                async {
                    coordinator.load(blockingVideo, noThumbnail, available) {
                        generationGate.await()
                        blockingVideo
                    }
                }
            val waiting =
                async {
                    coordinator.load(
                        waitingVideo,
                        existingThumbnail = noThumbnail,
                        isUnavailable = { unavailable },
                    ) { error("second marker check must skip generation") }
                }
            runCurrent()

            unavailable = true
            generationGate.complete(Unit)

            assertEquals(blockingVideo, blocker.await())
            assertNull(waiting.await())
        }

    private fun TestScope.coordinator() = VideoThumbnailGenerationCoordinator(2, StandardTestDispatcher(testScheduler))

    private fun TestScope.startBlockingGenerations(
        coordinator: VideoThumbnailGenerationCoordinator,
        generationGate: CompletableDeferred<Unit>,
    ) = List(2) { index ->
        async {
            val videoFile = temporaryFolder.newFile("blocker-$index.mp4")
            coordinator.load(videoFile, noThumbnail, available) {
                generationGate.await()
                videoFile
            }
        }
    }.also { runCurrent() }

    private companion object {
        val noThumbnail: (File) -> File? = { null }
        val available: (File) -> Boolean = { false }
    }
}
