package net.eggc.ryoikumemo.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.eggc.ryoikumemo.rule.FirestoreEmulatorRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirestoreNoteRepositoryTest {

    @get:Rule
    val emulatorRule = FirestoreEmulatorRule()

    private lateinit var repository: FirestoreNoteRepository
    private lateinit var timelineRepository: FirestoreTimelineRepository

    @Before
    fun setUp() {
        repository = FirestoreNoteRepository(emulatorRule.db, emulatorRule.auth)
        timelineRepository = FirestoreTimelineRepository(emulatorRule.db, emulatorRule.auth)
    }

    // --- Note Operations ---

    @Test
    fun getNotes_returnsEmptyList_whenNoNotesExist() = runBlocking {
        val notes = repository.getNotes()
        assertTrue("Initially notes should be empty", notes.isEmpty())
    }

    @Test
    fun createNote_and_getNotes() = runBlocking {
        val noteName = "Test Note"
        val createdNote = repository.createNote(noteName)

        assertEquals(noteName, createdNote.name)
        assertNotNull(createdNote.id)

        val notes = repository.getNotes()
        assertEquals(1, notes.size)
        assertEquals(noteName, notes[0].name)
        assertEquals(createdNote.id, notes[0].id)
    }

    @Test
    fun getNotesFlow_emitsCurrentNotes() = runBlocking {
        repository.createNote("Note 1")
        val notes = repository.getNotesFlow().first()
        assertEquals(1, notes.size)
        assertEquals("Note 1", notes[0].name)
    }

    @Test
    fun updateNote_updatesNameAndSharedId() = runBlocking {
        val note = repository.createNote("Old Name")
        val updatedNote = note.copy(name = "New Name", sharedId = "shared-123")

        repository.updateNote(updatedNote)

        val notes = repository.getNotes()
        assertEquals(1, notes.size)
        assertEquals("New Name", notes[0].name)
        assertEquals("shared-123", notes[0].sharedId)

        // Verify shared note info is also created
        val sharedInfo = repository.getNoteBySharedId("shared-123")
        assertNotNull(sharedInfo)
        assertEquals(note.id, sharedInfo?.noteId)
        assertEquals("New Name", sharedInfo?.noteName)
    }

    @Test
    fun deleteNote_removesNoteAndTimeline() = runBlocking {
        val note = repository.createNote("To be deleted")
        val ownerId = emulatorRule.auth.currentUser!!.uid
        // NoteRepository から分離されたため、TimelineRepository を使用してスタンプを保存
        timelineRepository.saveStamp(ownerId, note.id, StampType.MEMO, "Memo", System.currentTimeMillis())

        repository.deleteNote(note.id)

        val notes = repository.getNotes()
        assertTrue(notes.isEmpty())

        // 削除後にタイムラインが空になっていることを TimelineRepository で確認
        val items = timelineRepository.getAllStampItems(ownerId, note.id)
        assertTrue("Timeline should be cleared", items.isEmpty())
    }

    // --- Subscription Operations ---

    @Test
    fun subscribeAndUnsubscribe_worksCorrectly() = runBlocking {
        val sharedId = "shared-xyz"

        repository.subscribeToSharedNote(sharedId)
        assertTrue(repository.getSubscribedNoteIds().contains(sharedId))

        repository.unsubscribeFromSharedNote(sharedId)
        assertTrue(!repository.getSubscribedNoteIds().contains(sharedId))
    }

    @Test
    fun getNoteBySharedId_returnsNullIfNotFound() = runBlocking {
        val info = repository.getNoteBySharedId("non-existent")
        assertNull(info)
    }
}
