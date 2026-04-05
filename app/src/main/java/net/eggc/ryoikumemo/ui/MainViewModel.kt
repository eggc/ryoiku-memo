package net.eggc.ryoikumemo.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.eggc.ryoikumemo.data.AppPreferences
import net.eggc.ryoikumemo.data.FirestoreNoteRepository
import net.eggc.ryoikumemo.data.FirestoreTimelineRepository
import net.eggc.ryoikumemo.data.FirestoreTaskRepository
import net.eggc.ryoikumemo.data.Note
import net.eggc.ryoikumemo.data.NoteRepository
import net.eggc.ryoikumemo.data.TimelineRepository
import net.eggc.ryoikumemo.data.TaskRepository
import net.eggc.ryoikumemo.data.SharedPreferencesNoteRepository
import net.eggc.ryoikumemo.data.StampItem
import net.eggc.ryoikumemo.data.StampType
import java.time.LocalDate
import java.util.Stack

class MainViewModel(context: Context) : ViewModel() {
    private val appPreferences = AppPreferences(context)

    private val _currentUser = MutableStateFlow(Firebase.auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _noteRepository = MutableStateFlow<NoteRepository>(createNoteRepository(context, _currentUser.value))
    val noteRepository: StateFlow<NoteRepository> = _noteRepository.asStateFlow()

    private val _timelineRepository = MutableStateFlow<TimelineRepository>(createTimelineRepository(context, _currentUser.value))
    val timelineRepository: StateFlow<TimelineRepository> = _timelineRepository.asStateFlow()

    private val _taskRepository = MutableStateFlow<TaskRepository>(createTaskRepository(context, _currentUser.value))
    val taskRepository: StateFlow<TaskRepository> = _taskRepository.asStateFlow()

    private val _currentNote = MutableStateFlow<Note?>(null)
    val currentNote: StateFlow<Note?> = _currentNote.asStateFlow()

    private val _allNotes = MutableStateFlow<List<Note>>(emptyList())
    val allNotes: StateFlow<List<Note>> = _allNotes.asStateFlow()

    private val _selectedMonth = MutableStateFlow(LocalDate.now())
    val selectedMonth: StateFlow<LocalDate> = _selectedMonth.asStateFlow()

    private val _currentDestination = MutableStateFlow(AppDestinations.TIMELINE)
    val currentDestination: StateFlow<AppDestinations> = _currentDestination.asStateFlow()

    private val navigationHistory = Stack<AppDestinations>()

    private val _editingStamp = MutableStateFlow<StampItem?>(null)
    val editingStamp: StateFlow<StampItem?> = _editingStamp.asStateFlow()

    private val _isEditingExisting = MutableStateFlow(false)
    val isEditingExisting: StateFlow<Boolean> = _isEditingExisting.asStateFlow()

    init {
        // Auth 状態の監視
        Firebase.auth.addAuthStateListener { auth ->
            val user = auth.currentUser
            if (_currentUser.value?.uid != user?.uid) {
                _currentUser.value = user
                _noteRepository.value = createNoteRepository(context, user)
                _timelineRepository.value = createTimelineRepository(context, user)
                _taskRepository.value = createTaskRepository(context, user)
                refreshNotes()
            }
        }
        refreshNotes()
    }

    private fun createNoteRepository(context: Context, user: FirebaseUser?): NoteRepository {
        return if (user != null) {
            FirestoreNoteRepository(Firebase.firestore, Firebase.auth)
        } else {
            SharedPreferencesNoteRepository(context)
        }
    }

    private fun createTimelineRepository(context: Context, user: FirebaseUser?): TimelineRepository {
        return FirestoreTimelineRepository(Firebase.firestore, Firebase.auth)
    }

    private fun createTaskRepository(context: Context, user: FirebaseUser?): TaskRepository {
        return FirestoreTaskRepository(Firebase.firestore)
    }

    fun refreshNotes() {
        viewModelScope.launch {
            val repo = _noteRepository.value
            val user = _currentUser.value

            val ownNotes = repo.getNotes()
            _allNotes.value = ownNotes

            val subscribedIds = if (user != null) repo.getSubscribedNoteIds() else emptyList()
            val lastNote = appPreferences.getLastSelectedNote()

            val validatedNote = if (lastNote != null) {
                val isValid = if (lastNote.sharedId == null) {
                    ownNotes.any { it.id == lastNote.id }
                } else {
                    subscribedIds.contains(lastNote.sharedId)
                }
                if (isValid) lastNote else null
            } else null

            val finalNote = validatedNote ?: ownNotes.firstOrNull() ?: repo.createNote("ノート1")

            _currentNote.value = finalNote
            appPreferences.saveLastSelectedNote(finalNote)

            if (ownNotes.isEmpty() && validatedNote == null) {
                _allNotes.value = repo.getNotes()
            }
        }
    }

    fun selectNote(note: Note) {
        _currentNote.value = note
        appPreferences.saveLastSelectedNote(note)
        navigateTo(AppDestinations.TIMELINE)
    }

    fun updateCurrentNote(note: Note) {
        _currentNote.value = note
        appPreferences.saveLastSelectedNote(note)
    }

    fun setMonth(month: LocalDate) {
        _selectedMonth.value = month
    }

    fun navigateTo(destination: AppDestinations) {
        if (_currentDestination.value != destination) {
            navigationHistory.push(_currentDestination.value)
            _currentDestination.value = destination
        }
        if (destination != AppDestinations.EDIT_STAMP) {
            _editingStamp.value = null
        }
    }

    fun popBackStack(): Boolean {
        if (navigationHistory.isNotEmpty()) {
            _currentDestination.value = navigationHistory.pop()
            if (_currentDestination.value != AppDestinations.EDIT_STAMP) {
                _editingStamp.value = null
            }
            return true
        }
        return false
    }

    fun setEditingStamp(stamp: StampItem?) {
        _editingStamp.value = stamp
        _isEditingExisting.value = stamp != null
    }

    fun startAddingStamp(type: StampType) {
        _editingStamp.value = StampItem(System.currentTimeMillis(), type, "")
        _isEditingExisting.value = false
        navigateTo(AppDestinations.EDIT_STAMP)
    }

    fun setEditingStampById(stampId: Long) {
        val note = _currentNote.value ?: return
        val timelineRepo = _timelineRepository.value
        viewModelScope.launch {
            val item = timelineRepo.getStampItem(note.ownerId, note.id, stampId)
            _editingStamp.value = item
            _isEditingExisting.value = item != null
            if (item != null) {
                navigateTo(AppDestinations.EDIT_STAMP)
            }
        }
    }

    fun saveStamp(timestamp: Long, noteText: String) {
        val currentStamp = _editingStamp.value ?: return
        val note = _currentNote.value ?: return
        val timelineRepo = _timelineRepository.value

        viewModelScope.launch {
            if (_isEditingExisting.value) {
                timelineRepo.deleteTimelineItem(note.ownerId, note.id, currentStamp)
            }
            timelineRepo.saveStamp(note.ownerId, note.id, currentStamp.type, noteText, timestamp)
            _editingStamp.value = null
            _currentDestination.value = AppDestinations.TIMELINE
            navigationHistory.clear() // 保存後は履歴をクリアしてタイムラインを起点にする
        }
    }

    @Deprecated("Use saveStamp instead", ReplaceWith("saveStamp(timestamp, noteText)"))
    fun saveEditedStamp(timestamp: Long, noteText: String) {
        saveStamp(timestamp, noteText)
    }
}
