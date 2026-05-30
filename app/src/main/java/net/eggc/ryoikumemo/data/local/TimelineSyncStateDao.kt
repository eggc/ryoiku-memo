package net.eggc.ryoikumemo.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface TimelineSyncStateDao {
    @Query(
        """
        SELECT * FROM timeline_sync_state
        WHERE owner_id = :ownerId
          AND note_id = :noteId
          AND month_key = :monthKey
        LIMIT 1
        """
    )
    suspend fun get(
        ownerId: String,
        noteId: String,
        monthKey: String,
    ): TimelineSyncStateEntity?

    @Upsert
    suspend fun upsert(state: TimelineSyncStateEntity)
}
