package net.eggc.ryoikumemo.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.eggc.ryoikumemo.rule.FirestoreEmulatorRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class FirestoreTimelineRepositoryTest {

    @get:Rule
    val emulatorRule = FirestoreEmulatorRule()

    private lateinit var timelineRepository: FirestoreTimelineRepository
    private lateinit var noteRepository: FirestoreNoteRepository

    @Before
    fun setUp() {
        timelineRepository = FirestoreTimelineRepository(emulatorRule.db, emulatorRule.auth)
        noteRepository = FirestoreNoteRepository(emulatorRule.db, emulatorRule.auth)
    }

    @Test
    fun saveStamp_and_getStampItem() = runBlocking {
        val note = noteRepository.createNote("Note")
        val ownerId = emulatorRule.auth.currentUser!!.uid
        val ts = 123456789L

        timelineRepository.saveStamp(ownerId, note.id, StampType.SLEEP, "Slept", ts)

        val item = timelineRepository.getStampItem(ownerId, note.id, ts)
        assertNotNull(item)
        assertEquals(StampType.SLEEP, item?.type)
        assertEquals("Slept", item?.note)
    }

    @Test
    fun getTimelineItemsForMonth_filtersByDate() = runBlocking {
        val note = noteRepository.createNote("Month Test")
        val ownerId = emulatorRule.auth.currentUser!!.uid

        val thisMonth = LocalDate.of(2023, 10, 15)
        val tsThisMonth = 1697328000000L // 2023-10-15
        val tsNextMonth = 1698796800000L // 2023-11-01

        timelineRepository.saveStamp(ownerId, note.id, StampType.MEMO, "Oct", tsThisMonth)
        timelineRepository.saveStamp(ownerId, note.id, StampType.MEMO, "Nov", tsNextMonth)

        val items = timelineRepository.getTimelineItemsForMonth(ownerId, note.id, thisMonth)
        assertEquals(1, items.size)
        assertEquals("Oct", (items[0] as StampItem).note)
    }

    @Test
    fun getTimelineItemsForMonthFlow_emitsItems() = runBlocking {
        val note = noteRepository.createNote("Flow Test")
        val ownerId = emulatorRule.auth.currentUser!!.uid
        val now = LocalDate.now()
        val ts = System.currentTimeMillis()

        timelineRepository.saveStamp(ownerId, note.id, StampType.MEMO, "FlowItem", ts)

        val items = timelineRepository.getTimelineItemsForMonthFlow(ownerId, note.id, now).first()
        assertEquals(1, items.size)
        assertEquals("FlowItem", (items[0] as StampItem).note)
    }

    @Test
    fun getAllStampItems_returnsAllStampsSorted() = runBlocking {
        val note = noteRepository.createNote("Sort Test")
        val ownerId = emulatorRule.auth.currentUser!!.uid

        timelineRepository.saveStamp(ownerId, note.id, StampType.MEMO, "Second", 2000L)
        timelineRepository.saveStamp(ownerId, note.id, StampType.MEMO, "First", 1000L)

        val items = timelineRepository.getAllStampItems(ownerId, note.id)
        assertEquals(2, items.size)
        assertEquals("First", items[0].note)
        assertEquals("Second", items[1].note)
    }

    @Test
    fun saveStamps_bulkSavesData() = runBlocking {
        val note = noteRepository.createNote("Bulk Test")
        val ownerId = emulatorRule.auth.currentUser!!.uid
        val stamps = listOf(
            StampItem(100L, StampType.FUN, "Fun 1"),
            StampItem(200L, StampType.FUN, "Fun 2")
        )

        timelineRepository.saveStamps(ownerId, note.id, stamps)

        val items = timelineRepository.getAllStampItems(ownerId, note.id)
        assertEquals(2, items.size)
    }

    @Test
    fun deleteTimelineItem_removesSpecificItem() = runBlocking {
        val note = noteRepository.createNote("Delete Item Test")
        val ownerId = emulatorRule.auth.currentUser!!.uid
        val item = StampItem(1000L, StampType.MEMO, "To Delete")

        timelineRepository.saveStamp(ownerId, note.id, item.type, item.note, item.timestamp)
        timelineRepository.deleteTimelineItem(ownerId, note.id, item)

        assertNull(timelineRepository.getStampItem(ownerId, note.id, 1000L))
    }

    @Test
    fun getStampNoteSuggestions_returnsUniqueRecentNotes() = runBlocking {
        val note = noteRepository.createNote("Suggest Test")
        val ownerId = emulatorRule.auth.currentUser!!.uid

        timelineRepository.saveStamp(ownerId, note.id, StampType.MEMO, "A", 1000L)
        timelineRepository.saveStamp(ownerId, note.id, StampType.MEMO, "B", 2000L)
        timelineRepository.saveStamp(ownerId, note.id, StampType.MEMO, "A", 3000L)

        val suggestions = timelineRepository.getStampNoteSuggestions(ownerId, note.id, StampType.MEMO)
        assertEquals(2, suggestions.size)
        assertEquals("A", suggestions[0]) // Most recent unique
        assertEquals("B", suggestions[1])
    }
}
