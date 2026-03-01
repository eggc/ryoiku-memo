package net.eggc.ryoikumemo.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreNoteRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) : NoteRepository {
    private val tag = "FirestoreRepo"

    private val userId: String
        get() = auth.currentUser?.uid ?: "anonymous"

    private val userDocRef
        get() = db.collection("users").document(userId)

    private val notesCollection
        get() = userDocRef.collection("notes")

    private val sharedNotesCollection = db.collection("sharedNotes")

    private fun mapSnapshotToNotes(snapshot: QuerySnapshot?, currentUserId: String): List<Note> {
        return snapshot?.documents?.mapNotNull { doc ->
            doc.getString("name")?.let { name ->
                Note(
                    id = doc.id,
                    name = name,
                    sharedId = doc.getString("sharedId"),
                    ownerId = currentUserId
                )
            }
        } ?: emptyList()
    }

    override fun getNotesFlow(): Flow<List<Note>> = callbackFlow {
        val currentUserId = userId
        val subscription = notesCollection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(tag, "getNotesFlow error", error)
                trySend(emptyList())
                return@addSnapshotListener
            }
            trySend(mapSnapshotToNotes(snapshot, currentUserId))
        }
        awaitClose { subscription.remove() }
    }

    override suspend fun getNotes(): List<Note> {
        Log.d(tag, "API CALL: getNotes()")
        val snapshot = notesCollection.get().await()
        return mapSnapshotToNotes(snapshot, userId)
    }

    override suspend fun createNote(name: String, sharedId: String?): Note {
        Log.d(tag, "API CALL: createNote($name)")
        val currentUserId = userId
        val noteRef = notesCollection.document()
        val batch = db.batch()

        val noteData = mutableMapOf<String, Any>("name" to name)
        if (sharedId != null) {
            noteData["sharedId"] = sharedId
            val sharedNoteData = mapOf(
                "ownerId" to currentUserId,
                "noteId" to noteRef.id,
                "noteName" to name
            )
            batch.set(sharedNotesCollection.document(sharedId), sharedNoteData)
        }

        batch.set(noteRef, noteData)
        batch.commit().await()

        return Note(id = noteRef.id, name = name, sharedId = sharedId, ownerId = currentUserId)
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

        // タイムラインの削除
        val timelineItems = db.collection("users").document(userId).collection("notes").document(noteId).collection("timeline").get().await()
        for (document in timelineItems.documents) {
            batch.delete(document.reference)
        }

        sharedId?.let { batch.delete(sharedNotesCollection.document(it)) }
        batch.delete(noteRef)

        batch.commit().await()
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
