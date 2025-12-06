package net.eggc.ryoikumemo

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
    onEditDiaryClick: (String) -> Unit,
    onEditStampClick: (Long) -> Unit
) {
    val context = LocalContext.current
    val dateParser = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun getTimelineItems(): List<TimelineItem> {
        val diaryPrefs = context.getSharedPreferences("diary_prefs", Context.MODE_PRIVATE)
        val diaries = diaryPrefs.all.mapNotNull { (key, value) ->
            try {
                val diaryTimestamp = LocalDate.parse(key, dateParser).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                DiaryItem(timestamp = diaryTimestamp, text = value as String, date = key)
            } catch (e: Exception) {
                null
            }
        }

        val stampPrefs = context.getSharedPreferences("stamp_prefs", Context.MODE_PRIVATE)
        val stamps = stampPrefs.all.mapNotNull { (key, value) ->
            try {
                val valueString = value as String
                val parts = valueString.split('|', limit = 2)
                val type = StampType.valueOf(parts[0])
                val note = if (parts.size > 1) parts[1] else ""
                StampItem(timestamp = key.toLong(), type = type, note = note)
            } catch (e: Exception) {
                null
            }
        }

        return (diaries + stamps).sortedByDescending { it.timestamp }
    }

    var timelineItems by remember { mutableStateOf(getTimelineItems()) }
    var showDeleteDialogFor by remember { mutableStateOf<TimelineItem?>(null) }
    var currentFilter by remember { mutableStateOf<TimelineFilter>(TimelineFilter.All) }


    if (showDeleteDialogFor != null) {
        val itemToDelete = showDeleteDialogFor!!
        AlertDialog(
            onDismissRequest = { showDeleteDialogFor = null },
            title = { Text("削除") },
            text = { Text("この項目を削除しますか？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val keyToDelete = when (itemToDelete) {
                            is DiaryItem -> itemToDelete.date
                            is StampItem -> itemToDelete.timestamp.toString()
                        }
                        val prefsName = when (itemToDelete) {
                            is DiaryItem -> "diary_prefs"
                            is StampItem -> "stamp_prefs"
                        }
                        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                        with(prefs.edit()) {
                            remove(keyToDelete)
                            apply()
                        }
                        timelineItems = getTimelineItems() // Refresh the list
                        showDeleteDialogFor = null
                        Toast.makeText(context, "削除しました", Toast.LENGTH_SHORT).show()
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

    val filteredItems = remember(timelineItems, currentFilter) {
        when (val filter = currentFilter) {
            is TimelineFilter.All -> timelineItems
            is TimelineFilter.DiaryOnly -> timelineItems.filterIsInstance<DiaryItem>()
            is TimelineFilter.StampOnly -> timelineItems.filter { it is StampItem && it.type == filter.type }
        }
    }

    val groupedItems = filteredItems.groupBy {
        Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    }

    Column(modifier = modifier) {
        val filterOptions: List<TimelineFilter> =
            listOf(TimelineFilter.All, TimelineFilter.DiaryOnly) + StampType.entries.map { TimelineFilter.StampOnly(it) }

        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
        ) {
            filterOptions.forEach { filter ->
                FilterChip(
                    modifier = Modifier.padding(end = 8.dp),
                    selected = filter == currentFilter,
                    onClick = { currentFilter = filter },
                    label = {
                        Text(
                            when (filter) {
                                is TimelineFilter.All -> "すべて"
                                is TimelineFilter.DiaryOnly -> "日記"
                                is TimelineFilter.StampOnly -> filter.type.label
                            }
                        )
                    }
                )
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (groupedItems.isEmpty()) {
                item {
                    Text(
                        text = if (currentFilter == TimelineFilter.All) "まだ記録はありません。" else "この条件の記録はありません。",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                groupedItems.forEach { (date, items) ->
                    stickyHeader {
                        Surface(modifier = Modifier.fillParentMaxWidth(), color = MaterialTheme.colorScheme.primaryContainer) {
                            Text(
                                text = date.format(DateTimeFormatter.ofPattern("M月d日")),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(vertical = 4.dp, horizontal = 16.dp)
                            )
                        }
                    }
                    items(items, key = { it.timestamp }) { item ->
                        Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                            when (item) {
                                is DiaryItem -> DiaryCard(
                                    text = item.text,
                                    onEditClick = { onEditDiaryClick(item.date) },
                                    onDeleteClick = { showDeleteDialogFor = item }
                                )

                                is StampItem -> StampHistoryCard(
                                    timestamp = item.timestamp,
                                    stampType = item.type,
                                    note = item.note,
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

@Composable
fun DiaryCard(text: String, onEditClick: () -> Unit, onDeleteClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "日記",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = text, style = MaterialTheme.typography.bodyLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onEditClick) {
                    Text("編集")
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onDeleteClick) {
                    Text("削除")
                }
            }
        }
    }
}

@Composable
fun StampHistoryCard(timestamp: Long, stampType: StampType, note: String, onEditClick: () -> Unit, onDeleteClick: () -> Unit) {
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
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onEditClick) {
                    Text("編集")
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onDeleteClick) {
                    Text("削除")
                }
            }
        }
    }
}
