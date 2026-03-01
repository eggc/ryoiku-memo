package net.eggc.ryoikumemo.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.eggc.ryoikumemo.rule.FirestoreEmulatorRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirestoreTaskRepositoryTest {

    @get:Rule
    val emulatorRule = FirestoreEmulatorRule()

    private lateinit var taskRepository: FirestoreTaskRepository
    private lateinit var noteRepository: FirestoreNoteRepository

    @Before
    fun setUp() {
        taskRepository = FirestoreTaskRepository(emulatorRule.db)
        noteRepository = FirestoreNoteRepository(emulatorRule.db, emulatorRule.auth)
    }

    @Test
    fun taskOperations_lifecycle() = runBlocking {
        val note = noteRepository.createNote("Task Test")
        val ownerId = emulatorRule.auth.currentUser!!.uid

        // Create
        val task = taskRepository.createTask(ownerId, note.id, "Buy Milk")
        var tasks = taskRepository.getTasksFlow(ownerId, note.id).first()
        assertEquals(1, tasks.size)
        assertEquals("Buy Milk", tasks[0].name)
        assertEquals(false, tasks[0].isCompleted)

        // Update
        taskRepository.updateTaskProgress(ownerId, note.id, task.id, true)
        tasks = taskRepository.getTasksFlow(ownerId, note.id).first()
        assertEquals(true, tasks[0].isCompleted)

        // Delete
        taskRepository.deleteTask(ownerId, note.id, task.id)
        tasks = taskRepository.getTasksFlow(ownerId, note.id).first()
        assertTrue(tasks.isEmpty())
    }
}
