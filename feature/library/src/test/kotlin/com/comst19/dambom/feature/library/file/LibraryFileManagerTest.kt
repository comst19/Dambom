package com.comst19.dambom.feature.library.file

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.core.domain.model.DownloadStatus
import com.comst19.dambom.core.domain.model.DownloadTask
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryFileManagerTest {
    @Test
    fun `missing local file returns no share intent instead of throwing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = LibraryFileManager(context, Dispatchers.IO)

        assertNull(manager.createShareIntent(missingFileTask()))
    }
}

private fun missingFileTask() =
    DownloadTask(
        id = "missing",
        url = "https://example.com/video.mp4",
        sourcePageUrl = "https://example.com",
        title = "Missing video",
        mimeType = "video/mp4",
        expectedBytes = 1L,
        downloadedBytes = 1L,
        quality = "original",
        status = DownloadStatus.COMPLETED,
        failureReason = null,
        localFileName = "missing.mp4",
        localFilePath = "/does/not/exist/missing.mp4",
        createdAtMillis = 1L,
        updatedAtMillis = 1L,
    )
