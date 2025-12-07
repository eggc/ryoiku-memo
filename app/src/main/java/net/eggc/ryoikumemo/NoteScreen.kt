package net.eggc.ryoikumemo

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
) {
    val coroutineScope = rememberCoroutineScope()
    var notes by remember { mutableStateOf<List<Note>>(emptyList()) }
    var showAddNoteDialog by remember { mutableStateOf(false) }

    fun refreshNotes() {
        coroutineScope.launch {
            notes = timelineRepository.getNotes()
        }
    }

    LaunchedEffect(Unit) {
        refreshNotes()
    }

    if (showAddNoteDialog) {
        AddNoteDialog(
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

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddNoteDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "ノートを追加")
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(notes) { note ->
                Card(
                    onClick = { onNoteSelected(note) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = note.name,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun AddNoteDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var noteName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新しいノート") },
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
                Text("作成")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}
