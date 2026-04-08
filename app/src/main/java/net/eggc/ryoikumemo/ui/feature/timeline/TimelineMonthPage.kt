package net.eggc.ryoikumemo.ui.feature.timeline

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
        val filteredItems = if (selectedFilters.isEmpty()) items
        else items.filter { it is StampItem && selectedFilters.contains(it.type) }
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

    DeleteConfirmDialog(
        item = showDeleteDialogFor,
        onDismiss = { showDeleteDialogFor = null },
        onConfirm = { itemToDelete ->
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
    )

    if (timelineItems == null) {
        LoadingIndicator()
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
            TimelineList(
                groupedItems = groupedItems,
                listState = listState,
                isFiltered = selectedFilters.isNotEmpty(),
                onDateClick = onDateClick,
                onEditStampClick = onEditStampClick,
                onDeleteClick = { showDeleteDialogFor = it }
            )
        }
    }
}

@Composable
private fun TimelineList(
    groupedItems: Map<LocalDate, List<TimelineItem>>,
    listState: LazyListState,
    isFiltered: Boolean,
    onDateClick: () -> Unit,
    onEditStampClick: (Long) -> Unit,
    onDeleteClick: (TimelineItem) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 全体の時刻列背景
        Box(
            modifier = Modifier
                .width(52.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState
        ) {
            if (groupedItems.isEmpty()) {
                item {
                    Text(
                        text = if (!isFiltered) "この月の記録はありません。" else "条件に合う記録はありません。",
                        modifier = Modifier.padding(16.dp).padding(start = 52.dp)
                    )
                }
            } else {
                groupedItems.forEach { (date, items) ->
                    item(key = "date_${date}") {
                        TimelineDateRow(date = date, onClick = onDateClick)
                    }
                    items(items, key = { "stamp_${it.timestamp}" }) { item ->
                        if (item is StampItem) {
                            TimelineItemCard(
                                timestamp = item.timestamp,
                                stampType = item.type,
                                note = item.note,
                                operatorName = item.operatorName,
                                onEditClick = { onEditStampClick(item.timestamp) },
                                onDeleteClick = { onDeleteClick(item) }
                            )
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun TimelineDateRow(
    date: LocalDate,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 時刻列の延長
        Box(
            modifier = Modifier
                .width(52.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )

        Text(
            text = date.formatDateWithWeekdayOnlyDay(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .weight(1f)
        )
    }
}

@Composable
private fun LoadingIndicator() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun DeleteConfirmDialog(
    item: TimelineItem?,
    onDismiss: () -> Unit,
    onConfirm: (TimelineItem) -> Unit
) {
    if (item != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("削除") },
            text = { Text("この項目を削除しますか？") },
            confirmButton = {
                TextButton(onClick = { onConfirm(item) }) { Text("はい") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("いいえ") }
            }
        )
    }
}

private fun LocalDate.formatDateWithWeekdayOnlyDay(): String {
    val day = dayOfMonth
    val weekday = when (dayOfWeek) {
        java.time.DayOfWeek.MONDAY -> "月"
        java.time.DayOfWeek.TUESDAY -> "火"
        java.time.DayOfWeek.WEDNESDAY -> "水"
        java.time.DayOfWeek.THURSDAY -> "木"
        java.time.DayOfWeek.FRIDAY -> "金"
        java.time.DayOfWeek.SATURDAY -> "土"
        java.time.DayOfWeek.SUNDAY -> "日"
    }
    return "${day}日（$weekday）"
}
