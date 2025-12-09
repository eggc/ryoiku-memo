package net.eggc.ryoikumemo.data

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.UUID

data class Note(val id: String, val name: String, val sharedId: String? = null)

interface TimelineRepository {
    suspend fun getNotes(): List<Note>
    suspend fun createNote(name: String, sharedId: String? = null): Note
    suspend fun updateNote(note: Note)
    suspend fun deleteNote(noteId: String)

    suspend fun getTimelineItemsForMonth(noteId: String, dateInMonth: LocalDate): List<TimelineItem>
    suspend fun getStampItem(noteId: String, timestamp: Long): StampItem?
    suspend fun getStampNoteSuggestions(noteId: String): List<String>

    suspend fun saveStamp(noteId: String, stampType: StampType, note: String, timestamp: Long = System.currentTimeMillis())
    suspend fun deleteTimelineItem(noteId: String, item: TimelineItem)

    suspend fun subscribeToSharedNote(sharedId: String)
    suspend fun getSubscribedNoteIds(): List<String>
}

class FirestoreTimelineRepository : TimelineRepository {
    private val db = Firebase.firestore
    private val userId = Firebase.auth.currentUser?.uid ?: "anonymous"
    private val userDocRef = db.collection("users").document(userId)
    private val notesCollection = userDocRef.collection("notes")

    override suspend fun getNotes(): List<Note> {
        val snapshot = notesCollection.get().await()
        return snapshot.documents.mapNotNull { doc ->
            doc.getString("name")?.let { name ->
                Note(
                    id = doc.id,
                    name = name,
                    sharedId = doc.getString("sharedId")
                )
            }
        }
    }

    override suspend fun createNote(name: String, sharedId: String?): Note {
        val noteData = mutableMapOf<String, Any>("name" to name)
        sharedId?.let { noteData["sharedId"] = it }
        val noteRef = notesCollection.add(noteData).await()
        return Note(id = noteRef.id, name = name, sharedId = sharedId)
    }

    override suspend fun updateNote(note: Note) {
        val noteData = mutableMapOf<String, Any?>("name" to note.name)
        if (note.sharedId != null) {
            noteData["sharedId"] = note.sharedId
        } else {
            noteData.keys.remove("sharedId")
        }
        notesCollection.document(note.id).update(noteData).await()
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
                "stamp" -> StampItem(
                    timestamp = doc.getLong("timestamp")!!,
                    type = StampType.valueOf(doc.getString("type")!!),
                    note = doc.getString("note")!!
                )
                else -> null
            }
        }
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
        if (item is StampItem) {
            timelineCollection(noteId).document(item.timestamp.toString()).delete().await()
        }
    }

    override suspend fun subscribeToSharedNote(sharedId: String) {
        val subscription = mapOf("subscribedNoteIds" to FieldValue.arrayUnion(sharedId))
        userDocRef.set(subscription, SetOptions.merge()).await()
    }

    override suspend fun getSubscribedNoteIds(): List<String> {
        val snapshot = userDocRef.get().await()
        return snapshot.get("subscribedNoteIds") as? List<String> ?: emptyList()
    }
}

class SharedPreferencesTimelineRepository(private val context: Context) : TimelineRepository {

    private val notesPrefs = context.getSharedPreferences("notes_prefs", Context.MODE_PRIVATE)

    override suspend fun getNotes(): List<Note> {
        return notesPrefs.all.map { (id, name) -> Note(id, name as String, null) }
    }

    override suspend fun createNote(name: String, sharedId: String?): Note {
        val id = UUID.randomUUID().toString()
        notesPrefs.edit().putString(id, name).apply()
        return Note(id, name, sharedId)
    }

    override suspend fun updateNote(note: Note) {
        notesPrefs.edit().putString(note.id, note.name).apply()
    }

    override suspend fun deleteNote(noteId: String) {
        stampPrefs(noteId).edit().clear().apply()
        notesPrefs.edit().remove(noteId).apply()
    }

    private fun stampPrefs(noteId: String) = context.getSharedPreferences("stamp_prefs_$noteId", Context.MODE_PRIVATE)

    override suspend fun getTimelineItemsForMonth(noteId: String, dateInMonth: LocalDate): List<TimelineItem> {
        // SharedPreferences implementation doesn't support month-based filtering easily, returning all for simplicity
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

        return stamps.sortedByDescending { it.timestamp }
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

    override suspend fun saveStamp(noteId: String, stampType: StampType, note: String, timestamp: Long) {
        with(stampPrefs(noteId).edit()) {
            putString(timestamp.toString(), "${stampType.name}|${note}")
            apply()
        }
    }

    override suspend fun deleteTimelineItem(noteId: String, item: TimelineItem) {
        if (item is StampItem) {
            with(stampPrefs(noteId).edit()) {
                remove(item.timestamp.toString())
                apply()
            }
        }
    }

    override suspend fun subscribeToSharedNote(sharedId: String) {
        // Not implemented for local storage
    }

    override suspend fun getSubscribedNoteIds(): List<String> {
        // Not implemented for local storage
        return emptyList()
    }
}
