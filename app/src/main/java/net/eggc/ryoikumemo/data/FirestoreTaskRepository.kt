package net.eggc.ryoikumemo.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreTaskRepository(
    private val db: FirebaseFirestore
) : TaskRepository {

    private fun taskCollection(ownerId: String, noteId: String) =
        db.collection("users").document(ownerId).collection("notes").document(noteId).collection("tasks")

    private fun mapSnapshotToTasks(snapshot: QuerySnapshot?): List<Task> {
        return snapshot?.documents?.mapNotNull { doc ->
            Task(
                id = doc.id,
                name = doc.getString("name") ?: "",
                isCompleted = doc.getBoolean("isCompleted") ?: false,
                timestamp = doc.getLong("timestamp") ?: 0L
            )
        } ?: emptyList()
    }

    override fun getTasksFlow(ownerId: String, noteId: String): Flow<List<Task>> = callbackFlow {
        val subscription = taskCollection(ownerId, noteId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(mapSnapshotToTasks(snapshot))
            }
        awaitClose { subscription.remove() }
    }

    override suspend fun createTask(ownerId: String, noteId: String, name: String): Task {
        val taskRef = taskCollection(ownerId, noteId).document()
        val timestamp = System.currentTimeMillis()
        val taskData = mapOf(
            "name" to name,
            "isCompleted" to false,
            "timestamp" to timestamp
        )
        taskRef.set(taskData).await()
        return Task(taskRef.id, name, false, timestamp)
    }

    override suspend fun updateTaskProgress(ownerId: String, noteId: String, taskId: String, isCompleted: Boolean) {
        taskCollection(ownerId, noteId).document(taskId).update("isCompleted", isCompleted).await()
    }

    override suspend fun deleteTask(ownerId: String, noteId: String, taskId: String) {
        taskCollection(ownerId, noteId).document(taskId).delete().await()
    }
}
