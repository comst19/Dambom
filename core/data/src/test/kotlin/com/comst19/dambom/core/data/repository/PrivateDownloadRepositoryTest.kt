package com.comst19.dambom.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.comst19.dambom.core.data.download.DownloadFileStore
import com.comst19.dambom.core.data.download.DownloadWorkScheduler
import com.comst19.dambom.core.database.DambomDatabase
import com.comst19.dambom.core.database.download.DownloadTaskEntity
import com.comst19.dambom.core.domain.model.PrivateVideoSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PrivateDownloadRepositoryTest {
    @Test
    @Suppress("LongMethod")
    fun `private videos persist and are excluded from ordinary queries even after authentication`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val database =
                Room
                    .inMemoryDatabaseBuilder(context, DambomDatabase::class.java)
                    .allowMainThreadQueries()
                    .build()
            val session = PrivateVideoSession()
            val scheduler =
                object : DownloadWorkScheduler {
                    override suspend fun schedule() = Unit

                    override suspend fun ensureScheduled() = Unit

                    override suspend fun reschedule() = Unit
                }
            val dao = database.downloadTaskDao()
            val source =
                DownloadTaskEntity(
                    id = "private-video",
                    url = "https://example.com/private.mp4",
                    sourcePageUrl = "https://example.com",
                    host = "example.com",
                    title = "Private video",
                    mimeType = "video/mp4",
                    expectedBytes = 100,
                    downloadedBytes = 100,
                    quality = "original",
                    status = "COMPLETED",
                    failureReason = null,
                    retryCount = 0,
                    deletePending = false,
                    localFileName = "private-video.mp4",
                    createdAtMillis = 1,
                    updatedAtMillis = 2,
                )
            dao.insert(source)
            val files =
                object : DownloadFileStore(ApplicationProvider.getApplicationContext()) {
                    override fun revokeSharing(localFileName: String) = Unit
                }
            val repository =
                DefaultDownloadRepository(
                    dao,
                    scheduler,
                    files,
                    StandardTestDispatcher(testScheduler),
                    session,
                )
            assertTrue(runCatching { repository.setPrivate(setOf(source.id), true) }.isFailure)
            session.unlock()
            repository.setPrivate(setOf(source.id), true)
            assertEquals(source.copy(isPrivate = true), dao.getById(source.id))
            assertTrue(repository.downloads.first().isEmpty())
            assertTrue(repository.completedDownloads.first().isEmpty())
            assertEquals(
                source.id,
                repository.privateCompletedDownloads
                    .first()
                    .single()
                    .id,
            )
            assertEquals(source.id, repository.observeDownload(source.id).first()?.id)
            session.lock()
            assertTrue(repository.privateCompletedDownloads.first().isEmpty())
            assertEquals(null, repository.observeDownload(source.id).first())
            assertTrue(runCatching { repository.toggleFavorite(source.id) }.isFailure)
            assertTrue(runCatching { repository.delete(source.id) }.isFailure)
            session.unlock()
            repository.setPrivate(setOf(source.id), false)
            assertEquals(source, dao.getById(source.id))
            assertEquals(
                source.id,
                repository.completedDownloads
                    .first()
                    .single()
                    .id,
            )
            database.close()
        }
}
