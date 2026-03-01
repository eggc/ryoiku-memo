package net.eggc.ryoikumemo.ui.feature.task

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.eggc.ryoikumemo.data.Note
import net.eggc.ryoikumemo.data.TaskRepository
import net.eggc.ryoikumemo.data.Task

@Composable
fun TaskScreen(
    modifier: Modifier = Modifier,
    taskRepository: TaskRepository,
    note: Note
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 初期読み込み状態を null で表現
    val tasks by remember(note.id) {
        taskRepository.getTasksFlow(note.ownerId, note.id)
    }.collectAsState(initial = null)

    var newTaskName by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(text = "タスク管理", style = MaterialTheme.typography.headlineSmall)

            Spacer(modifier = Modifier.padding(vertical = 8.dp))

            // タスク入力エリア
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = newTaskName,
                    onValueChange = { if (it.length <= 128) newTaskName = it },
                    label = { Text("新しいタスク") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = !isProcessing
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    enabled = !isProcessing && newTaskName.isNotBlank(),
                    onClick = {
                        coroutineScope.launch {
                            isProcessing = true
                            try {
                                taskRepository.createTask(note.ownerId, note.id, newTaskName)
                                newTaskName = ""
                                // 追加後に先頭へスクロール
                                listState.animateScrollToItem(0)
                            } catch (e: Exception) {
                                Toast.makeText(context, "タスクの作成に失敗しました", Toast.LENGTH_SHORT).show()
                            } finally {
                                isProcessing = false
                            }
                        }
                    }
                ) {
                    Text("追加")
                }
            }

            Spacer(modifier = Modifier.padding(vertical = 12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.padding(vertical = 8.dp))

            // タスク一覧
            if (tasks == null) {
                // 初回読み込み中の表示
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (tasks!!.isEmpty()) {
                        item {
                            Text(
                                text = "タスクはありません。",
                                modifier = Modifier.padding(vertical = 16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(tasks!!, key = { it.id }) { task ->
                            TaskItem(
                                task = task,
                                enabled = !isProcessing,
                                onToggle = {
                                    coroutineScope.launch {
                                        isProcessing = true
                                        try {
                                            taskRepository.updateTaskProgress(note.ownerId, note.id, task.id, !task.isCompleted)
                                        } finally {
                                            isProcessing = false
                                        }
                                    }
                                },
                                onDelete = {
                                    coroutineScope.launch {
                                        isProcessing = true
                                        try {
                                            taskRepository.deleteTask(note.ownerId, note.id, task.id)
                                        } finally {
                                            isProcessing = false
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // 処理中のオーバーレイ表示
        if (isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.1f))
                    .clickable(enabled = false) { },
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun TaskItem(
    task: Task,
    enabled: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (task.isCompleted)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggle,
                enabled = enabled
            ) {
                Icon(
                    imageVector = if (task.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (task.isCompleted) "未完了に戻す" else "完了にする",
                    tint = if (task.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }

            Text(
                text = task.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge.copy(
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                ),
                color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )

            IconButton(
                onClick = onDelete,
                enabled = enabled
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "削除",
                    tint = if (enabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                )
            }
        }
    }
}
