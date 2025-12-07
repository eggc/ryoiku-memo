package net.eggc.ryoikumemo.data

import android.content.Context
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.google.firebase.Firebase
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

data class Note(val id: String, val name: String)

interface TimelineRepository {
    suspend fun getNotes(): List<Note>
    suspend fun createNote(name: String): Note
    suspend fun updateNote(note: Note)
    suspend fun deleteNote(noteId: String)

    suspend fun getTimelineItems(noteId: String): List<TimelineItem>
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

    override suspend fun getTimelineItems(noteId: String): List<TimelineItem> {
        val diaryItems = timelineCollection(noteId).whereEqualTo("itemType", "diary").get().await().map {
            DiaryItem(
                timestamp = it.getLong("timestamp")!!,
                text = it.getString("text")!!,
                date = it.getString("date")!!
            )
        }
        val stampItems = timelineCollection(noteId).whereEqualTo("itemType", "stamp").get().await().map {
            StampItem(
                timestamp = it.getLong("timestamp")!!,
                type = StampType.valueOf(it.getString("type")!!),
                note = it.getString("note")!!
            )
        }
        return (diaryItems + stampItems).sortedByDescending { it.timestamp }
    }

    override suspend fun saveDiary(noteId: String, date: String, text: String) {
        val diaryTimestamp = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd")).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
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

    override suspend fun getTimelineItems(noteId: String): List<TimelineItem> {
        val diaries = diaryPrefs(noteId).all.mapNotNull { (key, value) ->
            try {
                val diaryTimestamp = LocalDate.parse(key, dateParser).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
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
