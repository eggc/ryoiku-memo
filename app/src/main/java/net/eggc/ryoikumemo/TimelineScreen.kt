package net.eggc.ryoikumemo

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.eggc.ryoikumemo.data.Note
import net.eggc.ryoikumemo.data.NoteRepository
import net.eggc.ryoikumemo.data.StampItem
import net.eggc.ryoikumemo.data.TimelineItem
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    modifier: Modifier = Modifier,
    noteRepository: NoteRepository,
    note: Note,
    onEditStampClick: (Long) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var timelineItems by remember { mutableStateOf<List<TimelineItem>>(emptyList()) }
    var showDeleteDialogFor by remember { mutableStateOf<TimelineItem?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var currentMonth by remember { mutableStateOf(LocalDate.now()) }

    fun refreshTimeline() {
        coroutineScope.launch {
            isLoading = true
            try {
                timelineItems = noteRepository.getTimelineItemsForMonth(note.ownerId, note.id, note.sharedId, currentMonth)
            } catch (e: Exception) {
                Log.e("TimelineScreen", "Failed to load timeline items", e)
                Toast.makeText(context, "データの読み込みに失敗しました", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(note.id, currentMonth) {
        refreshTimeline()
    }

    if (showDeleteDialogFor != null) {
        val itemToDelete = showDeleteDialogFor!!
        AlertDialog(
            onDismissRequest = { showDeleteDialogFor = null },
            title = { Text("削除") },
            text = { Text("この項目を削除しますか？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                noteRepository.deleteTimelineItem(note.ownerId, note.id, itemToDelete)
                                refreshTimeline()
                                showDeleteDialogFor = null
                                Toast.makeText(context, "削除しました", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Log.e("TimelineScreen", "Failed to delete timeline item", e)
                                Toast.makeText(context, "データの削除に失敗しました", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("はい")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialogFor = null }) {
                    Text("いいえ")
                }
            }
        )
    }

    val groupedItems = timelineItems.groupBy {
        Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    }

    Column(modifier = modifier) {
        MonthSelector(currentMonth = currentMonth, onMonthChange = { currentMonth = it })

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (groupedItems.isEmpty()) {
                    item {
                        Text(
                            text = "この月の記録はありません。",
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    groupedItems.forEach { (date, items) ->
                        stickyHeader {
                            Surface(
                                modifier = Modifier.fillParentMaxWidth(),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = date.format(DateTimeFormatter.ofPattern("yyyy年M月d日")),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(vertical = 4.dp, horizontal = 16.dp)
                                )
                            }
                        }
                        items(items, key = {
                            "stamp_${it.timestamp}"
                        }) { item ->
                            if (item is StampItem) {
                                Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                                    StampHistoryCard(
                                        timestamp = item.timestamp,
                                        stampType = item.type,
                                        note = item.note,
                                        operatorName = item.operatorName,
                                        onEditClick = { onEditStampClick(item.timestamp) },
                                        onDeleteClick = { showDeleteDialogFor = item }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MonthSelector(currentMonth: LocalDate, onMonthChange: (LocalDate) -> Unit) {
    val formatter = DateTimeFormatter.ofPattern("yyyy年 M月")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = { onMonthChange(currentMonth.minusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "前の月")
        }
        Text(
            text = currentMonth.format(formatter),
            style = MaterialTheme.typography.titleMedium
        )
        IconButton(onClick = { onMonthChange(currentMonth.plusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "次の月")
        }
    }
}

@Composable
fun StampHistoryCard(
    timestamp: Long,
    stampType: net.eggc.ryoikumemo.data.StampType,
    note: String,
    operatorName: String?,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(stampType.icon, contentDescription = stampType.label, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stampType.label, style = MaterialTheme.typography.bodyLarge)
            }
            if (note.isNotBlank()) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp)),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = operatorName ?: "不明",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Default.Edit, contentDescription = "編集")
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Default.Delete, contentDescription = "削除")
                }
            }
        }
    }
}
