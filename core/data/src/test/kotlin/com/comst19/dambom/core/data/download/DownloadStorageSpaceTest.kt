package com.comst19.dambom.core.data.download

import android.content.Context
import android.os.StatFs
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowStatFs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadStorageSpaceTest {
    @Test
    fun `write may use bytes above reserve but never the reserve itself`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = DownloadFileStore(context)
        val path = context.filesDir.resolve("download-parts").path
        val blockSize = StatFs(path).blockSizeLong.toInt()
        val reservedBlocks = 16 * 1024 * 1024 / blockSize
        ShadowStatFs.registerStats(path, reservedBlocks + 1, reservedBlocks + 1, reservedBlocks + 1)

        assertTrue(store.hasSpaceFor(blockSize))
        assertFalse(store.hasSpaceFor(blockSize + 1))

        ShadowStatFs.registerStats(path, reservedBlocks, reservedBlocks, reservedBlocks)
        assertFalse(store.hasSpaceFor(1))
        ShadowStatFs.registerStats(path, reservedBlocks, 0, 0)
        assertFalse(store.hasSpaceFor(1))
    }
}
