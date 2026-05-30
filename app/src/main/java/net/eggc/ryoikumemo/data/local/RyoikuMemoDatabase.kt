package net.eggc.ryoikumemo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TimelineStampEntity::class,
        TimelineSyncStateEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class RyoikuMemoDatabase : RoomDatabase() {
    abstract fun timelineStampDao(): TimelineStampDao
    abstract fun timelineSyncStateDao(): TimelineSyncStateDao

    companion object {
        @Volatile
        private var instance: RyoikuMemoDatabase? = null

        fun getInstance(context: Context): RyoikuMemoDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RyoikuMemoDatabase::class.java,
                    "ryoiku_memo.db",
                ).build().also { instance = it }
            }
        }
    }
}
