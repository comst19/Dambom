package com.comst19.dambom.core.database

import androidx.room.Room
import androidx.room.migration.AutoMigrationSpec
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DambomDatabaseMigrationTest {
    @Test
    fun queueIndexMigrationPreservesExistingDownloads() {
        val name = "queue-index-migration-test"
        helper.createDatabase(name, 3).apply {
            execSQL(
                """
                INSERT INTO download_tasks (
                    id, url, sourcePageUrl, host, title, mimeType, expectedBytes, downloadedBytes,
                    quality, status, failureReason, retryCount, deletePending, localFileName,
                    createdAtMillis, updatedAtMillis, isFavorite
                ) VALUES (
                    'queued', 'https://example.com/video.mp4', 'https://example.com', 'example.com',
                    'Keep title', 'video/mp4', 100, 25, 'original', 'QUEUED', NULL, 1, 0, NULL, 10, 20, 1
                )
                """.trimIndent(),
            )
            close()
        }
        helper.runMigrationsAndValidate(name, 4, true, DambomDatabase.MIGRATION_3_4).use { db ->
            db.query("SELECT title, downloadedBytes, retryCount, isFavorite FROM download_tasks WHERE id = 'queued'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Keep title", cursor.getString(0))
                assertEquals(25L, cursor.getLong(1))
                assertEquals(1, cursor.getInt(2))
                assertEquals(1, cursor.getInt(3))
            }
            db
                .query(
                    "EXPLAIN QUERY PLAN SELECT * FROM download_tasks " +
                        "WHERE status = 'QUEUED' AND deletePending = 0 ORDER BY createdAtMillis ASC",
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertTrue(cursor.getString(3).contains("index_download_tasks_status_deletePending_createdAtMillis"))
                }
        }
    }

    @Test
    fun favoritesSurviveReopeningAndDoNotChangeVideoMetadata() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val name = "favorites-persistence-test"
            helper.createDatabase(name, 2).apply {
                execSQL(
                    """
                    INSERT INTO download_tasks (
                        id, url, sourcePageUrl, host, title, mimeType, expectedBytes, downloadedBytes,
                        quality, status, failureReason, retryCount, deletePending, localFileName,
                        createdAtMillis, updatedAtMillis
                    ) VALUES (
                        'favorite', 'https://example.com/video.mp4', 'https://example.com', 'example.com',
                        'Saved video', 'video/mp4', 100, 100, '720p', 'COMPLETED', NULL, 0, 0,
                        'video.mp4', 10, 20
                    )
                    """.trimIndent(),
                )
                close()
            }
            helper.runMigrationsAndValidate(name, 4, true, DambomDatabase.MIGRATION_2_3, DambomDatabase.MIGRATION_3_4).close()
            val first = Room.databaseBuilder(context, DambomDatabase::class.java, name).build()
            try {
                val dao = first.downloadTaskDao()
                val original = requireNotNull(dao.getById("favorite"))
                assertEquals(false, original.isFavorite)
                assertEquals(1, dao.toggleFavorite(original.id))
                assertEquals(original.copy(isFavorite = true), dao.getById(original.id))
            } finally {
                first.close()
            }
            val reopened = Room.databaseBuilder(context, DambomDatabase::class.java, name).build()
            try {
                val dao = reopened.downloadTaskDao()
                assertTrue(requireNotNull(dao.getById("favorite")).isFavorite)
                assertEquals(1, dao.toggleFavorite("favorite"))
                assertEquals(false, requireNotNull(dao.getById("favorite")).isFavorite)
                dao.claimForDeletion("favorite", 30)
                assertEquals(0, dao.toggleFavorite("favorite"))
                assertEquals(0, dao.toggleFavorite("missing"))
            } finally {
                reopened.close()
                context.deleteDatabase(name)
            }
        }

    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            DambomDatabase::class.java,
            emptyList<AutoMigrationSpec>(),
        )

    @Test
    fun migratesVersionOneDownloadTaskWithRetryAndDeletionDefaults() {
        helper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL(
                """
                INSERT INTO download_tasks (
                    id, url, sourcePageUrl, host, title, mimeType, expectedBytes, downloadedBytes,
                    quality, status, failureReason, localFileName, createdAtMillis, updatedAtMillis
                ) VALUES (
                    'task', 'https://media.example/video.mp4', 'https://media.example', 'media.example',
                    'video', 'video/mp4', 100, 10, 'original', 'QUEUED', NULL, NULL, 1, 1
                )
                """.trimIndent(),
            )
            close()
        }

        helper
            .runMigrationsAndValidate(TEST_DATABASE, 2, true, DambomDatabase.MIGRATION_1_2)
            .query("SELECT retryCount, deletePending FROM download_tasks WHERE id = 'task'")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
                assertEquals(0, cursor.getInt(1))
            }
    }
}

private const val TEST_DATABASE = "dambom-migration-test"
