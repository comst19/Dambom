package com.comst19.dambom.feature.library.media

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalVideoMetadataTest {
    @Test
    fun `concurrent loads reuse one cached result while a new revision reloads`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val key = LocalVideoCacheKey(context.filesDir.resolve("missing-concurrent.mp4").path, 1L)
            val results =
                List(20) {
                    async(Dispatchers.IO) { LocalVideoMetadataLoader.load(context, key) }
                }.awaitAll()

            results.forEach { assertSame(results.first(), it) }
            assertNotSame(results.first(), LocalVideoMetadataLoader.load(context, key.copy(revision = 2L)))
        }

    @Test
    fun `cache key changes when a video at the same path is replaced`() {
        assertNotEquals(
            LocalVideoCacheKey(path = "/videos/same.mp4", revision = 1L),
            LocalVideoCacheKey(path = "/videos/same.mp4", revision = 2L),
        )
    }
}
