package com.comst19.dambom.feature.library.media

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalVideoMetadataTest {
    @Test
    fun `concurrent loads reuse one cached result while replaced file reloads`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val videoFile = context.filesDir.resolve("replace-concurrent.mp4").apply { writeBytes(byteArrayOf(1)) }
            val key = localVideoCacheKey(videoFile.path)
            val results =
                List(20) {
                    async(Dispatchers.IO) { LocalVideoMetadataLoader.load(context, key) }
                }.awaitAll()

            results.forEach { assertSame(results.first(), it) }
            videoFile.writeBytes(byteArrayOf(1, 2, 3))
            assertNotSame(results.first(), LocalVideoMetadataLoader.load(context, localVideoCacheKey(videoFile.path)))
        }

    @Test
    fun `cache key is unchanged when only task metadata changes`() {
        val videoFile = File.createTempFile("rename-cache", ".mp4").apply { writeBytes(byteArrayOf(1)) }

        val beforeRename = localVideoCacheKey(videoFile.path)
        val afterRename = localVideoCacheKey(videoFile.path)

        assertEquals(beforeRename, afterRename)
        videoFile.delete()
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
