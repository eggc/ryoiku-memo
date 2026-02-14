package net.eggc.ryoikumemo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.eggc.ryoikumemo.data.Note
import net.eggc.ryoikumemo.data.NoteRepository

@Composable
fun TaskScreen(
    modifier: Modifier = Modifier,
    noteRepository: NoteRepository,
    note: Note
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "タスク管理（準備中）", style = MaterialTheme.typography.headlineSmall)
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(text = "ここにタスク一覧が表示される予定です。")
        }
    }
}
