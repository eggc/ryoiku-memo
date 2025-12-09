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
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.aventrix.jnanoid.jnanoid.NanoIdUtils
import kotlinx.coroutines.launch
import net.eggc.ryoikumemo.data.Note
import net.eggc.ryoikumemo.data.NoteRepository
import net.eggc.ryoikumemo.data.SharedNoteInfo
import java.util.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteScreen(
    modifier: Modifier = Modifier,
    noteRepository: NoteRepository,
    onNoteSelected: (Note) -> Unit,
    currentNoteId: String,
    onNoteUpdated: (Note) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    var notes by remember { mutableStateOf<List<Note>>(emptyList()) }
    var subscribedNotes by remember { mutableStateOf<List<Pair<String, SharedNoteInfo?>>>(emptyList()) }
    var showAddNoteDialog by remember { mutableStateOf(false) }
    var showEditNoteDialog by remember { mutableStateOf<Note?>(null) }
    var showDeleteNoteDialog by remember { mutableStateOf<Note?>(null) }
    var showSubscribeDialog by remember { mutableStateOf(false) }
    var showUnsubscribeDialog by remember { mutableStateOf<String?>(null) }

    fun refreshNotes() {
        coroutineScope.launch {
            notes = noteRepository.getNotes()
            val ids = noteRepository.getSubscribedNoteIds()
            subscribedNotes = ids.map {
                it to noteRepository.getNoteBySharedId(it)
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshNotes()
    }

    if (showAddNoteDialog) {
        EditNoteDialog(
            onDismiss = { showAddNoteDialog = false },
            onConfirm = { noteName, sharedId ->
                coroutineScope.launch {
                    noteRepository.createNote(noteName, sharedId)
                    refreshNotes()
                }
                showAddNoteDialog = false
            }
        )
    }

    showEditNoteDialog?.let { note ->
        EditNoteDialog(
            initialName = note.name,
            initialSharedId = note.sharedId,
            onDismiss = { showEditNoteDialog = null },
            onConfirm = { newName, newSharedId ->
                val updatedNote = note.copy(name = newName, sharedId = newSharedId)
                coroutineScope.launch {
                    noteRepository.updateNote(updatedNote)
                    refreshNotes()
                    onNoteUpdated(updatedNote)
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
                    noteRepository.deleteNote(note.id)
                    refreshNotes()
                }
                showDeleteNoteDialog = null
            }
        )
    }

    if (showSubscribeDialog) {
        SubscribeNoteDialog(
            onDismiss = { showSubscribeDialog = false },
            onConfirm = { sharedId ->
                coroutineScope.launch {
                    noteRepository.subscribeToSharedNote(sharedId)
                    refreshNotes()
                }
                showSubscribeDialog = false
            }
        )
    }

    showUnsubscribeDialog?.let { sharedId ->
        UnsubscribeDialog(
            sharedId = sharedId,
            onDismiss = { showUnsubscribeDialog = null },
            onConfirm = {
                coroutineScope.launch {
                    noteRepository.unsubscribeFromSharedNote(sharedId)
                    refreshNotes()
                }
                showUnsubscribeDialog = null
            }
        )
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                FloatingActionButton(onClick = { showAddNoteDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "ノートを追加")
                }
                Spacer(modifier = Modifier.padding(8.dp))
                FloatingActionButton(onClick = { showSubscribeDialog = true }) {
                    Icon(Icons.Default.Share, contentDescription = "共有ノートを購読")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
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
                        Icon(Icons.Default.Book, contentDescription = "ノート")
                        Spacer(modifier = Modifier.width(16.dp))
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
            if (subscribedNotes.isNotEmpty()) {
                item {
                    Text(
                        text = "購読中のノート",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)
                    )
                }
                items(subscribedNotes) { (sharedId, info) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                if (info != null) {
                                    Text(text = info.noteName, style = MaterialTheme.typography.titleLarge)
                                    Text(text = "持ち主: ${info.ownerId}", style = MaterialTheme.typography.bodySmall)
                                } else {
                                    Text(
                                        text = "参照エラー",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(text = "ID: $sharedId", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            IconButton(onClick = { showUnsubscribeDialog = sharedId }) {
                                Icon(Icons.Default.Delete, contentDescription = "購読を解除")
                            }
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
    initialSharedId: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, String?) -> Unit
) {
    var noteName by remember { mutableStateOf(initialName) }
    var sharedId by remember { mutableStateOf(initialSharedId) }
    var isShared by remember { mutableStateOf(initialSharedId != null) }
    val clipboardManager = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialName.isEmpty()) "新しいノート" else "ノートを編集") },
        text = {
            Column {
                TextField(
                    value = noteName,
                    onValueChange = { noteName = it },
                    label = { Text("ノート名") }
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text("共有ノート")
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = isShared,
                        onCheckedChange = {
                            isShared = it
                            if (it) {
                                if (sharedId == null) {
                                    sharedId = NanoIdUtils.randomNanoId(
                                        Random(),
                                        "0123456789abcdefghijklmnopqrstuvwxyz".toCharArray(),
                                        10
                                    )
                                }
                            } else {
                                sharedId = null
                            }
                        }
                    )
                }
                if (isShared && sharedId != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "共有ノートID: $sharedId",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        IconButton(onClick = { clipboardManager.setText(AnnotatedString(sharedId!!)) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "コピー")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (noteName.isNotBlank()) {
                        onConfirm(noteName, sharedId)
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

@Composable
private fun SubscribeNoteDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var sharedId by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("共有ノートを購読") },
        text = {
            TextField(
                value = sharedId,
                onValueChange = { sharedId = it },
                label = { Text("共有ID") }
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (sharedId.isNotBlank()) {
                        onConfirm(sharedId)
                    }
                }
            ) {
                Text("購読")
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
private fun UnsubscribeDialog(
    sharedId: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("購読を停止") },
        text = { Text("このノートの購読を停止しますか？") },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("停止する")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}
