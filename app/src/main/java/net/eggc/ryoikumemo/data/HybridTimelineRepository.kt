package net.eggc.ryoikumemo.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import net.eggc.ryoikumemo.data.local.TimelineLocalDataSource
import java.time.LocalDate

class HybridTimelineRepository(
    private val remote: TimelineRepository,
    private val local: TimelineLocalDataSource,
) : TimelineRepository {

    override fun getTimelineItemsForMonthFlow(
        ownerId: String,
        noteId: String,
        dateInMonth: LocalDate,
    ): Flow<List<TimelineItem>> {
        return flow {
            val localFirst = local.listByMonth(ownerId, noteId, dateInMonth)
            emit(localFirst)

            var latest = localFirst
            remote.getTimelineItemsForMonthFlow(ownerId, noteId, dateInMonth).collect { remoteItems ->
                val remoteStamps = remoteItems.filterIsInstance<StampItem>()
                local.replaceMonth(ownerId, noteId, dateInMonth, remoteStamps)
                val updatedLocal = local.listByMonth(ownerId, noteId, dateInMonth)
                if (updatedLocal != latest) {
                    latest = updatedLocal
                    emit(updatedLocal)
                }
            }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun getTimelineItemsForMonth(
        ownerId: String,
        noteId: String,
        dateInMonth: LocalDate,
    ): List<TimelineItem> {
        val remoteItems = remote.getTimelineItemsForMonth(ownerId, noteId, dateInMonth)
        val remoteStamps = remoteItems.filterIsInstance<StampItem>()
        local.replaceMonth(ownerId, noteId, dateInMonth, remoteStamps)
        return local.listByMonth(ownerId, noteId, dateInMonth)
    }

    override suspend fun getAllStampItems(ownerId: String, noteId: String): List<StampItem> {
        return remote.getAllStampItems(ownerId, noteId)
    }

    override suspend fun getStampItem(ownerId: String, noteId: String, timestamp: Long): StampItem? {
        val localItem = local.getByTimestamp(ownerId, noteId, timestamp)
        if (localItem != null) return localItem

        val remoteItem = remote.getStampItem(ownerId, noteId, timestamp)
        if (remoteItem != null) {
            local.upsert(ownerId, noteId, remoteItem)
        }
        return remoteItem
    }

    override suspend fun getStampNoteSuggestions(ownerId: String, noteId: String, type: StampType): List<String> {
        return remote.getStampNoteSuggestions(ownerId, noteId, type)
    }

    override suspend fun saveStamp(ownerId: String, noteId: String, stampType: StampType, note: String, timestamp: Long) {
        local.upsert(
            ownerId,
            noteId,
            StampItem(
                timestamp = timestamp,
                type = stampType,
                note = note,
            )
        )
        remote.saveStamp(ownerId, noteId, stampType, note, timestamp)
    }

    override suspend fun saveStamps(ownerId: String, noteId: String, stamps: List<StampItem>) {
        local.upsertAll(ownerId, noteId, stamps)
        remote.saveStamps(ownerId, noteId, stamps)
    }

    override suspend fun deleteTimelineItem(ownerId: String, noteId: String, item: TimelineItem) {
        if (item is StampItem) {
            local.deleteByTimestamp(ownerId, noteId, item.timestamp)
        }
        remote.deleteTimelineItem(ownerId, noteId, item)
    }
}
