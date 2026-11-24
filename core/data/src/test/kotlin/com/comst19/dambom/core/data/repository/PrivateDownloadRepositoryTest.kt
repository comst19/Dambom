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
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PrivateDownloadRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: DambomDatabase
    private lateinit var files: TrackingFileStore
    private lateinit var scheduler: CountingScheduler

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database =
            Room
                .inMemoryDatabaseBuilder(context, DambomDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        files = TrackingFileStore(context)
        scheduler = CountingScheduler()
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

    @Test
    fun `recovery preserves intact original over partial copy in both public and private directions`() =
        runTest {
            val session = PrivateVideoSession().apply { unlock() }
            val dao = database.downloadTaskDao()
            val publicRow = completedEntity("public-partial", isPrivate = false)
            val privateRow = completedEntity("private-partial", isPrivate = true)
            dao.insert(publicRow)
            dao.insert(privateRow)

            val publicOriginal = createVideoFile(publicRow, content = "intact-11b!")
            val publicPartialCopy = createPrivateVideoFile(publicRow, content = "int")
            val privateOriginal = createPrivateVideoFile(privateRow, content = "secret-11b!")
            val privatePartialCopy = createVideoFile(privateRow, content = "sec")
            val repository = createRepository(dao, session, testScheduler)

            repository.recoverPendingDownloads()
            repository.recoverPendingDownloads()

            assertFalse(requireNotNull(dao.getById(publicRow.id)).isPrivate)
            assertTrue(publicOriginal.isFile)
            assertEquals("intact-11b!", publicOriginal.readText())
            assertFalse(publicPartialCopy.exists())
            val publicTask = repository.completedDownloads.first().single()
            assertEquals(publicOriginal.canonicalPath, publicTask.localFilePath)
            assertEquals(publicOriginal.canonicalPath, repository.observeDownload(publicRow.id).first()?.localFilePath)

            assertTrue(requireNotNull(dao.getById(privateRow.id)).isPrivate)
            assertTrue(privateOriginal.isFile)
            assertEquals("secret-11b!", privateOriginal.readText())
            assertFalse(privatePartialCopy.exists())
            val privateTask = repository.privateCompletedDownloads.first().single()
            assertEquals(privateOriginal.canonicalPath, privateTask.localFilePath)
            assertEquals(
                privateOriginal.canonicalPath,
                repository.observeDownload(privateRow.id).first()?.localFilePath,
            )
        }

    @Test
    fun `setPrivate fails and preserves database when video file is missing from both folders`() =
        runTest {
            val session = PrivateVideoSession().apply { unlock() }
            val dao = database.downloadTaskDao()
            val source = completedEntity("missing-both", isPrivate = false)
            dao.insert(source)
            val repository = createRepository(dao, session, testScheduler)

            val thrown = runCatching { repository.setPrivate(setOf(source.id), true) }.exceptionOrNull()

            assertTrue(thrown is IOException)
            assertFalse(requireNotNull(dao.getById(source.id)).isPrivate)
        }

    @Test
    fun `setPrivate fails and preserves database when only subtitle exists without video file`() =
        runTest {
            val session = PrivateVideoSession().apply { unlock() }
            val dao = database.downloadTaskDao()
            val source = completedEntity("subtitle-only", isPrivate = false)
            dao.insert(source)
            val orphanSubtitle = files.completedFile(source.id, source.url, source.mimeType).videoSubtitleFile()
            orphanSubtitle.writeText("orphan-subtitle")
            val repository = createRepository(dao, session, testScheduler)

            val thrown = runCatching { repository.setPrivate(setOf(source.id), true) }.exceptionOrNull()

            assertTrue(thrown is IOException)
            assertFalse(requireNotNull(dao.getById(source.id)).isPrivate)
            assertTrue(orphanSubtitle.isFile)
            assertFalse(privateFileFor(source).videoSubtitleFile().exists())
        }

    @Test
    fun `setPrivate preserves target video and moves leftover subtitle when only subtitle remains in source`() =
        runTest {
            val session = PrivateVideoSession().apply { unlock() }
            val dao = database.downloadTaskDao()
            val source = completedEntity("target-has-video", isPrivate = false)
            dao.insert(source)
            val privateVideo = createPrivateVideoFile(source, content = "already-at-target")
            val leftoverSubtitle = files.completedFile(source.id, source.url, source.mimeType).videoSubtitleFile()
            leftoverSubtitle.writeText("source-subtitle")
            val repository = createRepository(dao, session, testScheduler)

            repository.setPrivate(setOf(source.id), true)

            assertTrue(requireNotNull(dao.getById(source.id)).isPrivate)
            assertTrue(privateVideo.isFile)
            assertEquals("already-at-target", privateVideo.readText())
            assertFalse(leftoverSubtitle.exists())
            assertTrue(privateVideo.videoSubtitleFile().isFile)
            assertEquals("source-subtitle", privateVideo.videoSubtitleFile().readText())
        }

    @Test
    @Suppress("LongMethod")
    fun `recovery reports failure while migrating healthy legacy item and succeeds on retry`() =
        runTest {
            val session = PrivateVideoSession().apply { unlock() }
            val dao = database.downloadTaskDao()
            val blocked = completedEntity("legacy-blocked", isPrivate = true)
            val healthy = completedEntity("legacy-healthy", isPrivate = true)
            val queued =
                completedEntity("queued-work", isPrivate = false).copy(
                    status = "QUEUED",
                    localFileName = null,
                )
            dao.insert(blocked)
            dao.insert(healthy)
            dao.insert(queued)
            val blockedPublicFile = createVideoFile(blocked, content = "blocked-video")
            blockedPublicFile.videoSubtitleFile().writeText("blocked-sub")
            val healthyPublicFile = createVideoFile(healthy, content = "healthy-video")
            val blockingDir =
                privateFileFor(blocked).apply {
                    mkdirs()
                    resolve("blocker").writeText("lock")
                }
            val repository = createRepository(dao, session, testScheduler)

            repository.privateCompletedDownloads.test {
                val listTurbine = this
                repository.observeDownload(blocked.id).test {
                    assertEquals(blockedPublicFile.canonicalPath, awaitItem()?.localFilePath)
                    val initialList = listTurbine.awaitItem().associateBy { it.id }
                    assertEquals(blockedPublicFile.canonicalPath, initialList[blocked.id]?.localFilePath)
                    assertEquals(healthyPublicFile.canonicalPath, initialList[healthy.id]?.localFilePath)

                    val firstError = runCatching { repository.recoverPendingDownloads() }.exceptionOrNull()

                    assertTrue(firstError is IOException)
                    assertEquals(1, scheduler.ensureCount)
                    assertTrue(privateFileFor(healthy).isFile)
                    assertFalse(healthyPublicFile.exists())
                    assertTrue(blockedPublicFile.isFile)
                    assertTrue(blockedPublicFile.videoSubtitleFile().isFile)
                    val afterFirstRun = listTurbine.awaitItem().associateBy { it.id }
                    assertEquals(privateFileFor(healthy).canonicalPath, afterFirstRun[healthy.id]?.localFilePath)
                    assertEquals(blockedPublicFile.canonicalPath, afterFirstRun[blocked.id]?.localFilePath)

                    blockingDir.deleteRecursively()
                    repository.recoverPendingDownloads()

                    val expectedBlockedPrivate = privateFileFor(blocked).canonicalPath
                    assertEquals(expectedBlockedPrivate, awaitItem()?.localFilePath)
                    val afterRetry = listTurbine.awaitItem().associateBy { it.id }
                    assertEquals(expectedBlockedPrivate, afterRetry[blocked.id]?.localFilePath)
                    assertTrue(privateFileFor(blocked).isFile)
                    assertTrue(privateFileFor(blocked).videoSubtitleFile().isFile)
                    assertFalse(blockedPublicFile.exists())
                    assertFalse(blockedPublicFile.videoSubtitleFile().exists())
                    cancelAndIgnoreRemainingEvents()
                }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    @Suppress("LongMethod")
    fun `recovery removes stale move temp files in both directions and preserves intact originals`() =
        runTest {
            val session = PrivateVideoSession().apply { unlock() }
            val dao = database.downloadTaskDao()
            val privateRow = completedEntity("private-tmp", isPrivate = true)
            val publicRow = completedEntity("public-tmp", isPrivate = false)
            dao.insert(privateRow)
            dao.insert(publicRow)

            val privateOriginal = createPrivateVideoFile(privateRow, content = "secret-11b!")
            val publicOriginal = createVideoFile(publicRow, content = "intact-11b!")
            val videosDir = checkNotNull(publicOriginal.parentFile)
            val privateDir = checkNotNull(privateOriginal.parentFile)

            val privateVideoTempInPublic =
                videosDir.resolve(".${privateOriginal.name}.1001.move.tmp").apply { writeText("sec") }
            val privateSubtitleTempInPublic =
                videosDir.resolve(".${privateOriginal.videoSubtitleFile().name}.1002.move.tmp").apply {
                    writeText("sub")
                }
            val publicVideoTempInPrivate =
                privateDir.resolve(".${publicOriginal.name}.2001.move.tmp").apply { writeText("int") }
            val publicThumbTempInPrivate =
                privateDir.resolve(".${publicOriginal.videoThumbnailFile().name}.2002.move.tmp").apply {
                    writeText("th")
                }
            val repository = createRepository(dao, session, testScheduler)

            repository.recoverPendingDownloads()

            assertFalse(privateVideoTempInPublic.exists())
            assertFalse(privateSubtitleTempInPublic.exists())
            assertFalse(publicVideoTempInPrivate.exists())
            assertFalse(publicThumbTempInPrivate.exists())

            repository.recoverPendingDownloads()

            assertTrue(requireNotNull(dao.getById(privateRow.id)).isPrivate)
            assertEquals("secret-11b!", privateOriginal.readText())
            val privateTask = repository.privateCompletedDownloads.first().single()
            assertEquals(privateOriginal.canonicalPath, privateTask.localFilePath)
            assertEquals(
                privateOriginal.canonicalPath,
                repository.observeDownload(privateRow.id).first()?.localFilePath,
            )

            assertFalse(requireNotNull(dao.getById(publicRow.id)).isPrivate)
            assertEquals("intact-11b!", publicOriginal.readText())
            val publicTask = repository.completedDownloads.first().single()
            assertEquals(publicOriginal.canonicalPath, publicTask.localFilePath)
            assertEquals(
                publicOriginal.canonicalPath,
                repository.observeDownload(publicRow.id).first()?.localFilePath,
            )
        }

    @Test
    @Suppress("LongMethod")
    fun `delete removes target move temp files across both directories without touching similar videos`() =
        runTest {
            val session = PrivateVideoSession().apply { unlock() }
            val dao = database.downloadTaskDao()
            val target = completedEntity("target", isPrivate = true)
            val similar = completedEntity("target-other", isPrivate = true)
            dao.insert(target)
            dao.insert(similar)

            val targetPrivate = createPrivateVideoFile(target, content = "target-video")
            val targetSubtitle = targetPrivate.videoSubtitleFile().apply { writeText("target-sub") }
            val targetThumb = targetPrivate.videoThumbnailFile().apply { writeText("target-thumb") }
            val similarPrivate = createPrivateVideoFile(similar, content = "similar-video")
            val videosDir = context.filesDir.resolve("videos").apply(File::mkdirs)
            val privateDir = checkNotNull(targetPrivate.parentFile)

            val targetTempInPublic =
                videosDir.resolve(".${targetPrivate.name}.1001.move.tmp").apply { writeText("part") }
            val targetSubTempInPublic =
                videosDir.resolve(".${targetSubtitle.name}.1002.move.tmp").apply { writeText("part-sub") }
            val targetTempInPrivate =
                privateDir.resolve(".${targetPrivate.name}.1003.move.tmp").apply { writeText("part-priv") }
            val targetThumbTempInPrivate =
                privateDir.resolve(".${targetThumb.name}.1004.move.tmp").apply { writeText("part-thumb") }

            val similarTempInPublic =
                videosDir.resolve(".${similarPrivate.name}.2001.move.tmp").apply { writeText("keep-1") }
            val similarTempInPrivate =
                privateDir.resolve(".${similarPrivate.name}.2002.move.tmp").apply { writeText("keep-2") }
            val dotPrefixedOtherTemp =
                videosDir.resolve(".${targetPrivate.name}.copy.mp4.2003.move.tmp").apply { writeText("keep-3") }
            val repository = createRepository(dao, session, testScheduler)

            repository.delete(target.id)

            assertNull(dao.getById(target.id))
            assertFalse(targetPrivate.exists())
            assertFalse(targetSubtitle.exists())
            assertFalse(targetThumb.exists())
            assertFalse(targetTempInPublic.exists())
            assertFalse(targetSubTempInPublic.exists())
            assertFalse(targetTempInPrivate.exists())
            assertFalse(targetThumbTempInPrivate.exists())

            assertTrue(similarPrivate.isFile)
            assertEquals("similar-video", similarPrivate.readText())
            assertTrue(similarTempInPublic.isFile)
            assertTrue(similarTempInPrivate.isFile)
            assertTrue(dotPrefixedOtherTemp.isFile)
        }

    @Test
    fun `delete fails and retains database row when target move temp file cannot be deleted`() =
        runTest {
            val session = PrivateVideoSession().apply { unlock() }
            val dao = database.downloadTaskDao()
            val target = completedEntity("target-locked-tmp", isPrivate = true)
            dao.insert(target)
            val targetPrivate = createPrivateVideoFile(target, content = "target-video")
            val videosDir = context.filesDir.resolve("videos").apply(File::mkdirs)
            val undeletableTempDir =
                videosDir.resolve(".${targetPrivate.name}.9999.move.tmp").apply {
                    mkdirs()
                    resolve("blocker").writeText("lock")
                }
            val repository = createRepository(dao, session, testScheduler)

            val thrown = runCatching { repository.delete(target.id) }.exceptionOrNull()

            assertTrue(thrown is IOException)
            assertEquals(true, dao.getById(target.id)?.deletePending)
            assertTrue(undeletableTempDir.exists())
            undeletableTempDir.deleteRecursively()
        }

    private fun createRepository(
        dao: DownloadTaskDao,
        session: PrivateVideoSession,
        scheduler: TestCoroutineScheduler,
    ): DefaultDownloadRepository =
        DefaultDownloadRepository(
            dao = dao,
            scheduler = this.scheduler,
            fileStore = files,
            ioDispatcher = StandardTestDispatcher(scheduler),
            privateVideoSession = session,
        )

    private fun createVideoFile(
        entity: DownloadTaskEntity,
        content: String = "video-bytes",
    ): File =
        files.completedFile(entity.id, entity.url, entity.mimeType).apply {
            writeText(content)
        }

    private fun createPrivateVideoFile(
        entity: DownloadTaskEntity,
        content: String = "video-bytes",
    ): File =
        privateFileFor(entity).apply {
            parentFile?.mkdirs()
            writeText(content)
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

private class CountingScheduler : DownloadWorkScheduler {
    var ensureCount = 0

    override suspend fun schedule() = Unit

    override suspend fun ensureScheduled() {
        ensureCount++
    }

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
