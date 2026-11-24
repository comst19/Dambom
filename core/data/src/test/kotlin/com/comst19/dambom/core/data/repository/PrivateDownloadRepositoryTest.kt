package com.comst19.dambom.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.comst19.dambom.core.common.io.videoSubtitleFile
import com.comst19.dambom.core.common.io.videoThumbnailFile
import com.comst19.dambom.core.data.download.DownloadFileStore
import com.comst19.dambom.core.data.download.DownloadWorkScheduler
import com.comst19.dambom.core.data.download.PrivacyFileMoveResult
import com.comst19.dambom.core.database.DambomDatabase
import com.comst19.dambom.core.database.download.DownloadTaskDao
import com.comst19.dambom.core.database.download.DownloadTaskEntity
import com.comst19.dambom.core.domain.model.PrivateVideoSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PrivateDownloadRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: DambomDatabase
    private lateinit var files: TrackingFileStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database =
            Room
                .inMemoryDatabaseBuilder(context, DambomDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        files = TrackingFileStore(context)
    }

    @After
    fun tearDown() {
        database.close()
        context.filesDir.resolve("videos").deleteRecursively()
        context.filesDir.resolve("private_videos").deleteRecursively()
    }

    @Test
    @Suppress("LongMethod")
    fun `private videos persist and are excluded from ordinary queries even after authentication`() =
        runTest {
            val session = PrivateVideoSession()
            val dao = database.downloadTaskDao()
            val source = completedEntity("private-video")
            dao.insert(source)
            val publicFile = createVideoFile(source)
            val repository = createRepository(dao, session, testScheduler)

            assertTrue(runCatching { repository.setPrivate(setOf(source.id), true) }.isFailure)
            session.unlock()
            repository.setPrivate(setOf(source.id), true)
            assertEquals(source.copy(isPrivate = true), dao.getById(source.id))
            assertFalse(publicFile.exists())
            assertTrue(repository.downloads.first().isEmpty())
            assertTrue(repository.completedDownloads.first().isEmpty())
            val privateTask = repository.privateCompletedDownloads.first().single()
            assertEquals(source.id, privateTask.id)
            assertTrue(privateTask.localFilePath?.contains("/private_videos/") == true)
            assertEquals(source.id, repository.observeDownload(source.id).first()?.id)
            session.lock()
            assertTrue(repository.privateCompletedDownloads.first().isEmpty())
            assertNull(repository.observeDownload(source.id).first())
            assertTrue(runCatching { repository.toggleFavorite(source.id) }.isFailure)
            assertTrue(runCatching { repository.delete(source.id) }.isFailure)
            session.unlock()
            repository.setPrivate(setOf(source.id), false)
            assertEquals(source, dao.getById(source.id))
            assertTrue(publicFile.isFile)
            assertEquals(
                source.id,
                repository.completedDownloads
                    .first()
                    .single()
                    .id,
            )
        }

    @Test
    fun `cancellation before database commit rolls back moved private files`() =
        runTest {
            val session = PrivateVideoSession().apply { unlock() }
            val realDao = database.downloadTaskDao()
            val source = completedEntity("cancel-private")
            realDao.insert(source)
            val publicFile = createVideoFile(source)
            publicFile.videoSubtitleFile().writeText("subtitle")
            val cancellingDao =
                object : DownloadTaskDao by realDao {
                    override suspend fun setPrivate(
                        ids: Set<String>,
                        isPrivate: Boolean,
                    ): Unit = throw CancellationException("cancelled before commit")
                }
            val repository = createRepository(cancellingDao, session, testScheduler)

            val thrown = runCatching { repository.setPrivate(setOf(source.id), true) }.exceptionOrNull()

            assertTrue(thrown is CancellationException)
            assertFalse(requireNotNull(realDao.getById(source.id)).isPrivate)
            assertTrue(publicFile.isFile)
            assertTrue(publicFile.videoSubtitleFile().isFile)
            assertFalse(privateFileFor(source).exists())
            assertFalse(privateFileFor(source).videoSubtitleFile().exists())
        }

    @Test
    fun `repeated private request cancellation rolls back only newly moved sidecar and keeps private video`() =
        runTest {
            val session = PrivateVideoSession().apply { unlock() }
            val realDao = database.downloadTaskDao()
            val source = completedEntity("already-private", isPrivate = true)
            realDao.insert(source)
            val publicFile = createVideoFile(source)
            assertTrue(files.movePrivacyFiles(publicFile.name, toPrivate = true) is PrivacyFileMoveResult.Moved)
            publicFile.videoSubtitleFile().writeText("leftover-subtitle")
            val cancellingDao =
                object : DownloadTaskDao by realDao {
                    override suspend fun setPrivate(
                        ids: Set<String>,
                        isPrivate: Boolean,
                    ): Unit = throw CancellationException("cancel repeated request")
                }
            val repository = createRepository(cancellingDao, session, testScheduler)

            val thrown = runCatching { repository.setPrivate(setOf(source.id), true) }.exceptionOrNull()

            assertTrue(thrown is CancellationException)
            assertTrue(requireNotNull(realDao.getById(source.id)).isPrivate)
            assertTrue(privateFileFor(source).isFile)
            assertFalse(publicFile.exists())
            assertTrue(publicFile.videoSubtitleFile().isFile)
            assertFalse(privateFileFor(source).videoSubtitleFile().exists())
        }

    @Test
    fun `recovery restores interrupted move and updates subscribed list and detail flows`() =
        runTest {
            val session = PrivateVideoSession()
            val dao = database.downloadTaskDao()
            val source = completedEntity("crash-before-commit", isPrivate = false)
            dao.insert(source)
            val publicFile = createVideoFile(source)
            publicFile.videoSubtitleFile().writeText("subtitle")
            assertTrue(files.movePrivacyFiles(publicFile.name, toPrivate = true) is PrivacyFileMoveResult.Moved)
            val repository = createRepository(dao, session, testScheduler)

            repository.completedDownloads.test {
                val listTurbine = this
                repository.observeDownload(source.id).test {
                    assertNull(awaitItem()?.localFilePath)
                    assertNull(listTurbine.awaitItem().single().localFilePath)

                    repository.recoverPendingDownloads()

                    assertEquals(publicFile.canonicalPath, awaitItem()?.localFilePath)
                    assertEquals(publicFile.canonicalPath, listTurbine.awaitItem().single().localFilePath)
                    assertTrue(publicFile.isFile)
                    assertTrue(publicFile.videoSubtitleFile().isFile)
                    assertFalse(privateFileFor(source).exists())
                    assertFalse(privateFileFor(source).videoSubtitleFile().exists())
                    cancelAndIgnoreRemainingEvents()
                }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `recovery migrates legacy private files and updates subscribed private flows`() =
        runTest {
            val session = PrivateVideoSession().apply { unlock() }
            val dao = database.downloadTaskDao()
            val source = completedEntity("legacy-private", isPrivate = true)
            dao.insert(source)
            val publicFile = createVideoFile(source)
            publicFile.videoThumbnailFile().writeText("thumb")
            val repository = createRepository(dao, session, testScheduler)

            repository.privateCompletedDownloads.test {
                val listTurbine = this
                repository.observeDownload(source.id).test {
                    assertEquals(publicFile.canonicalPath, awaitItem()?.localFilePath)
                    assertEquals(publicFile.canonicalPath, listTurbine.awaitItem().single().localFilePath)

                    repository.recoverPendingDownloads()

                    val expectedPrivatePath = privateFileFor(source).canonicalPath
                    assertEquals(expectedPrivatePath, awaitItem()?.localFilePath)
                    assertEquals(expectedPrivatePath, listTurbine.awaitItem().single().localFilePath)
                    assertTrue(privateFileFor(source).isFile)
                    assertTrue(privateFileFor(source).videoThumbnailFile().isFile)
                    assertFalse(publicFile.exists())
                    assertFalse(publicFile.videoThumbnailFile().exists())
                    assertEquals(listOf(publicFile.name), files.revokedFiles)
                    cancelAndIgnoreRemainingEvents()
                }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `concurrent recovery and setPrivate reconciles against latest database state`() =
        runTest {
            val session = PrivateVideoSession().apply { unlock() }
            val realDao = database.downloadTaskDao()
            val source = completedEntity("concurrent-video", isPrivate = false)
            realDao.insert(source)
            val publicFile = createVideoFile(source)
            publicFile.videoSubtitleFile().writeText("subtitle")

            val recoveryReadSnapshot = CompletableDeferred<Unit>()
            val proceedRecovery = CompletableDeferred<Unit>()
            val coordinatedDao =
                object : DownloadTaskDao by realDao {
                    override suspend fun getCompleted(): List<DownloadTaskEntity> =
                        realDao.getCompleted().also {
                            recoveryReadSnapshot.complete(Unit)
                            proceedRecovery.await()
                        }
                }
            val repository = createRepository(coordinatedDao, session, testScheduler)

            val recoveryJob = async { repository.recoverPendingDownloads() }
            recoveryReadSnapshot.await()

            repository.setPrivate(setOf(source.id), true)
            proceedRecovery.complete(Unit)
            recoveryJob.await()

            assertTrue(requireNotNull(realDao.getById(source.id)).isPrivate)
            assertTrue(privateFileFor(source).isFile)
            assertTrue(privateFileFor(source).videoSubtitleFile().isFile)
            assertFalse(publicFile.exists())
            assertFalse(publicFile.videoSubtitleFile().exists())
        }

    private fun createRepository(
        dao: DownloadTaskDao,
        session: PrivateVideoSession,
        scheduler: TestCoroutineScheduler,
    ): DefaultDownloadRepository =
        DefaultDownloadRepository(
            dao = dao,
            scheduler = NoOpScheduler,
            fileStore = files,
            ioDispatcher = StandardTestDispatcher(scheduler),
            privateVideoSession = session,
        )

    private fun createVideoFile(entity: DownloadTaskEntity): File =
        files.completedFile(entity.id, entity.url, entity.mimeType).apply {
            writeText("video-bytes")
        }

    private fun privateFileFor(entity: DownloadTaskEntity): File =
        context.filesDir.resolve("private_videos").resolve(requireNotNull(entity.localFileName))
}

private class TrackingFileStore(
    context: Context,
) : DownloadFileStore(context) {
    val revokedFiles = mutableListOf<String>()

    override fun revokeSharing(localFileName: String) {
        revokedFiles += localFileName
    }
}

private object NoOpScheduler : DownloadWorkScheduler {
    override suspend fun schedule() = Unit

    override suspend fun ensureScheduled() = Unit

    override suspend fun reschedule() = Unit
}

private fun completedEntity(
    id: String,
    isPrivate: Boolean = false,
): DownloadTaskEntity =
    DownloadTaskEntity(
        id = id,
        url = "https://example.com/$id.mp4",
        sourcePageUrl = "https://example.com",
        host = "example.com",
        title = id,
        mimeType = "video/mp4",
        expectedBytes = 100,
        downloadedBytes = 100,
        quality = "original",
        status = "COMPLETED",
        failureReason = null,
        retryCount = 0,
        deletePending = false,
        localFileName = "$id.mp4",
        createdAtMillis = 1,
        updatedAtMillis = 2,
        isPrivate = isPrivate,
    )
