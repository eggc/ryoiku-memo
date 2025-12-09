package net.eggc.ryoikumemo.data

import android.content.Context

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_LAST_SELECTED_NOTE_ID = "last_selected_note_id"
        private const val KEY_HIDDEN_STAMP_TYPES = "hidden_stamp_types"
    }

    fun saveLastSelectedNoteId(noteId: String) {
        prefs.edit().putString(KEY_LAST_SELECTED_NOTE_ID, noteId).apply()
    }

    fun getLastSelectedNoteId(): String? {
        return prefs.getString(KEY_LAST_SELECTED_NOTE_ID, null)
    }

    fun saveHiddenStampTypes(stampTypes: Set<String>) {
        prefs.edit().putStringSet(KEY_HIDDEN_STAMP_TYPES, stampTypes).apply()
    }

    fun getHiddenStampTypes(): Set<String> {
        return prefs.getStringSet(KEY_HIDDEN_STAMP_TYPES, emptySet()) ?: emptySet()
    }
}
