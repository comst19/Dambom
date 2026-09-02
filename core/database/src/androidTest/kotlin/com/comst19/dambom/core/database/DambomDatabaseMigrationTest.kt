package com.comst19.dambom.core.database

import androidx.room.migration.AutoMigrationSpec
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DambomDatabaseMigrationTest {
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
