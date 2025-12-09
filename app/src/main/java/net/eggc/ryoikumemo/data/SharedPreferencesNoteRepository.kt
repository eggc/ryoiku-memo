package net.eggc.ryoikumemo.data

import android.content.Context
import java.time.LocalDate
import java.util.UUID

class SharedPreferencesNoteRepository(private val context: Context) : NoteRepository {

    private val notesPrefs = context.getSharedPreferences("notes_prefs", Context.MODE_PRIVATE)

    override suspend fun getNotes(): List<Note> {
        return notesPrefs.all.map { (id, name) -> Note(id, name as String, null, null) }
    }

    override suspend fun createNote(name: String, sharedId: String?): Note {
        val id = UUID.randomUUID().toString()
        notesPrefs.edit().putString(id, name).apply()
        return Note(id, name, sharedId, null)
    }

    override suspend fun updateNote(note: Note) {
        notesPrefs.edit().putString(note.id, note.name).apply()
    }

    override suspend fun deleteNote(noteId: String) {
        stampPrefs(noteId).edit().clear().apply()
        notesPrefs.edit().remove(noteId).apply()
    }

    private fun stampPrefs(noteId: String) = context.getSharedPreferences("stamp_prefs_$noteId", Context.MODE_PRIVATE)

    override suspend fun getTimelineItemsForMonth(
        ownerId: String,
        noteId: String,
        sharedId: String?,
        dateInMonth: LocalDate
    ): List<TimelineItem> {
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

    override suspend fun unsubscribeFromSharedNote(sharedId: String) {
        // Not implemented for local storage
    }

    override suspend fun getSubscribedNoteIds(): List<String> {
        // Not implemented for local storage
        return emptyList()
    }

    override suspend fun getNoteBySharedId(sharedId: String): SharedNoteInfo? {
        // Not implemented for local storage
        return null
    }
}
