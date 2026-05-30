package net.eggc.ryoikumemo.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await
import net.eggc.ryoikumemo.BuildConfig
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

class FirestoreTimelineRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : TimelineRepository {
    private val tag = "FirestoreTimelineRepo"

    private fun logOneLine(message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, message)
    }

    private fun timelineCollection(ownerId: String, noteId: String) =
        db.collection("users").document(ownerId).collection("notes").document(noteId).collection("timeline")

    private fun timelineMetaCollection(ownerId: String, noteId: String) =
        db.collection("users").document(ownerId).collection("notes").document(noteId).collection("timeline_meta")

    private fun mapSnapshotToTimelineItems(snapshot: QuerySnapshot?): List<TimelineItem> {
        return snapshot?.documents?.mapNotNull { doc ->
            when (doc.getString("itemType")) {
                "stamp" -> StampItem(
                    timestamp = doc.getLong("timestamp") ?: 0L,
                    type = try {
                        StampType.valueOf(doc.getString("type") ?: "")
                    } catch (e: Exception) {
                        StampType.MEMO
                    },
                    note = doc.getString("note") ?: "",
                    operatorName = doc.getString("operatorName"),
                    remoteUpdatedAt = doc.getTimestamp("updatedAt")?.toDate()?.time,
                )
                else -> null
            }
        } ?: emptyList()
    }

    private fun queryForMonth(ownerId: String, noteId: String, dateInMonth: LocalDate): Query {
        val (startTimestamp, endTimestamp) = monthRange(dateInMonth)

        return timelineCollection(ownerId, noteId)
            .whereGreaterThanOrEqualTo("timestamp", startTimestamp)
            .whereLessThan("timestamp", endTimestamp)
            .orderBy("timestamp", Query.Direction.DESCENDING)
    }

    private fun monthRange(dateInMonth: LocalDate): Pair<Long, Long> {
        val startOfMonth = dateInMonth.with(TemporalAdjusters.firstDayOfMonth())
        val endOfMonth = dateInMonth.with(TemporalAdjusters.lastDayOfMonth())
        val startTimestamp = startOfMonth.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endTimestamp = endOfMonth.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return startTimestamp to endTimestamp
    }

    private fun monthId(dateInMonth: LocalDate): String {
        val normalizedMonth = dateInMonth.with(TemporalAdjusters.firstDayOfMonth())
        return "%04d-%02d".format(normalizedMonth.year, normalizedMonth.monthValue)
    }

    private fun monthId(timestamp: Long): String {
        val date = java.time.Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        return monthId(date)
    }

    private suspend fun touchMonthMeta(ownerId: String, noteId: String, monthId: String) {
        timelineMetaCollection(ownerId, noteId)
            .document(monthId)
            .set(
                mapOf(
                    "monthId" to monthId,
                    "lastChangedAt" to FieldValue.serverTimestamp(),
                    "revision" to FieldValue.increment(1L),
                ),
                SetOptions.merge(),
            )
            .await()
    }

    private fun monthKey(ownerId: String, noteId: String, dateInMonth: LocalDate): String {
        val normalizedMonth = dateInMonth.with(TemporalAdjusters.firstDayOfMonth())
        return "$ownerId/$noteId/${normalizedMonth.year}-${normalizedMonth.monthValue}"
    }

    override fun getTimelineItemsForMonthFlow(ownerId: String, noteId: String, dateInMonth: LocalDate): Flow<List<TimelineItem>> {
        val key = monthKey(ownerId, noteId, dateInMonth)
        val query = queryForMonth(ownerId, noteId, dateInMonth)

        return flow {
            val cacheStartedAt = System.currentTimeMillis()
            val cacheItems = try {
                mapSnapshotToTimelineItems(query.get(Source.CACHE).await())
            } catch (e: Exception) {
                Log.w(tag, "cache fetch failed: $key", e)
                emptyList()
            }
            val cacheElapsedMs = System.currentTimeMillis() - cacheStartedAt
            logOneLine("TL resource=$key action=read source=cache size=${cacheItems.size} ms=$cacheElapsedMs")
            emit(cacheItems)

            val serverStartedAt = System.currentTimeMillis()
            try {
                val serverItems = mapSnapshotToTimelineItems(query.get(Source.SERVER).await())
                val serverElapsedMs = System.currentTimeMillis() - serverStartedAt
                logOneLine("TL resource=$key action=read source=server size=${serverItems.size} ms=$serverElapsedMs")
                if (serverItems != cacheItems) {
                    emit(serverItems)
                }
            } catch (e: Exception) {
                val serverElapsedMs = System.currentTimeMillis() - serverStartedAt
                logOneLine("TL resource=$key action=read source=server status=error ms=$serverElapsedMs")
            }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun getTimelineItemsForMonth(ownerId: String, noteId: String, dateInMonth: LocalDate): List<TimelineItem> {
        val key = monthKey(ownerId, noteId, dateInMonth)
        val startedAt = System.currentTimeMillis()
        val items = try {
            mapSnapshotToTimelineItems(queryForMonth(ownerId, noteId, dateInMonth).get(Source.SERVER).await())
        } catch (e: Exception) {
            mapSnapshotToTimelineItems(queryForMonth(ownerId, noteId, dateInMonth).get(Source.CACHE).await())
        }
        val elapsedMs = System.currentTimeMillis() - startedAt
        logOneLine("TL resource=$key action=refresh size=${items.size} ms=$elapsedMs")
        return items
    }

    override suspend fun getMonthMeta(ownerId: String, noteId: String, dateInMonth: LocalDate): TimelineMonthMeta? {
        return try {
            val doc = timelineMetaCollection(ownerId, noteId)
                .document(monthId(dateInMonth))
                .get(Source.SERVER)
                .await()
            if (!doc.exists()) return null

            TimelineMonthMeta(
                revision = doc.getLong("revision") ?: 0L,
                lastChangedAt = doc.getTimestamp("lastChangedAt")?.toDate()?.time ?: 0L,
            )
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun getAllStampItems(ownerId: String, noteId: String): List<StampItem> {
        val snapshot = timelineCollection(ownerId, noteId)
            .whereEqualTo("itemType", "stamp")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .get()
            .await()

        @Suppress("UNCHECKED_CAST")
        return mapSnapshotToTimelineItems(snapshot) as List<StampItem>
    }

    override suspend fun getStampItem(ownerId: String, noteId: String, timestamp: Long): StampItem? {
        val doc = timelineCollection(ownerId, noteId).document(timestamp.toString()).get().await()
        if (!doc.exists()) return null
        return StampItem(
            timestamp = doc.getLong("timestamp") ?: 0L,
            type = try {
                StampType.valueOf(doc.getString("type") ?: "")
            } catch (e: Exception) {
                StampType.MEMO
            },
            note = doc.getString("note") ?: "",
            operatorName = doc.getString("operatorName"),
            remoteUpdatedAt = doc.getTimestamp("updatedAt")?.toDate()?.time,
        )
    }

    override suspend fun getStampNoteSuggestions(ownerId: String, noteId: String, type: StampType): List<String> {
        return timelineCollection(ownerId, noteId)
            .whereEqualTo("itemType", "stamp")
            .whereEqualTo("type", type.name)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(100)
            .get()
            .await()
            .mapNotNull { it.getString("note") }
            .filter { it.isNotBlank() }
            .distinct()
            .take(10)
    }

    override suspend fun saveStamp(ownerId: String, noteId: String, stampType: StampType, note: String, timestamp: Long) {
        val startedAt = System.currentTimeMillis()
        val stampMap = hashMapOf(
            "itemType" to "stamp",
            "timestamp" to timestamp,
            "type" to stampType.name,
            "note" to note,
            "operatorName" to auth.currentUser?.displayName,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        timelineCollection(ownerId, noteId).document(timestamp.toString()).set(stampMap).await()
        touchMonthMeta(ownerId, noteId, monthId(timestamp))
        val elapsedMs = System.currentTimeMillis() - startedAt
        logOneLine("TL resource=$ownerId/$noteId/$timestamp action=create ms=$elapsedMs")
    }

    override suspend fun saveStamps(ownerId: String, noteId: String, stamps: List<StampItem>) {
        if (stamps.isEmpty()) return

        val timeline = timelineCollection(ownerId, noteId)
        val operatorName = auth.currentUser?.displayName
        val touchedMonths = mutableSetOf<String>()

        stamps.chunked(500).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { stamp ->
                touchedMonths += monthId(stamp.timestamp)
                val stampMap = hashMapOf(
                    "itemType" to "stamp",
                    "timestamp" to stamp.timestamp,
                    "type" to stamp.type.name,
                    "note" to stamp.note,
                    "operatorName" to operatorName,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.set(timeline.document(stamp.timestamp.toString()), stampMap)
            }
            batch.commit().await()
        }

        touchedMonths.forEach { month ->
            touchMonthMeta(ownerId, noteId, month)
        }
    }

    override suspend fun deleteTimelineItem(ownerId: String, noteId: String, item: TimelineItem) {
        if (item is StampItem) {
            val startedAt = System.currentTimeMillis()
            timelineCollection(ownerId, noteId).document(item.timestamp.toString()).delete().await()
            touchMonthMeta(ownerId, noteId, monthId(item.timestamp))
            val elapsedMs = System.currentTimeMillis() - startedAt
            logOneLine("TL resource=$ownerId/$noteId/${item.timestamp} action=delete ms=$elapsedMs")
        }
    }
}
