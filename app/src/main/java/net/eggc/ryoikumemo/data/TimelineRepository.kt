package net.eggc.ryoikumemo.data

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.UUID

data class Note(val id: String, val name: String)

interface TimelineRepository {
    suspend fun getNotes(): List<Note>
    suspend fun createNote(name: String): Note
    suspend fun updateNote(note: Note)
    suspend fun deleteNote(noteId: String)

    suspend fun getTimelineItemsForMonth(noteId: String, dateInMonth: LocalDate): List<TimelineItem>
    suspend fun getDiaryItem(noteId: String, date: String): DiaryItem?
    suspend fun getStampItem(noteId: String, timestamp: Long): StampItem?
    suspend fun getStampNoteSuggestions(noteId: String): List<String>

    suspend fun saveDiary(noteId: String, date: String, text: String)
    suspend fun saveStamp(noteId: String, stampType: StampType, note: String, timestamp: Long = System.currentTimeMillis())
    suspend fun deleteTimelineItem(noteId: String, item: TimelineItem)
}

class FirestoreTimelineRepository : TimelineRepository {
    private val db = Firebase.firestore
    private val userId = Firebase.auth.currentUser?.uid ?: "anonymous"
    private val notesCollection = db.collection("users").document(userId).collection("notes")

    override suspend fun getNotes(): List<Note> {
        val snapshot = notesCollection.get().await()
        return snapshot.documents.mapNotNull { doc ->
            doc.getString("name")?.let { name ->
                Note(id = doc.id, name = name)
            }
        }
    }

    override suspend fun createNote(name: String): Note {
        val noteData = hashMapOf("name" to name)
        val noteRef = notesCollection.add(noteData).await()
        return Note(id = noteRef.id, name = name)
    }

    override suspend fun updateNote(note: Note) {
        notesCollection.document(note.id).update("name", note.name).await()
    }

    override suspend fun deleteNote(noteId: String) {
        val timelineItems = timelineCollection(noteId).get().await()
        val batch = db.batch()
        for (document in timelineItems.documents) {
            batch.delete(document.reference)
        }
        batch.commit().await()
        notesCollection.document(noteId).delete().await()
    }

    private fun timelineCollection(noteId: String) =
        notesCollection.document(noteId).collection("timeline")

    override suspend fun getTimelineItemsForMonth(noteId: String, dateInMonth: LocalDate): List<TimelineItem> {
        val startOfMonth = dateInMonth.with(TemporalAdjusters.firstDayOfMonth())
        val endOfMonth = dateInMonth.with(TemporalAdjusters.lastDayOfMonth())

        val startTimestamp = startOfMonth.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endTimestamp = endOfMonth.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val snapshot = timelineCollection(noteId)
            .whereGreaterThanOrEqualTo("timestamp", startTimestamp)
            .whereLessThan("timestamp", endTimestamp)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            when (doc.getString("itemType")) {
                "diary" -> DiaryItem(
                    timestamp = doc.getLong("timestamp")!!,
                    text = doc.getString("text")!!,
                    date = doc.getString("date")!!
                )
                "stamp" -> StampItem(
                    timestamp = doc.getLong("timestamp")!!,
                    type = StampType.valueOf(doc.getString("type")!!),
                    note = doc.getString("note")!!
                )
                else -> null
            }
        }
    }

    override suspend fun getDiaryItem(noteId: String, date: String): DiaryItem? {
        val doc = timelineCollection(noteId).document(date).get().await()
        if (!doc.exists()) return null
        return DiaryItem(
            timestamp = doc.getLong("timestamp")!!,
            text = doc.getString("text")!!,
            date = doc.getString("date")!!
        )
    }

    override suspend fun getStampItem(noteId: String, timestamp: Long): StampItem? {
        val doc = timelineCollection(noteId).document(timestamp.toString()).get().await()
        if (!doc.exists()) return null
        return StampItem(
            timestamp = doc.getLong("timestamp")!!,
            type = StampType.valueOf(doc.getString("type")!!),
            note = doc.getString("note")!!
        )
    }

    override suspend fun getStampNoteSuggestions(noteId: String): List<String> {
        return timelineCollection(noteId)
            .whereEqualTo("itemType", "stamp")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(100) // Look at last 100 stamps for suggestions
            .get()
            .await()
            .mapNotNull { it.getString("note") }
            .filter { it.isNotBlank() }
            .distinct()
            .take(10)
    }


    override suspend fun saveDiary(noteId: String, date: String, text: String) {
        val diaryTimestamp = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val diaryMap = hashMapOf(
            "itemType" to "diary",
            "timestamp" to diaryTimestamp,
            "text" to text,
            "date" to date
        )
        timelineCollection(noteId).document(date).set(diaryMap).await()
    }

    override suspend fun saveStamp(noteId: String, stampType: StampType, note: String, timestamp: Long) {
        val stampMap = hashMapOf(
            "itemType" to "stamp",
            "timestamp" to timestamp,
            "type" to stampType.name,
            "note" to note
        )
        timelineCollection(noteId).document(timestamp.toString()).set(stampMap).await()
    }

    override suspend fun deleteTimelineItem(noteId: String, item: TimelineItem) {
        val docId = when (item) {
            is DiaryItem -> item.date
            is StampItem -> item.timestamp.toString()
        }
        timelineCollection(noteId).document(docId).delete().await()
    }
}

