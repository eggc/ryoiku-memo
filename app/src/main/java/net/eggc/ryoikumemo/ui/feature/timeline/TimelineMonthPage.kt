package net.eggc.ryoikumemo.ui.feature.timeline

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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

/** タイムラインの1行分のデータ */
private data class TimelineRow(
    val item: StampItem,
    val date: LocalDate,
    /** この行が日付列に日付を表示するかどうか（同一日付の最初の行のみ true） */
    val showDate: Boolean
)

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

    val timelineRows = remember(timelineItems, selectedFilters) {
        buildTimelineRows(timelineItems, selectedFilters)
    }

    LaunchedEffect(targetDate, timelineItems) {
        if (targetDate != null && timelineItems != null && timelineRows.isNotEmpty()) {
            val index = timelineRows.indexOfFirst { it.date <= targetDate && it.showDate }
                .takeIf { it >= 0 } ?: 0
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
                timelineRows = timelineRows,
                listState = listState,
                isFiltered = selectedFilters.isNotEmpty(),
                onEditStampClick = onEditStampClick,
                onDeleteClick = { showDeleteDialogFor = it }
            )
        }
    }
}

/**
 * フィルタを適用し、各行に showDate フラグを付けたリストを構築する。
 * リストは新しい順（降順）。同じ日付の最初の行のみ showDate = true。
 */
private fun buildTimelineRows(
    items: List<TimelineItem>?,
    selectedFilters: Set<StampType>
): List<TimelineRow> {
    if (items == null) return emptyList()

    val filtered = if (selectedFilters.isEmpty()) items
    else items.filter { it is StampItem && selectedFilters.contains(it.type) }

    var prevDate: LocalDate? = null
    return filtered.filterIsInstance<StampItem>().map { item ->
        val date = Instant.ofEpochMilli(item.timestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        val showDate = date != prevDate
        prevDate = date
        TimelineRow(item = item, date = date, showDate = showDate)
    }
}

@Composable
private fun TimelineList(
    timelineRows: List<TimelineRow>,
    listState: LazyListState,
    isFiltered: Boolean,
    onEditStampClick: (Long) -> Unit,
    onDeleteClick: (TimelineItem) -> Unit
) {
    // 日付列 + 時刻列の合計幅分を surfaceVariant で塗りつぶす背景
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .width(DATE_COLUMN_WIDTH + TIME_COLUMN_WIDTH)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState
        ) {
            if (timelineRows.isEmpty()) {
                item {
                    Text(
                        text = if (!isFiltered) "この月の記録はありません。" else "条件に合う記録はありません。",
                        modifier = Modifier
                            .padding(16.dp)
                            .padding(start = DATE_COLUMN_WIDTH + TIME_COLUMN_WIDTH)
                    )
                }
            } else {
                items(timelineRows, key = { "stamp_${it.item.timestamp}" }) { row ->
                    TimelineItemCard(
                        timestamp = row.item.timestamp,
                        date = row.date,
                        showDate = row.showDate,
                        stampType = row.item.type,
                        note = row.item.note,
                        operatorName = row.item.operatorName,
                        onEditClick = { onEditStampClick(row.item.timestamp) },
                        onDeleteClick = { onDeleteClick(row.item) }
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
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
