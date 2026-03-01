package net.eggc.ryoikumemo.data

import kotlinx.coroutines.flow.Flow

data class Note(
    val id: String,
    val name: String,
    val sharedId: String? = null,
    val ownerId: String
)

data class SharedNoteInfo(val noteId: String, val ownerId: String, val noteName: String)

interface NoteRepository {
    fun getNotesFlow(): Flow<List<Note>>
    suspend fun getNotes(): List<Note>
    suspend fun createNote(name: String, sharedId: String? = null): Note
    suspend fun updateNote(note: Note)
    suspend fun deleteNote(noteId: String)

    suspend fun subscribeToSharedNote(sharedId: String)
    suspend fun unsubscribeFromSharedNote(sharedId: String)
    suspend fun getSubscribedNoteIds(): List<String>

    suspend fun getNoteBySharedId(sharedId: String): SharedNoteInfo?
}
