package net.eggc.ryoikumemo.data

import android.content.Context

class AppPreferences(context: Context) {

    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    fun saveHiddenStampTypes(hiddenStampTypes: Set<String>) {
        prefs.edit().putStringSet("hidden_stamp_types", hiddenStampTypes).apply()
    }

    fun getHiddenStampTypes(): Set<String> {
        return prefs.getStringSet("hidden_stamp_types", emptySet()) ?: emptySet()
    }

    fun saveLastSelectedNote(note: Note) {
        prefs.edit().apply {
            putString("last_note_id", note.id)
            putString("last_note_name", note.name)
            putString("last_note_owner_id", note.ownerId)
            putString("last_note_shared_id", note.sharedId)
            apply()
        }
    }

    fun getLastSelectedNote(): Note? {
        val id = prefs.getString("last_note_id", null) ?: return null
        val name = prefs.getString("last_note_name", "") ?: ""
        val ownerId = prefs.getString("last_note_owner_id", null)
        val sharedId = prefs.getString("last_note_shared_id", null)
        return Note(id, name, sharedId, ownerId)
    }

    fun clearLastSelectedNote() {
        prefs.edit().apply {
            remove("last_note_id")
            remove("last_note_name")
            remove("last_note_owner_id")
            remove("last_note_shared_id")
            apply()
        }
    }
}
