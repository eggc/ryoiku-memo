package net.eggc.ryoikumemo.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import net.eggc.ryoikumemo.rule.FirestoreEmulatorRule
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

    @Before
    fun setUp() {
        repository = FirestoreNoteRepository(emulatorRule.db, emulatorRule.auth)
    }

    @Test
    fun getNotes_returnsEmptyList_whenNoNotesExist() = runBlocking {
        val notes = repository.getNotes()
        assertTrue("Initially notes should be empty", notes.isEmpty())
    }
}
