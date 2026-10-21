package com.comst19.dambom.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.core.data.download.DownloadFileStore
import com.comst19.dambom.core.data.download.DownloadWorkScheduler
import com.comst19.dambom.core.database.DambomDatabase
import com.comst19.dambom.core.domain.model.DownloadRequest
import com.comst19.dambom.core.network.header.decodeMediaHeaders
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadRequestContextTest {
    @Test
    fun `request context survives queue persistence and is cleared after completion`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val builder = Room.inMemoryDatabaseBuilder(context, DambomDatabase::class.java)
            val database = builder.allowMainThreadQueries().build()
            try {
                val scheduler =
                    object : DownloadWorkScheduler {
                        override suspend fun schedule() = Unit

                        override suspend fun ensureScheduled() = Unit

                        override suspend fun reschedule() = Unit
                    }
                val dao = database.downloadTaskDao()
                val repository =
                    DefaultDownloadRepository(
                        dao,
                        scheduler,
                        DownloadFileStore(context),
                        StandardTestDispatcher(testScheduler),
                    )
                val request =
                    DownloadRequest(
                        "context",
                        "https://example.com/video",
                        "https://example.com",
                        "Video",
                        "video/mp4",
                        null,
                        requestHeaders = mapOf("Cookie" to "session=test"),
                        videoHeight = 720,
                    )
                repository.enqueue(listOf(request))
                val stored = requireNotNull(dao.getById(request.id))
                assertEquals(720, stored.videoHeight)
                assertEquals(mapOf("cookie" to "session=test"), decodeMediaHeaders(stored.requestHeaders))
                dao.compareAndSetStatus(request.id, "QUEUED", "DOWNLOADING", 1L)
                dao.markCompleted(request.id, 10L, "test.mp4", 2L)
                assertEquals("{}", dao.getById(request.id)?.requestHeaders)
            } finally {
                database.close()
            }
        }
}
