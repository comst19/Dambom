package com.comst19.dambom.feature.library.media

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], instrumentedPackages = ["com.comst19.dambom.feature.library.media"])
class LocalVideoMetadataTest {
    private val fixtures = mutableListOf<File>()

    @After
    fun cleanFixtures() {
        fixtures.forEach { file ->
            File("${file.path}.thumbnail.unavailable").delete()
            file.delete()
        }
    }

    private fun metadataFixture(file: File): File {
        file.writeBytes(byteArrayOf(1))
        File("${file.path}.thumbnail.unavailable").apply {
            writeBytes(byteArrayOf())
            setLastModified(file.lastModified())
        }
        org.junit.Assert.assertTrue(
            File("${file.path}.thumbnail.unavailable").lastModified() >= file.lastModified(),
        )
        fixtures += file
        return file
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Suppress("DEPRECATION")
    fun `trim signal prevents a pending load from repopulating the cache`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Application>()
            val file = metadataFixture(context.filesDir.resolve("pending-hidden-cache.mp4"))
            val key = localVideoCacheKey(file.path)
            val mutex =
                LocalVideoMetadataLoader::class.java.getDeclaredField("readMutex").let {
                    it.isAccessible = true
                    it.get(LocalVideoMetadataLoader) as Mutex
                }
            org.junit.Assert.assertFalse("Leaked metadata reader: $mutex main=${Dispatchers.Main}", mutex.isLocked)
            mutex.lock()
            val pending = async { LocalVideoMetadataLoader.load(context, key) }
            try {
                runCurrent()
                context.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
            } finally {
                mutex.unlock()
            }
            assertNotSame(pending.await(), LocalVideoMetadataLoader.load(context, key))
        }

    @Test
    @Suppress("DEPRECATION")
    fun `hidden UI releases cached metadata`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Application>()
            val file = metadataFixture(context.filesDir.resolve("hidden-cache.mp4"))
            val key = localVideoCacheKey(file.path)
            val first = LocalVideoMetadataLoader.load(context, key)
            context.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
            assertNotSame(first, LocalVideoMetadataLoader.load(context, key))
        }

    @Test
    @Suppress("DEPRECATION")
    fun `visible moderate signal preserves cached metadata`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Application>()
            val file = metadataFixture(context.filesDir.resolve("visible-cache.mp4"))
            val key = localVideoCacheKey(file.path)
            val first = LocalVideoMetadataLoader.load(context, key)
            context.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE)
            assertSame(first, LocalVideoMetadataLoader.load(context, key))
        }

    @Test
    fun `revision observer sees missing file creation replacement and deletion and stops on cancel`() =
        runBlocking {
            val directory =
                java.nio.file.Files
                    .createTempDirectory("observed-video")
                    .toFile()
            val file = directory.resolve("video.mp4")
            val updates = Channel<LocalVideoCacheKey>(Channel.UNLIMITED)
            val job = launch(Dispatchers.IO) { observeLocalVideoCacheKey(file.path).collect { updates.send(it) } }

            suspend fun awaitSize(size: Long) =
                withTimeout(5_000) {
                    var key = updates.receive()
                    while (key.sizeBytes != size) key = updates.receive()
                    key
                }
            try {
                awaitSize(0)
                file.writeBytes(byteArrayOf(1))
                val first = awaitSize(1)
                file.writeBytes(byteArrayOf(1, 2, 3))
                assertNotEquals(first, awaitSize(3))
                file.delete()
                awaitSize(0)
                job.cancelAndJoin()
                org.junit.Assert.assertTrue(job.isCancelled)
            } finally {
                job.cancelAndJoin()
                directory.deleteRecursively()
            }
        }

    @Test
    fun `concurrent loads reuse one cached result while replaced file reloads`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val videoFile = metadataFixture(context.filesDir.resolve("replace-concurrent.mp4"))
            val key = localVideoCacheKey(videoFile.path)
            val results =
                List(20) {
                    async(Dispatchers.IO) { LocalVideoMetadataLoader.load(context, key) }
                }.awaitAll()

            results.forEach { assertSame(results.first(), it) }
            videoFile.writeBytes(byteArrayOf(1, 2, 3))
            File("${videoFile.path}.thumbnail.unavailable").setLastModified(videoFile.lastModified())
            assertNotSame(results.first(), LocalVideoMetadataLoader.load(context, localVideoCacheKey(videoFile.path)))
        }

    @Test
    fun `cache key changes when video content at the same path is replaced`() {
        val videoFile = File.createTempFile("replace-cache", ".mp4").apply { writeBytes(byteArrayOf(1)) }
        val beforeReplace = localVideoCacheKey(videoFile.path)

        videoFile.writeBytes(byteArrayOf(1, 2))

        assertNotEquals(beforeReplace, localVideoCacheKey(videoFile.path))
        videoFile.delete()
    }
}
