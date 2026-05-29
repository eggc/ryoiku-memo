package net.eggc.ryoikumemo.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.tasks.await
import net.eggc.ryoikumemo.BuildConfig
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.ConcurrentHashMap

class FirestoreTimelineRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) : TimelineRepository {
    private val tag = "FirestoreTimelineRepo"
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val monthFlowCache = ConcurrentHashMap<String, Flow<List<TimelineItem>>>()

    private fun timelineCollection(ownerId: String, noteId: String) =
        db.collection("users").document(ownerId).collection("notes").document(noteId).collection("timeline")

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
                    operatorName = doc.getString("operatorName")
                )
                else -> null
            }
        } ?: emptyList()
    }

    override fun getTimelineItemsForMonthFlow(ownerId: String, noteId: String, dateInMonth: LocalDate): Flow<List<TimelineItem>> {
        val normalizedMonth = dateInMonth.with(TemporalAdjusters.firstDayOfMonth())
        val flowKey = "$ownerId/$noteId/${normalizedMonth.year}-${normalizedMonth.monthValue}"

        monthFlowCache[flowKey]?.let { cached ->
            if (BuildConfig.DEBUG) {
                Log.d(tag, "timeline-month-flow:reuse key=$flowKey")
            }
            return cached
        }

        val shared = createTimelineItemsForMonthFlow(ownerId, noteId, normalizedMonth)
            .shareIn(
                scope = repositoryScope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
                replay = 1
            )

        val existing = monthFlowCache.putIfAbsent(flowKey, shared)
        if (BuildConfig.DEBUG) {
            Log.d(tag, "timeline-month-flow:create key=$flowKey")
        }
        return existing ?: shared
    }

    private fun createTimelineItemsForMonthFlow(ownerId: String, noteId: String, dateInMonth: LocalDate): Flow<List<TimelineItem>> = callbackFlow {
        val startOfMonth = dateInMonth.with(TemporalAdjusters.firstDayOfMonth())
        val endOfMonth = dateInMonth.with(TemporalAdjusters.lastDayOfMonth())

        val startTimestamp = startOfMonth.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endTimestamp = endOfMonth.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val query = timelineCollection(ownerId, noteId)
            .whereGreaterThanOrEqualTo("timestamp", startTimestamp)
            .whereLessThan("timestamp", endTimestamp)
            .orderBy("timestamp", Query.Direction.DESCENDING)

        val monthKey = "$ownerId/$noteId/${startOfMonth.year}-${startOfMonth.monthValue}"
        if (BuildConfig.DEBUG) {
            Log.d(tag, "timeline-month-listener:start key=$monthKey startTs=$startTimestamp endTs=$endTimestamp")
        }

        val subscription = query.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                Log.e(tag, "getTimelineItemsForMonthFlow error", error)
                trySend(emptyList())
                return@addSnapshotListener
            }

            if (BuildConfig.DEBUG && snapshot != null) {
                Log.d(
                    tag,
                    "timeline-month-listener:update key=$monthKey size=${snapshot.size()} fromCache=${snapshot.metadata.isFromCache} pendingWrites=${snapshot.metadata.hasPendingWrites()}"
                )
            }
            trySend(mapSnapshotToTimelineItems(snapshot))
        }
        awaitClose {
            if (BuildConfig.DEBUG) {
                Log.d(tag, "timeline-month-listener:stop key=$monthKey")
            }
            subscription.remove()
        }
    }

    override suspend fun getTimelineItemsForMonth(ownerId: String, noteId: String, dateInMonth: LocalDate): List<TimelineItem> {
        val startOfMonth = dateInMonth.with(TemporalAdjusters.firstDayOfMonth())
        val endOfMonth = dateInMonth.with(TemporalAdjusters.lastDayOfMonth())

        val startTimestamp = startOfMonth.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endTimestamp = endOfMonth.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val snapshot = timelineCollection(ownerId, noteId)
            .whereGreaterThanOrEqualTo("timestamp", startTimestamp)
            .whereLessThan("timestamp", endTimestamp)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .await()

        return mapSnapshotToTimelineItems(snapshot)
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
            operatorName = doc.getString("operatorName")
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
        val stampMap = hashMapOf(
            "itemType" to "stamp",
            "timestamp" to timestamp,
            "type" to stampType.name,
            "note" to note,
            "operatorName" to auth.currentUser?.displayName
        )
        timelineCollection(ownerId, noteId).document(timestamp.toString()).set(stampMap).await()
    }

    override suspend fun saveStamps(ownerId: String, noteId: String, stamps: List<StampItem>) {
        if (stamps.isEmpty()) return

        val timeline = timelineCollection(ownerId, noteId)
        val operatorName = auth.currentUser?.displayName

        stamps.chunked(500).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { stamp ->
                val stampMap = hashMapOf(
                    "itemType" to "stamp",
                    "timestamp" to stamp.timestamp,
                    "type" to stamp.type.name,
                    "note" to stamp.note,
                    "operatorName" to operatorName
                )
                batch.set(timeline.document(stamp.timestamp.toString()), stampMap)
            }
            batch.commit().await()
        }
    }

    override suspend fun deleteTimelineItem(ownerId: String, noteId: String, item: TimelineItem) {
        if (item is StampItem) {
            timelineCollection(ownerId, noteId).document(item.timestamp.toString()).delete().await()
        }
    }
}
