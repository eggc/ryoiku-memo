package net.eggc.ryoikumemo.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID

private const val LOCAL_USER_ID = "local_user"

class SharedPreferencesNoteRepository(private val context: Context) : NoteRepository {

    private val notesPrefs = context.getSharedPreferences("notes_prefs", Context.MODE_PRIVATE)

    override fun getNotesFlow(): Flow<List<Note>> = flow {
        emit(getNotes())
    }

    override suspend fun getNotes(): List<Note> {
        return notesPrefs.all.map { (id, name) -> Note(id, name as String, null, LOCAL_USER_ID) }
    }

    override suspend fun createNote(name: String, sharedId: String?): Note {
        val id = UUID.randomUUID().toString()
        notesPrefs.edit().putString(id, name).apply()
        return Note(id, name, sharedId, LOCAL_USER_ID)
    }

    override suspend fun updateNote(note: Note) {
        notesPrefs.edit().putString(note.id, note.name).apply()
    }

    override suspend fun deleteNote(noteId: String) {
        notesPrefs.edit().remove(noteId).apply()
    }

    override suspend fun subscribeToSharedNote(sharedId: String) {}

    override suspend fun unsubscribeFromSharedNote(sharedId: String) {}

    override suspend fun getSubscribedNoteIds(): List<String> = emptyList()

    override suspend fun getNoteBySharedId(sharedId: String): SharedNoteInfo? = null
}