class SharedPreferencesTimelineRepository(private val context: Context) : TimelineRepository {

    private val notesPrefs = context.getSharedPreferences("notes_prefs", Context.MODE_PRIVATE)
    private val dateParser = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    override suspend fun getNotes(): List<Note> {
        return notesPrefs.all.map { (id, name) -> Note(id, name as String) }
    }

    override suspend fun createNote(name: String): Note {
        val id = UUID.randomUUID().toString()
        notesPrefs.edit().putString(id, name).apply()
        return Note(id, name)
    }

    override suspend fun updateNote(note: Note) {
        notesPrefs.edit().putString(note.id, note.name).apply()
    }

    override suspend fun deleteNote(noteId: String) {
        diaryPrefs(noteId).edit().clear().apply()
        stampPrefs(noteId).edit().clear().apply()
        notesPrefs.edit().remove(noteId).apply()
    }

    private fun diaryPrefs(noteId: String) = context.getSharedPreferences("diary_prefs_$noteId", Context.MODE_PRIVATE)
    private fun stampPrefs(noteId: String) = context.getSharedPreferences("stamp_prefs_$noteId", Context.MODE_PRIVATE)

    override suspend fun getTimelineItemsForMonth(noteId: String, dateInMonth: LocalDate): List<TimelineItem> {
        // SharedPreferences implementation doesn't support month-based filtering easily, returning all for simplicity
        val diaries = diaryPrefs(noteId).all.mapNotNull { (key, value) ->
            try {
                val diaryTimestamp =
                    LocalDate.parse(key, dateParser).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                DiaryItem(timestamp = diaryTimestamp, text = value as String, date = key)
            } catch (e: Exception) {
                null
            }
        }

        val stamps = stampPrefs(noteId).all.mapNotNull { (key, value) ->
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

    override suspend fun getDiaryItem(noteId: String, date: String): DiaryItem? {
        val text = diaryPrefs(noteId).getString(date, null) ?: return null
        val timestamp = LocalDate.parse(date, dateParser).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return DiaryItem(timestamp, text, date)
    }

    override suspend fun getStampItem(noteId: String, timestamp: Long): StampItem? {
        val valueString = stampPrefs(noteId).getString(timestamp.toString(), null) ?: return null
        val parts = valueString.split('|', limit = 2)
        val type = StampType.valueOf(parts[0])
        val note = if (parts.size > 1) parts[1] else ""
        return StampItem(timestamp = timestamp, type = type, note = note)
    }

    override suspend fun getStampNoteSuggestions(noteId: String): List<String> {
        return stampPrefs(noteId).all.values
            .mapNotNull { it as? String }
            .map { it.split('|', limit = 2).getOrElse(1) { "" } }
            .filter { it.isNotBlank() }
            .distinct()
            .take(10)
    }


    override suspend fun saveDiary(noteId: String, date: String, text: String) {
        with(diaryPrefs(noteId).edit()) {
            putString(date, text)
            apply()
        }
    }

    override suspend fun saveStamp(noteId: String, stampType: StampType, note: String, timestamp: Long) {
        with(stampPrefs(noteId).edit()) {
            putString(timestamp.toString(), "${stampType.name}|${note}")
            apply()
        }
    }

    override suspend fun deleteTimelineItem(noteId: String, item: TimelineItem) {
        val keyToDelete = when (item) {
            is DiaryItem -> item.date
            is StampItem -> item.timestamp.toString()
        }
        val prefs = when (item) {
            is DiaryItem -> diaryPrefs(noteId)
            is StampItem -> stampPrefs(noteId)
        }
        with(prefs.edit()) {
            remove(keyToDelete)
            apply()
        }
    }
}
