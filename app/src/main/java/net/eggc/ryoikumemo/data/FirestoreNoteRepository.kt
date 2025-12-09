package net.eggc.ryoikumemo.data

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
    private val db = Firebase.firestore
    private val userId = Firebase.auth.currentUser?.uid ?: "anonymous"
    private val userDocRef = db.collection("users").document(userId)
    private val notesCollection = userDocRef.collection("notes")
    private val sharedNotesCollection = db.collection("sharedNotes")

    override suspend fun getNotes(): List<Note> {
        val snapshot = notesCollection.get().await()
        return snapshot.documents.mapNotNull { doc ->
            doc.getString("name")?.let { name ->
                Note(
                    id = doc.id,
                    name = name,
                    sharedId = doc.getString("sharedId"),
                    ownerId = userId // Own notes belong to the current user
                )
            }
        }
    }

    override suspend fun createNote(name: String, sharedId: String?): Note {
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
        val noteRef = notesCollection.document(note.id)
        val oldNoteSnapshot = noteRef.get().await()
        val oldSharedId = oldNoteSnapshot.getString("sharedId")

        val batch = db.batch()

        // Handle sharedId changes
        if (note.sharedId != oldSharedId) {
            // Delete old shared note entry only if it actually exists
            if (oldSharedId != null) {
                val oldSharedNoteRef = sharedNotesCollection.document(oldSharedId)
                if (oldSharedNoteRef.get().await().exists()) {
                    batch.delete(oldSharedNoteRef)
                }
            }
        }

        // Create or Update new shared note entry
        if (note.sharedId != null) {
            val sharedNoteData = mapOf(
                "ownerId" to userId,
                "noteId" to note.id,
                "noteName" to note.name
            )
            batch.set(sharedNotesCollection.document(note.sharedId), sharedNoteData)
        }

        // Update the note document itself
        val noteData = mapOf("name" to note.name, "sharedId" to note.sharedId)
        batch.update(noteRef, noteData)

        batch.commit().await()
    }

    override suspend fun deleteNote(noteId: String) {
        val noteRef = notesCollection.document(noteId)
        val noteSnapshot = noteRef.get().await()
        val sharedId = noteSnapshot.getString("sharedId")

        val batch = db.batch()

        // Delete timeline items
        val timelineItems = timelineCollection(userId, noteId).get().await()
        for (document in timelineItems.documents) {
            batch.delete(document.reference)
        }

        // Delete shared note entry
        sharedId?.let { batch.delete(sharedNotesCollection.document(it)) }

        // Delete the note itself
        batch.delete(noteRef)

        batch.commit().await()
    }

    private fun timelineCollection(ownerId: String, noteId: String) =
        db.collection("users").document(ownerId).collection("notes").document(noteId).collection("timeline")

    override suspend fun getTimelineItemsForMonth(ownerId: String, noteId: String, sharedId: String?, dateInMonth: LocalDate): List<TimelineItem> {
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
                    note = doc.getString("note")!!
                )
                else -> null
            }
        }
    }

    override suspend fun getStampItem(noteId: String, timestamp: Long): StampItem? {
        // This needs to be updated to handle shared notes if editing is required.
        val doc = timelineCollection(userId, noteId).document(timestamp.toString()).get().await()
        if (!doc.exists()) return null
        return StampItem(
            timestamp = doc.getLong("timestamp")!!,
            type = StampType.valueOf(doc.getString("type")!!),
            note = doc.getString("note")!!
        )
    }

    override suspend fun getStampNoteSuggestions(noteId: String): List<String> {
        // This needs to be updated to handle shared notes if suggestions are required.
        return timelineCollection(userId, noteId)
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
        // This needs to be updated to handle shared notes if saving is required.
        val stampMap = hashMapOf(
            "itemType" to "stamp",
            "timestamp" to timestamp,
            "type" to stampType.name,
            "note" to note
        )
        timelineCollection(userId, noteId).document(timestamp.toString()).set(stampMap).await()
    }

    override suspend fun deleteTimelineItem(noteId: String, item: TimelineItem) {
        // This needs to be updated to handle shared notes if deleting is required.
        if (item is StampItem) {
            timelineCollection(userId, noteId).document(item.timestamp.toString()).delete().await()
        }
    }

    override suspend fun subscribeToSharedNote(sharedId: String) {
        val subscription = mapOf("subscribedNoteIds" to FieldValue.arrayUnion(sharedId))
        userDocRef.set(subscription, SetOptions.merge()).await()
    }

    override suspend fun unsubscribeFromSharedNote(sharedId: String) {
        val subscription = mapOf("subscribedNoteIds" to FieldValue.arrayRemove(sharedId))
        userDocRef.update(subscription).await()
    }

    override suspend fun getSubscribedNoteIds(): List<String> {
        val snapshot = userDocRef.get().await()
        return snapshot.get("subscribedNoteIds") as? List<String> ?: emptyList()
    }

    override suspend fun getNoteBySharedId(sharedId: String): SharedNoteInfo? {
        val doc = sharedNotesCollection.document(sharedId).get().await()
        if (!doc.exists()) return null

        val ownerId = doc.getString("ownerId") ?: return null
        val noteId = doc.getString("noteId") ?: return null
        val noteName = doc.getString("noteName") ?: return null

        return SharedNoteInfo(noteId, ownerId, noteName)
    }
}
