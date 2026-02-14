package net.eggc.ryoikumemo.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
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
        stampPrefs(noteId).edit().clear().apply()
        notesPrefs.edit().remove(noteId).apply()
    }

    private fun stampPrefs(noteId: String) = context.getSharedPreferences("stamp_prefs_$noteId", Context.MODE_PRIVATE)

    override fun getTimelineItemsForMonthFlow(
        ownerId: String,
        noteId: String,
        dateInMonth: LocalDate
    ): Flow<List<TimelineItem>> = flow {
        emit(getTimelineItemsForMonth(ownerId, noteId, null, dateInMonth))
    }

    override suspend fun getTimelineItemsForMonth(
        ownerId: String,
        noteId: String,
        sharedId: String?,
        dateInMonth: LocalDate
    ): List<TimelineItem> {
        // SharedPreferences implementation doesn't support month-based filtering easily, returning all for simplicity
        val stamps = getAllStampItems(ownerId, noteId)
        return stamps.sortedByDescending { it.timestamp }
    }

    override suspend fun getAllStampItems(ownerId: String, noteId: String): List<StampItem> {
        return stampPrefs(noteId).all.mapNotNull { (key, value) ->
            try {
                val valueString = value as String
                val parts = valueString.split('|', limit = 2)
                val type = StampType.valueOf(parts[0])
                val note = if (parts.size > 1) parts[1] else ""
                StampItem(timestamp = key.toLong(), type = type, note = note, operatorName = null)
            } catch (e: Exception) {
                null
            }
        }.sortedByDescending { it.timestamp }
    }

    override suspend fun getStampItem(ownerId: String, noteId: String, timestamp: Long): StampItem? {
        val valueString = stampPrefs(noteId).getString(timestamp.toString(), null) ?: return null
        val parts = valueString.split('|', limit = 2)
        val type = StampType.valueOf(parts[0])
        val note = if (parts.size > 1) parts[1] else ""
        return StampItem(timestamp = timestamp, type = type, note = note, operatorName = null)
    }

    override suspend fun getStampNoteSuggestions(ownerId: String, noteId: String, type: StampType): List<String> {
        return stampPrefs(noteId).all.values
            .asSequence()
            .mapNotNull { it as? String }
            .map { it.split('|', limit = 2) }
            .filter { it.size == 2 && StampType.valueOf(it[0]) == type }
            .map { it[1] }
            .filter { it.isNotBlank() }
            .distinct()
            .take(10)
            .toList()
    }

    override suspend fun saveStamp(ownerId: String, noteId: String, stampType: StampType, note: String, timestamp: Long) {
        with(stampPrefs(noteId).edit()) {
            // operatorName is not stored in local storage
            putString(timestamp.toString(), "${stampType.name}|${note}")
            apply()
        }
    }

    override suspend fun saveStamps(ownerId: String, noteId: String, stamps: List<StampItem>) {
        val prefs = stampPrefs(noteId)
        with(prefs.edit()) {
            stamps.forEach { item ->
                putString(item.timestamp.toString(), "${item.type.name}|${item.note}")
            }
            apply()
        }
    }

    override suspend fun deleteTimelineItem(ownerId: String, noteId: String, item: TimelineItem) {
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

    // --- Task management (Simplified local implementation) ---

    private fun taskPrefs(noteId: String) = context.getSharedPreferences("task_prefs_$noteId", Context.MODE_PRIVATE)

    override fun getTasksFlow(ownerId: String, noteId: String): Flow<List<Task>> = flow {
        val prefs = taskPrefs(noteId)
        val tasks = prefs.all.mapNotNull { (id, value) ->
            try {
                val parts = (value as String).split("|")
                Task(
                    id = id,
                    name = parts[0],
                    isCompleted = parts[1].toBoolean(),
                    timestamp = parts[2].toLong()
                )
            } catch (e: Exception) { null }
        }.sortedByDescending { it.timestamp }
        emit(tasks)
    }

    override suspend fun createTask(ownerId: String, noteId: String, name: String): Task {
        val id = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val task = Task(id, name, false, timestamp)
        taskPrefs(noteId).edit().putString(id, "$name|false|$timestamp").apply()
        return task
    }

    override suspend fun updateTaskProgress(ownerId: String, noteId: String, taskId: String, isCompleted: Boolean) {
        val prefs = taskPrefs(noteId)
        val value = prefs.getString(taskId, null) ?: return
        val parts = value.split("|").toMutableList()
        parts[1] = isCompleted.toString()
        prefs.edit().putString(taskId, parts.joinToString("|")).apply()
    }

    override suspend fun deleteTask(ownerId: String, noteId: String, taskId: String) {
        taskPrefs(noteId).edit().remove(taskId).apply()
    }
}
