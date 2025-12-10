package net.eggc.ryoikumemo.data

import java.time.LocalDate

data class Note(
    val id: String,
    val name: String,
    val sharedId: String? = null,
    val ownerId: String
)

data class SharedNoteInfo(val noteId: String, val ownerId: String, val noteName: String)

interface NoteRepository {
    suspend fun getNotes(): List<Note>
    suspend fun createNote(name: String, sharedId: String? = null): Note
    suspend fun updateNote(note: Note)
    suspend fun deleteNote(noteId: String)

    suspend fun getTimelineItemsForMonth(ownerId: String, noteId: String, sharedId: String?, dateInMonth: LocalDate): List<TimelineItem>
    suspend fun getStampItem(ownerId: String, noteId: String, timestamp: Long): StampItem?
    suspend fun getStampNoteSuggestions(ownerId: String, noteId: String, type: StampType): List<String>

    suspend fun saveStamp(ownerId: String, noteId: String, stampType: StampType, note: String, timestamp: Long = System.currentTimeMillis())
    suspend fun deleteTimelineItem(ownerId: String, noteId: String, item: TimelineItem)

    suspend fun subscribeToSharedNote(sharedId: String)
    suspend fun unsubscribeFromSharedNote(sharedId: String)
    suspend fun getSubscribedNoteIds(): List<String>

    suspend fun getNoteBySharedId(sharedId: String): SharedNoteInfo?
}
