package net.eggc.ryoikumemo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TimelineStampEntity::class,
        TimelineSyncStateEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class RyoikuMemoDatabase : RoomDatabase() {
    abstract fun timelineStampDao(): TimelineStampDao
    abstract fun timelineSyncStateDao(): TimelineSyncStateDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS timeline_stamps_new (
                        owner_id TEXT NOT NULL,
                        note_id TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        note TEXT NOT NULL,
                        operator_name TEXT,
                        local_synced_at INTEGER,
                        remote_updated_at INTEGER,
                        PRIMARY KEY(owner_id, note_id, timestamp)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO timeline_stamps_new (
                        owner_id,
                        note_id,
                        timestamp,
                        type,
                        note,
                        operator_name,
                        local_synced_at,
                        remote_updated_at
                    )
                    SELECT
                        owner_id,
                        note_id,
                        timestamp,
                        type,
                        note,
                        operator_name,
                        updated_at,
                        NULL
                    FROM timeline_stamps
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE timeline_stamps")
                db.execSQL("ALTER TABLE timeline_stamps_new RENAME TO timeline_stamps")
            }
        }

        @Volatile
        private var instance: RyoikuMemoDatabase? = null

        fun getInstance(context: Context): RyoikuMemoDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RyoikuMemoDatabase::class.java,
                    "ryoiku_memo.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
