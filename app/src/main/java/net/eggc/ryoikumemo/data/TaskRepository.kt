package net.eggc.ryoikumemo.data

import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    fun getTasksFlow(ownerId: String, noteId: String): Flow<List<Task>>
    suspend fun createTask(ownerId: String, noteId: String, name: String): Task
    suspend fun updateTaskProgress(ownerId: String, noteId: String, taskId: String, isCompleted: Boolean)
    suspend fun deleteTask(ownerId: String, noteId: String, taskId: String)
}
