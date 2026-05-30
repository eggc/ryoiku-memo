package net.eggc.ryoikumemo.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "timeline_sync_state",
    primaryKeys = ["owner_id", "note_id", "month_key"]
)
data class TimelineSyncStateEntity(
    @ColumnInfo(name = "owner_id")
    val ownerId: String,
    @ColumnInfo(name = "note_id")
    val noteId: String,
    @ColumnInfo(name = "month_key")
    val monthKey: String,
    @ColumnInfo(name = "last_polled_at")
    val lastPolledAt: Long,
    @ColumnInfo(name = "last_server_sync_at")
    val lastServerSyncAt: Long,
)
