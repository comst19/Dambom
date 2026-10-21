package com.comst19.dambom.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.comst19.dambom.core.database.download.DownloadTaskDao
import com.comst19.dambom.core.database.download.DownloadTaskEntity
import com.comst19.dambom.core.database.playback.PlaybackHistoryDao
import com.comst19.dambom.core.database.playback.PlaybackHistoryEntity
import com.comst19.dambom.core.database.tag.VideoTagAssignmentEntity
import com.comst19.dambom.core.database.tag.VideoTagDao
import com.comst19.dambom.core.database.tag.VideoTagEntity

@Database(
    entities = [
        DownloadTaskEntity::class,
        VideoTagEntity::class,
        VideoTagAssignmentEntity::class,
        PlaybackHistoryEntity::class,
    ],
    version = STREAM_DATABASE_VERSION,
    exportSchema = true,
)
abstract class DambomDatabase : RoomDatabase() {
    abstract fun downloadTaskDao(): DownloadTaskDao

    abstract fun videoTagDao(): VideoTagDao

    abstract fun playbackHistoryDao(): PlaybackHistoryDao

    internal companion object {
        val MIGRATION_6_7 =
            object : Migration(PLAYBACK_DATABASE_VERSION, STREAM_DATABASE_VERSION) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE download_tasks ADD COLUMN requestHeaders TEXT NOT NULL DEFAULT '{}'")
                    db.execSQL("ALTER TABLE download_tasks ADD COLUMN videoHeight INTEGER")
                }
            }

        val MIGRATION_5_6 =
            object : Migration(TAG_DATABASE_VERSION, PLAYBACK_DATABASE_VERSION) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS playback_history " +
                            "(downloadId TEXT NOT NULL, positionMillis INTEGER NOT NULL, " +
                            "durationMillis INTEGER NOT NULL, lastPlayedAtMillis INTEGER NOT NULL, " +
                            "PRIMARY KEY(downloadId), FOREIGN KEY(downloadId) REFERENCES download_tasks(id) " +
                            "ON UPDATE NO ACTION ON DELETE CASCADE)",
                    )
                }
            }

        val MIGRATION_4_5 =
            object : Migration(DOWNLOAD_INDEX_DATABASE_VERSION, TAG_DATABASE_VERSION) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS video_tags " +
                            "(id TEXT NOT NULL, name TEXT NOT NULL, PRIMARY KEY(id))",
                    )
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_video_tags_name ON video_tags(name)")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS video_tag_assignments " +
                            "(videoId TEXT NOT NULL, tagId TEXT NOT NULL, " +
                            "PRIMARY KEY(videoId, tagId), " +
                            "FOREIGN KEY(videoId) REFERENCES download_tasks(id) " +
                            "ON UPDATE NO ACTION ON DELETE CASCADE, " +
                            "FOREIGN KEY(tagId) REFERENCES video_tags(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_video_tag_assignments_tagId " +
                            "ON video_tag_assignments(tagId)",
                    )
                }
            }

        val MIGRATION_3_4 =
            object : Migration(FAVORITES_DATABASE_VERSION, DOWNLOAD_INDEX_DATABASE_VERSION) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_download_tasks_status_deletePending_createdAtMillis " +
                            "ON download_tasks(status, deletePending, createdAtMillis)",
                    )
                }
            }

        val MIGRATION_2_3 =
            object : Migration(2, FAVORITES_DATABASE_VERSION) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE download_tasks ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
                }
            }

        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE download_tasks ADD COLUMN retryCount INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE download_tasks ADD COLUMN deletePending INTEGER NOT NULL DEFAULT 0")
                }
            }
    }
}

private const val FAVORITES_DATABASE_VERSION = 3
private const val DOWNLOAD_INDEX_DATABASE_VERSION = 4
private const val TAG_DATABASE_VERSION = 5

private const val PLAYBACK_DATABASE_VERSION = 6

private const val STREAM_DATABASE_VERSION = 7
