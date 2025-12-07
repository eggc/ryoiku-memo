package net.eggc.ryoikumemo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.eggc.ryoikumemo.data.Note
import net.eggc.ryoikumemo.data.TimelineRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteScreen(
    modifier: Modifier = Modifier,
    timelineRepository: TimelineRepository,
    onNoteSelected: (Note) -> Unit,
    currentNoteId: String,
) {
    val coroutineScope = rememberCoroutineScope()
    var notes by remember { mutableStateOf<List<Note>>(emptyList()) }
    var showAddNoteDialog by remember { mutableStateOf(false) }
    var showEditNoteDialog by remember { mutableStateOf<Note?>(null) }
    var showDeleteNoteDialog by remember { mutableStateOf<Note?>(null) }

    fun refreshNotes() {
        coroutineScope.launch {
            notes = timelineRepository.getNotes()
        }
    }

    LaunchedEffect(Unit) {
        refreshNotes()
    }

    if (showAddNoteDialog) {
        EditNoteDialog(
            onDismiss = { showAddNoteDialog = false },
            onConfirm = { noteName ->
                coroutineScope.launch {
                    timelineRepository.createNote(noteName)
                    refreshNotes()
                }
                showAddNoteDialog = false
            }
        )
    }

    showEditNoteDialog?.let { note ->
        EditNoteDialog(
            initialName = note.name,
            onDismiss = { showEditNoteDialog = null },
            onConfirm = { newName ->
                coroutineScope.launch {
                    timelineRepository.updateNote(note.copy(name = newName))
                    refreshNotes()
                }
                showEditNoteDialog = null
            }
        )
    }

    showDeleteNoteDialog?.let { note ->
        DeleteNoteDialog(
            note = note,
            onDismiss = { showDeleteNoteDialog = null },
            onConfirm = {
                coroutineScope.launch {
                    timelineRepository.deleteNote(note.id)
                    refreshNotes()
                }
                showDeleteNoteDialog = null
            }
        )
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddNoteDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "ノートを追加")
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier
            .padding(padding)
            .fillMaxSize()) {
            items(notes) { note ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clickable { onNoteSelected(note) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = note.name,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { showEditNoteDialog = note }) {
                            Icon(Icons.Default.Edit, contentDescription = "編集")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { showDeleteNoteDialog = note },
                            enabled = note.id != currentNoteId
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "削除")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditNoteDialog(
    initialName: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var noteName by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialName.isEmpty()) "新しいノート" else "ノート名を編集") },
        text = {
            TextField(
                value = noteName,
                onValueChange = { noteName = it },
                label = { Text("ノート名") }
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (noteName.isNotBlank()) {
                        onConfirm(noteName)
                    }
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

@Composable
private fun DeleteNoteDialog(
    note: Note,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ノートを削除") },
        text = { Text("${note.name} と、それに紐づくすべての記録を削除します。よろしいですか？") },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("削除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}
