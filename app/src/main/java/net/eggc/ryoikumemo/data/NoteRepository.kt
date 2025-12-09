package net.eggc.ryoikumemo.data

import java.time.LocalDate

data class Note(val id: String, val name: String, val sharedId: String? = null)

data class SharedNoteInfo(val noteId: String, val ownerId: String, val noteName: String)

interface NoteRepository {
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
    suspend fun unsubscribeFromSharedNote(sharedId: String)
    suspend fun getSubscribedNoteIds(): List<String>

    suspend fun getNoteBySharedId(sharedId: String): SharedNoteInfo?
}
