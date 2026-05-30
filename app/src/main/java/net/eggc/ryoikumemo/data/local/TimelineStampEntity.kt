package net.eggc.ryoikumemo.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "timeline_stamps",
    primaryKeys = ["owner_id", "note_id", "timestamp"]
)
data class TimelineStampEntity(
    @ColumnInfo(name = "owner_id")
    val ownerId: String,
    @ColumnInfo(name = "note_id")
    val noteId: String,
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,
    @ColumnInfo(name = "type")
    val type: String,
    @ColumnInfo(name = "note")
    val note: String,
    @ColumnInfo(name = "operator_name")
    val operatorName: String?,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long?,
)
