package com.comst19.dambom.core.database

import androidx.room.Room
import androidx.room.migration.AutoMigrationSpec
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackHistoryPersistenceTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            DambomDatabase::class.java,
            emptyList<AutoMigrationSpec>(),
        )

    @Test
    fun historySurvivesReopeningRejectsStaleWritesAndCascadesOnDeletion() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val name = "playback-history-persistence-test"
            helper.createDatabase(name, 5).apply {
                execSQL(
                    """
                    INSERT INTO download_tasks (
                        id, url, sourcePageUrl, host, title, mimeType, expectedBytes, downloadedBytes,
                        quality, status, failureReason, retryCount, deletePending, localFileName,
                        createdAtMillis, updatedAtMillis, isFavorite
                    ) VALUES (
                        'video', 'https://example.com/video.mp4', 'https://example.com', 'example.com',
                        'Saved video', 'video/mp4', 100, 100, '720p', 'COMPLETED', NULL, 0, 0,
                        'video.mp4', 10, 20, 1
                    )
                    """.trimIndent(),
                )
                close()
            }
            helper.runMigrationsAndValidate(name, 6, true, DambomDatabase.MIGRATION_5_6).close()
            val first = Room.databaseBuilder(context, DambomDatabase::class.java, name).addMigrations(DambomDatabase.MIGRATION_6_7).build()
            try {
                assertTrue(
                    first
                        .playbackHistoryDao()
                        .observeHistory()
                        .first()
                        .isEmpty(),
                )
                first.playbackHistoryDao().saveProgress("video", 12_000, 60_000, 100)
                first.playbackHistoryDao().saveProgress("video", 15_000, 60_000, 100)
                first.playbackHistoryDao().saveProgress("video", 1_000, 60_000, 90)
                first.playbackHistoryDao().saveProgress("missing", 1_000, 60_000, 200)
            } finally {
                first.close()
            }
            val reopened =
                Room
                    .databaseBuilder(
                        context,
                        DambomDatabase::class.java,
                        name,
                    ).addMigrations(DambomDatabase.MIGRATION_6_7)
                    .build()
            try {
                val row =
                    reopened
                        .playbackHistoryDao()
                        .observeHistory()
                        .first()
                        .single()
                assertEquals(row, reopened.playbackHistoryDao().getHistory("video"))
                assertNull(reopened.playbackHistoryDao().getHistory("missing"))
                assertEquals(15_000L, row.positionMillis)
                assertEquals(100L, row.lastPlayedAtMillis)
                assertEquals(20L, requireNotNull(reopened.downloadTaskDao().getById("video")).updatedAtMillis)
                reopened.downloadTaskDao().claimForDeletion("video", 30)
                reopened.playbackHistoryDao().saveProgress("video", 20_000, 60_000, 200)
                assertEquals(
                    row,
                    reopened
                        .playbackHistoryDao()
                        .observeHistory()
                        .first()
                        .single(),
                )
                reopened.downloadTaskDao().deleteClaimed("video")
                assertTrue(
                    reopened
                        .playbackHistoryDao()
                        .observeHistory()
                        .first()
                        .isEmpty(),
                )
            } finally {
                reopened.close()
                context.deleteDatabase(name)
            }
        }
}
