package net.eggc.ryoikumemo.data

import android.util.Log
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

class FirestoreNoteRepository : NoteRepository {
    private val tag = "FirestoreRepo"
    private val db = Firebase.firestore
    private val userId = Firebase.auth.currentUser?.uid ?: "anonymous"
    private val userDocRef = db.collection("users").document(userId)
    private val notesCollection = userDocRef.collection("notes")
    private val sharedNotesCollection = db.collection("sharedNotes")

    override suspend fun getNotes(): List<Note> {
        Log.d(tag, "API CALL: getNotes()")
        val snapshot = notesCollection.get().await()
        return snapshot.documents.mapNotNull { doc ->
            doc.getString("name")?.let { name ->
                Note(
                    id = doc.id,
                    name = name,
                    sharedId = doc.getString("sharedId"),
                    ownerId = userId
                )
            }
        }
    }

    override suspend fun createNote(name: String, sharedId: String?): Note {
        Log.d(tag, "API CALL: createNote($name)")
        val noteRef = notesCollection.document()
        val batch = db.batch()

        val noteData = mutableMapOf<String, Any>("name" to name)
        if (sharedId != null) {
            noteData["sharedId"] = sharedId
            val sharedNoteData = mapOf(
                "ownerId" to userId,
                "noteId" to noteRef.id,
                "noteName" to name
            )
            batch.set(sharedNotesCollection.document(sharedId), sharedNoteData)
        }

        batch.set(noteRef, noteData)
        batch.commit().await()

        return Note(id = noteRef.id, name = name, sharedId = sharedId, ownerId = userId)
    }

    override suspend fun updateNote(note: Note) {
        Log.d(tag, "API CALL: updateNote(${note.id})")
        val noteRef = notesCollection.document(note.id)
        val oldNoteSnapshot = noteRef.get().await()
        val oldSharedId = oldNoteSnapshot.getString("sharedId")

        val batch = db.batch()

        if (note.sharedId != oldSharedId) {
            if (oldSharedId != null) {
                val oldSharedNoteRef = sharedNotesCollection.document(oldSharedId)
                if (oldSharedNoteRef.get().await().exists()) {
                    batch.delete(oldSharedNoteRef)
                }
            }
        }

        if (note.sharedId != null) {
            val sharedNoteData = mapOf(
                "ownerId" to userId,
                "noteId" to note.id,
                "noteName" to note.name
            )
            batch.set(sharedNotesCollection.document(note.sharedId), sharedNoteData)
        }

        val noteData = mapOf("name" to note.name, "sharedId" to note.sharedId)
        batch.update(noteRef, noteData)

        batch.commit().await()
    }

    override suspend fun deleteNote(noteId: String) {
        Log.d(tag, "API CALL: deleteNote($noteId)")
        val noteRef = notesCollection.document(noteId)
        val noteSnapshot = noteRef.get().await()
        val sharedId = noteSnapshot.getString("sharedId")

        val batch = db.batch()

        val timelineItems = timelineCollection(userId, noteId).get().await()
        for (document in timelineItems.documents) {
            batch.delete(document.reference)
        }

        sharedId?.let { batch.delete(sharedNotesCollection.document(it)) }
        batch.delete(noteRef)

        batch.commit().await()
    }

    private fun timelineCollection(ownerId: String, noteId: String) =
        db.collection("users").document(ownerId).collection("notes").document(noteId).collection("timeline")

    override suspend fun getTimelineItemsForMonth(ownerId: String, noteId: String, sharedId: String?, dateInMonth: LocalDate): List<TimelineItem> {
        Log.d(tag, "API CALL: getTimelineItemsForMonth(noteId: $noteId, month: ${dateInMonth.month})")
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

        return snapshot.documents.mapNotNull { doc ->
            when (doc.getString("itemType")) {
                "stamp" -> StampItem(
                    timestamp = doc.getLong("timestamp")!!,
                    type = StampType.valueOf(doc.getString("type")!!),
                    note = doc.getString("note")!!,
                    operatorName = doc.getString("operatorName")
                )
                else -> null
            }
        }
    }

