package com.comst19.dambom.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SampleDaoTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    AppDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `upsert observe and delete use real Room database`() =
        runTest {
            database.sampleDao().observeAll().test {
                assertEquals(emptyList<SampleEntity>(), awaitItem())
                database.sampleDao().upsertAll(listOf(SampleEntity(1, "Title", "Description")))
                assertEquals(1L, awaitItem().single().id)
                database.sampleDao().deleteAll()
                assertEquals(emptyList<SampleEntity>(), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `replace all emits only the committed rows`() =
        runTest {
            val dao = database.sampleDao()
            dao.upsertAll(listOf(SampleEntity(1, "Old", "Value")))

            dao.observeAll().test {
                assertEquals(listOf(1L), awaitItem().map(SampleEntity::id))

                dao.replaceAll(
                    listOf(
                        SampleEntity(2, "New", "First"),
                        SampleEntity(3, "New", "Second"),
                    ),
                )

                assertEquals(listOf(2L, 3L), awaitItem().map(SampleEntity::id))
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DatabaseMigrationTest {
    @Test
    fun `migration 1 to 2 adds synced timestamp with default`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test.db"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { database ->
            database.execSQL(
                """
                CREATE TABLE samples (
                    id INTEGER NOT NULL PRIMARY KEY,
                    title TEXT NOT NULL,
                    description TEXT NOT NULL
                )
                """.trimIndent(),
            )
            database.execSQL("INSERT INTO samples VALUES (1, 'Title', 'Description')")
            database.version = 1
        }

        val migrated =
            Room
                .databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2)
                .allowMainThreadQueries()
                .build()
        val cursor =
            migrated.openHelper.readableDatabase.query(
                "SELECT syncedAtEpochMillis FROM samples WHERE id = 1",
            )
        cursor.use {
            it.moveToFirst()
            assertEquals(0L, it.getLong(0))
        }
        migrated.close()
        context.deleteDatabase(name)
    }
}
