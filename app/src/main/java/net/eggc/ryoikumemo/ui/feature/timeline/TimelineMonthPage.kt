package net.eggc.ryoikumemo.ui.feature.timeline

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.eggc.ryoikumemo.data.Note
import net.eggc.ryoikumemo.data.StampItem
import net.eggc.ryoikumemo.data.StampType
import net.eggc.ryoikumemo.data.TimelineItem
import net.eggc.ryoikumemo.data.TimelineRepository
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TimelineMonthPage(
    timelineRepository: TimelineRepository,
    note: Note,
    month: LocalDate,
    selectedFilters: Set<StampType>,
    targetDate: LocalDate?,
    onTargetDateScrolled: () -> Unit,
    onEditStampClick: (Long) -> Unit,
    onDateClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val timelineItems by remember(note.id, month) {
        timelineRepository.getTimelineItemsForMonthFlow(note.ownerId, note.id, month)
    }.collectAsState(initial = null)

    var isRefreshing by remember { mutableStateOf(false) }
    var showDeleteDialogFor by remember { mutableStateOf<TimelineItem?>(null) }
    val listState = rememberLazyListState()

    val groupedItems = remember(timelineItems, selectedFilters) {
        val items = timelineItems ?: return@remember emptyMap<LocalDate, List<TimelineItem>>()
        val filteredItems = if (selectedFilters.isEmpty()) {
            items
        } else {
            items.filter { it is StampItem && selectedFilters.contains(it.type) }
        }
        filteredItems.groupBy {
            Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        }
    }

    LaunchedEffect(targetDate, timelineItems) {
        if (targetDate != null && timelineItems != null && groupedItems.isNotEmpty()) {
            val availableDates = groupedItems.keys.sortedDescending()
            val jumpToDate = availableDates.firstOrNull { it <= targetDate } ?: availableDates.last()

            var index = 0
            for (date in availableDates) {
                if (date == jumpToDate) break
                index += 1 + (groupedItems[date]?.size ?: 0)
            }

            listState.animateScrollToItem(index)
            onTargetDateScrolled()
        }
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
                                timelineRepository.deleteTimelineItem(note.ownerId, note.id, itemToDelete)
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

    if (timelineItems == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                coroutineScope.launch {
                    isRefreshing = true
                    try {
                        timelineRepository.getTimelineItemsForMonth(note.ownerId, note.id, month)
                    } finally {
                        delay(500)
                        isRefreshing = false
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState
            ) {
                if (groupedItems.isEmpty()) {
                    item {
                        Text(
                            text = if (selectedFilters.isEmpty()) "この月の記録はありません。" else "条件に合う記録はありません。",
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    groupedItems.forEach { (date, items) ->
                        stickyHeader(key = "header_${date}") {
                            Surface(
                                modifier = Modifier
                                    .fillParentMaxWidth()
                                    .clickable { onDateClick() },
                                color = MaterialTheme.colorScheme.primaryContainer
                                    .copy(alpha = 0.95f)
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
                    // フローティングボタンに隠れないための余白を追加
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun StampHistoryCard(
    timestamp: Long,
    stampType: StampType,
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