    override suspend fun getAllStampItems(ownerId: String, noteId: String): List<StampItem> {
        Log.d(tag, "API CALL: getAllStampItems(noteId: $noteId)")
        val snapshot = timelineCollection(ownerId, noteId)
            .whereEqualTo("itemType", "stamp")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            StampItem(
                timestamp = doc.getLong("timestamp")!!,
                type = StampType.valueOf(doc.getString("type")!!),
                note = doc.getString("note")!!,
                operatorName = doc.getString("operatorName")
            )
        }
    }

    override suspend fun getStampItem(ownerId: String, noteId: String, timestamp: Long): StampItem? {
        Log.d(tag, "API CALL: getStampItem(timestamp: $timestamp)")
        val doc = timelineCollection(ownerId, noteId).document(timestamp.toString()).get().await()
        if (!doc.exists()) return null
        return StampItem(
            timestamp = doc.getLong("timestamp")!!,
            type = StampType.valueOf(doc.getString("type")!!),
            note = doc.getString("note")!!,
            operatorName = doc.getString("operatorName")
        )
    }

    override suspend fun getStampNoteSuggestions(ownerId: String, noteId: String, type: StampType): List<String> {
        Log.d(tag, "API CALL: getStampNoteSuggestions(type: ${type.name})")
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
        Log.d(tag, "API CALL: saveStamp(type: ${stampType.name})")
        val stampMap = hashMapOf(
            "itemType" to "stamp",
            "timestamp" to timestamp,
            "type" to stampType.name,
            "note" to note,
            "operatorName" to Firebase.auth.currentUser?.displayName
        )
        timelineCollection(ownerId, noteId).document(timestamp.toString()).set(stampMap).await()
    }

    override suspend fun saveStamps(ownerId: String, noteId: String, stamps: List<StampItem>) {
        Log.d(tag, "API CALL: saveStamps(count: ${stamps.size})")
        if (stamps.isEmpty()) return
        
        val timeline = timelineCollection(ownerId, noteId)
        val operatorName = Firebase.auth.currentUser?.displayName
        
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
        Log.d(tag, "API CALL: deleteTimelineItem()")
        if (item is StampItem) {
            timelineCollection(ownerId, noteId).document(item.timestamp.toString()).delete().await()
        }
    }

    override suspend fun subscribeToSharedNote(sharedId: String) {
        Log.d(tag, "API CALL: subscribeToSharedNote($sharedId)")
        val subscription = mapOf("subscribedNoteIds" to FieldValue.arrayUnion(sharedId))
        userDocRef.set(subscription, SetOptions.merge()).await()
    }

    override suspend fun unsubscribeFromSharedNote(sharedId: String) {
        Log.d(tag, "API CALL: unsubscribeFromSharedNote($sharedId)")
        val subscription = mapOf("subscribedNoteIds" to FieldValue.arrayRemove(sharedId))
        userDocRef.update(subscription).await()
    }

    override suspend fun getSubscribedNoteIds(): List<String> {
        Log.d(tag, "API CALL: getSubscribedNoteIds()")
        val snapshot = userDocRef.get().await()
        @Suppress("UNCHECKED_CAST")
        return snapshot.get("subscribedNoteIds") as? List<String> ?: emptyList()
    }

    override suspend fun getNoteBySharedId(sharedId: String): SharedNoteInfo? {
        Log.d(tag, "API CALL: getNoteBySharedId($sharedId)")
        val doc = sharedNotesCollection.document(sharedId).get().await()
        if (!doc.exists()) return null

        val ownerId = doc.getString("ownerId") ?: return null
        val noteId = doc.getString("noteId") ?: return null
        val noteName = doc.getString("noteName") ?: return null

        return SharedNoteInfo(noteId, ownerId, noteName)
    }
}
