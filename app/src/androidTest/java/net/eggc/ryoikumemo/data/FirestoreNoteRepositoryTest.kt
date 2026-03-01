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
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class FirestoreNoteRepositoryTest {

    @get:Rule
    val emulatorRule = FirestoreEmulatorRule()

    private lateinit var repository: FirestoreNoteRepository

    @Before
    fun setUp() {
        repository = FirestoreNoteRepository(emulatorRule.db, emulatorRule.auth)
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
        repository.saveStamp(ownerId, note.id, StampType.MEMO, "Memo", System.currentTimeMillis())

        repository.deleteNote(note.id)

        val notes = repository.getNotes()
        assertTrue(notes.isEmpty())

        val items = repository.getAllStampItems(ownerId, note.id)
        assertTrue("Timeline should be cleared", items.isEmpty())
    }

    // --- Timeline / Stamp Operations ---

    @Test
    fun saveStamp_and_getStampItem() = runBlocking {
        val note = repository.createNote("Note")
        val ownerId = emulatorRule.auth.currentUser!!.uid
        val ts = 123456789L

        repository.saveStamp(ownerId, note.id, StampType.SLEEP, "Slept", ts)

        val item = repository.getStampItem(ownerId, note.id, ts)
        assertNotNull(item)
        assertEquals(StampType.SLEEP, item?.type)
        assertEquals("Slept", item?.note)
    }

    @Test
    fun getTimelineItemsForMonth_filtersByDate() = runBlocking {
        val note = repository.createNote("Month Test")
        val ownerId = emulatorRule.auth.currentUser!!.uid

        val thisMonth = LocalDate.of(2023, 10, 15)
        val tsThisMonth = 1697328000000L // 2023-10-15
        val tsNextMonth = 1698796800000L // 2023-11-01

        repository.saveStamp(ownerId, note.id, StampType.MEMO, "Oct", tsThisMonth)
        repository.saveStamp(ownerId, note.id, StampType.MEMO, "Nov", tsNextMonth)

        val items = repository.getTimelineItemsForMonth(ownerId, note.id, null, thisMonth)
        assertEquals(1, items.size)
        assertEquals("Oct", (items[0] as StampItem).note)
    }

    @Test
    fun getTimelineItemsForMonthFlow_emitsItems() = runBlocking {
        val note = repository.createNote("Flow Test")
        val ownerId = emulatorRule.auth.currentUser!!.uid
        val now = LocalDate.now()
        val ts = System.currentTimeMillis()

        repository.saveStamp(ownerId, note.id, StampType.MEMO, "FlowItem", ts)

        val items = repository.getTimelineItemsForMonthFlow(ownerId, note.id, now).first()
        assertEquals(1, items.size)
        assertEquals("FlowItem", (items[0] as StampItem).note)
    }

    @Test
    fun getAllStampItems_returnsAllStampsSorted() = runBlocking {
        val note = repository.createNote("Sort Test")
        val ownerId = emulatorRule.auth.currentUser!!.uid

        repository.saveStamp(ownerId, note.id, StampType.MEMO, "Second", 2000L)
        repository.saveStamp(ownerId, note.id, StampType.MEMO, "First", 1000L)

        val items = repository.getAllStampItems(ownerId, note.id)
        assertEquals(2, items.size)
        assertEquals("First", items[0].note)
        assertEquals("Second", items[1].note)
    }

    @Test
    fun saveStamps_bulkSavesData() = runBlocking {
        val note = repository.createNote("Bulk Test")
        val ownerId = emulatorRule.auth.currentUser!!.uid
        val stamps = listOf(
            StampItem(100L, StampType.FUN, "Fun 1"),
            StampItem(200L, StampType.FUN, "Fun 2")
        )

        repository.saveStamps(ownerId, note.id, stamps)

        val items = repository.getAllStampItems(ownerId, note.id)
        assertEquals(2, items.size)
    }

    @Test
    fun deleteTimelineItem_removesSpecificItem() = runBlocking {
        val note = repository.createNote("Delete Item Test")
        val ownerId = emulatorRule.auth.currentUser!!.uid
        val item = StampItem(1000L, StampType.MEMO, "To Delete")

        repository.saveStamp(ownerId, note.id, item.type, item.note, item.timestamp)
        repository.deleteTimelineItem(ownerId, note.id, item)

        assertNull(repository.getStampItem(ownerId, note.id, 1000L))
    }

    @Test
    fun getStampNoteSuggestions_returnsUniqueRecentNotes() = runBlocking {
        val note = repository.createNote("Suggest Test")
        val ownerId = emulatorRule.auth.currentUser!!.uid

        repository.saveStamp(ownerId, note.id, StampType.MEMO, "A", 1000L)
        repository.saveStamp(ownerId, note.id, StampType.MEMO, "B", 2000L)
        repository.saveStamp(ownerId, note.id, StampType.MEMO, "A", 3000L)

        val suggestions = repository.getStampNoteSuggestions(ownerId, note.id, StampType.MEMO)
        assertEquals(2, suggestions.size)
        assertEquals("A", suggestions[0]) // Most recent unique
        assertEquals("B", suggestions[1])
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
