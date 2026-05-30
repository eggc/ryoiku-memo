package net.eggc.ryoikumemo.data.local

import net.eggc.ryoikumemo.data.StampItem
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

class TimelineLocalDataSource(
    private val stampDao: TimelineStampDao,
) {
    private fun monthRange(dateInMonth: LocalDate): Pair<Long, Long> {
        val startOfMonth = dateInMonth.with(TemporalAdjusters.firstDayOfMonth())
        val endOfMonth = dateInMonth.with(TemporalAdjusters.lastDayOfMonth())
        val startTimestamp = startOfMonth.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endTimestamp = endOfMonth.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return startTimestamp to endTimestamp
    }

    suspend fun listByMonth(ownerId: String, noteId: String, dateInMonth: LocalDate): List<StampItem> {
        val (startTimestamp, endTimestamp) = monthRange(dateInMonth)
        return stampDao.listByMonth(ownerId, noteId, startTimestamp, endTimestamp).map { it.toModel() }
    }

    suspend fun replaceMonth(ownerId: String, noteId: String, dateInMonth: LocalDate, items: List<StampItem>) {
        val (startTimestamp, endTimestamp) = monthRange(dateInMonth)
        stampDao.deleteByMonth(ownerId, noteId, startTimestamp, endTimestamp)
        if (items.isNotEmpty()) {
            val syncedAt = System.currentTimeMillis()
            stampDao.upsertAll(
                items.map {
                    it.toEntity(
                        ownerId = ownerId,
                        noteId = noteId,
                        localSyncedAt = syncedAt,
                        remoteUpdatedAt = it.remoteUpdatedAt,
                    )
                }
            )
        }
    }

    suspend fun getByTimestamp(ownerId: String, noteId: String, timestamp: Long): StampItem? {
        return stampDao.getByTimestamp(ownerId, noteId, timestamp)?.toModel()
    }

    suspend fun upsert(ownerId: String, noteId: String, item: StampItem) {
        stampDao.upsert(item.toEntity(ownerId, noteId, localSyncedAt = System.currentTimeMillis(), remoteUpdatedAt = item.remoteUpdatedAt))
    }

    suspend fun upsertAll(ownerId: String, noteId: String, items: List<StampItem>) {
        if (items.isEmpty()) return
        val syncedAt = System.currentTimeMillis()
        stampDao.upsertAll(items.map { it.toEntity(ownerId, noteId, localSyncedAt = syncedAt, remoteUpdatedAt = it.remoteUpdatedAt) })
    }

    suspend fun deleteByTimestamp(ownerId: String, noteId: String, timestamp: Long) {
        stampDao.deleteByTimestamp(ownerId, noteId, timestamp)
    }
}
