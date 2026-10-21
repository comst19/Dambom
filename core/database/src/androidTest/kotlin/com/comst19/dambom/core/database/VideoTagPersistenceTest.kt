package com.comst19.dambom.core.database

import androidx.room.Room
import androidx.room.migration.AutoMigrationSpec
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.comst19.dambom.core.database.tag.VideoTagEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VideoTagPersistenceTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            DambomDatabase::class.java,
            emptyList<AutoMigrationSpec>(),
        )

    @Test
    fun tagsAndAssignmentsSurviveMigrationAndReopening() =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val name = "tag-persistence-test"
            helper.createDatabase(name, 4).apply {
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
            helper.runMigrationsAndValidate(name, 5, true, DambomDatabase.MIGRATION_4_5).close()
            val first =
                Room
                    .databaseBuilder(context, DambomDatabase::class.java, name)
                    .addMigrations(DambomDatabase.MIGRATION_5_6, DambomDatabase.MIGRATION_6_7)
                    .build()
            try {
                val tags = first.videoTagDao()
                tags.insert(VideoTagEntity("tag", "Travel"))
                tags.assign(setOf("video", "missing"), "tag", true)
                tags.rename("tag", "Trips")
            } finally {
                first.close()
            }
            val reopened =
                Room
                    .databaseBuilder(context, DambomDatabase::class.java, name)
                    .addMigrations(DambomDatabase.MIGRATION_5_6, DambomDatabase.MIGRATION_6_7)
                    .build()
            try {
                val tags = reopened.videoTagDao()
                assertEquals(listOf(VideoTagEntity("tag", "Trips")), tags.observeTags().first())
                assertEquals(1, tags.observeAssignments().first().size)
                val video = requireNotNull(reopened.downloadTaskDao().getById("video"))
                assertEquals("Saved video", video.title)
                assertTrue(video.isFavorite)
                tags.delete("tag")
                assertTrue(tags.observeAssignments().first().isEmpty())
                assertEquals(video, reopened.downloadTaskDao().getById("video"))
            } finally {
                reopened.close()
                context.deleteDatabase(name)
            }
        }
}
