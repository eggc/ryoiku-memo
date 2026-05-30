package net.eggc.ryoikumemo.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TimelineStampDao {
    @Query(
        """
        SELECT * FROM timeline_stamps
        WHERE owner_id = :ownerId
          AND note_id = :noteId
          AND timestamp >= :startTimestamp
          AND timestamp < :endTimestamp
        ORDER BY timestamp DESC
        """
    )
    fun observeByMonth(
        ownerId: String,
        noteId: String,
        startTimestamp: Long,
        endTimestamp: Long,
    ): Flow<List<TimelineStampEntity>>

    @Query(
        """
        SELECT * FROM timeline_stamps
        WHERE owner_id = :ownerId
          AND note_id = :noteId
          AND timestamp >= :startTimestamp
          AND timestamp < :endTimestamp
        ORDER BY timestamp DESC
        """
    )
    suspend fun listByMonth(
        ownerId: String,
        noteId: String,
        startTimestamp: Long,
        endTimestamp: Long,
    ): List<TimelineStampEntity>

    @Query(
        """
        SELECT * FROM timeline_stamps
        WHERE owner_id = :ownerId
          AND note_id = :noteId
          AND timestamp = :timestamp
        LIMIT 1
        """
    )
    suspend fun getByTimestamp(
        ownerId: String,
        noteId: String,
        timestamp: Long,
    ): TimelineStampEntity?

    @Upsert
    suspend fun upsert(entity: TimelineStampEntity)

    @Upsert
    suspend fun upsertAll(entities: List<TimelineStampEntity>)

    @Query(
        """
        DELETE FROM timeline_stamps
        WHERE owner_id = :ownerId
          AND note_id = :noteId
          AND timestamp = :timestamp
        """
    )
    suspend fun deleteByTimestamp(
        ownerId: String,
        noteId: String,
        timestamp: Long,
    )
}
