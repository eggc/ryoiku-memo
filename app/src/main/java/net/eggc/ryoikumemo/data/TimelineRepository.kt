package net.eggc.ryoikumemo.data

import android.content.Context
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.google.firebase.Firebase
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

interface TimelineRepository {
    suspend fun getTimelineItems(): List<TimelineItem>
    suspend fun saveDiary(date: String, text: String)
    suspend fun saveStamp(stampType: StampType, note: String, timestamp: Long = System.currentTimeMillis())
    suspend fun deleteTimelineItem(item: TimelineItem)
}

class FirestoreTimelineRepository : TimelineRepository {
    private val db = Firebase.firestore
    private val userId = Firebase.auth.currentUser?.uid ?: "anonymous"
    private val timelineCollection = db.collection("users").document(userId).collection("timeline")

    override suspend fun getTimelineItems(): List<TimelineItem> {
        val diaryItems = timelineCollection.whereEqualTo("itemType", "diary").get().await().map {
            DiaryItem(
                timestamp = it.getLong("timestamp")!!,
                text = it.getString("text")!!,
                date = it.getString("date")!!
            )
        }
        val stampItems = timelineCollection.whereEqualTo("itemType", "stamp").get().await().map {
            StampItem(
                timestamp = it.getLong("timestamp")!!,
                type = StampType.valueOf(it.getString("type")!!),
                note = it.getString("note")!!
            )
        }
        return (diaryItems + stampItems).sortedByDescending { it.timestamp }
    }

    override suspend fun saveDiary(date: String, text: String) {
        val diaryTimestamp = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd")).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val diaryMap = hashMapOf(
            "itemType" to "diary",
            "timestamp" to diaryTimestamp,
            "text" to text,
            "date" to date
        )
        timelineCollection.document(date).set(diaryMap).await()
    }

    override suspend fun saveStamp(stampType: StampType, note: String, timestamp: Long) {
        val stampMap = hashMapOf(
            "itemType" to "stamp",
            "timestamp" to timestamp,
            "type" to stampType.name,
            "note" to note
        )
        timelineCollection.document(timestamp.toString()).set(stampMap).await()
    }

    override suspend fun deleteTimelineItem(item: TimelineItem) {
        val docId = when (item) {
            is DiaryItem -> item.date
            is StampItem -> item.timestamp.toString()
        }
        timelineCollection.document(docId).delete().await()
    }
}

class SharedPreferencesTimelineRepository(private val context: Context) : TimelineRepository {

    private val diaryPrefs = context.getSharedPreferences("diary_prefs", Context.MODE_PRIVATE)
    private val stampPrefs = context.getSharedPreferences("stamp_prefs", Context.MODE_PRIVATE)
    private val dateParser = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    override suspend fun getTimelineItems(): List<TimelineItem> {
        val diaries = diaryPrefs.all.mapNotNull { (key, value) ->
            try {
                val diaryTimestamp = LocalDate.parse(key, dateParser).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                DiaryItem(timestamp = diaryTimestamp, text = value as String, date = key)
            } catch (e: Exception) {
                null
            }
        }

        val stamps = stampPrefs.all.mapNotNull { (key, value) ->
            try {
                val valueString = value as String
                val parts = valueString.split('|', limit = 2)
                val type = StampType.valueOf(parts[0])
                val note = if (parts.size > 1) parts[1] else ""
                StampItem(timestamp = key.toLong(), type = type, note = note)
            } catch (e: Exception) {
                null
            }
        }

        return (diaries + stamps).sortedByDescending { it.timestamp }
    }

    override suspend fun saveDiary(date: String, text: String) {
        with(diaryPrefs.edit()) {
            putString(date, text)
            apply()
        }
    }

    override suspend fun saveStamp(stampType: StampType, note: String, timestamp: Long) {
        with(stampPrefs.edit()) {
            putString(timestamp.toString(), "${stampType.name}|${note}")
            apply()
        }
    }

    override suspend fun deleteTimelineItem(item: TimelineItem) {
        val keyToDelete = when (item) {
            is DiaryItem -> item.date
            is StampItem -> item.timestamp.toString()
        }
        val prefs = when (item) {
            is DiaryItem -> diaryPrefs
            is StampItem -> stampPrefs
        }
        with(prefs.edit()) {
            remove(keyToDelete)
            apply()
        }
    }
}
