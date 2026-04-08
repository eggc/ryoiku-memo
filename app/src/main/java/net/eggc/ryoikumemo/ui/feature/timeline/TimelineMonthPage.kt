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
import androidx.compose.ui.text.style.TextAlign
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
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** タイムラインに表示する行の種類 */
private sealed interface TimelineRow {
    /** 日付ヘッダー行（先頭の現在日、または日付跨ぎの区切り） */
    data class DateHeader(val date: LocalDate) : TimelineRow

    /** 00:00 + 日付を横並びで表示する「日付跨ぎ」行 */
    data class DateBoundary(val date: LocalDate) : TimelineRow

    /** スタンプアイテム行 */
    data class Stamp(val item: StampItem) : TimelineRow
}

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

    // フィルタ適用済みアイテム（新しい順）
    val filteredItems = remember(timelineItems, selectedFilters) {
        val items = timelineItems ?: return@remember null
        if (selectedFilters.isEmpty()) items
        else items.filter { it is StampItem && selectedFilters.contains(it.type) }
    }

    // タイムラインに表示する行リストを構築
    val timelineRows = remember(filteredItems, month) {
        buildTimelineRows(filteredItems, month)
    }

    LaunchedEffect(targetDate, timelineItems) {
        if (targetDate != null && timelineItems != null && timelineRows.isNotEmpty()) {
            // targetDate 以前の最初の DateHeader/DateBoundary のインデックスへスクロール
            val index = timelineRows.indexOfFirst { row ->
                when (row) {
                    is TimelineRow.DateHeader -> row.date <= targetDate
                    is TimelineRow.DateBoundary -> row.date <= targetDate
                    else -> false
                }
            }.takeIf { it >= 0 } ?: 0
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
                hasItems = filteredItems?.isNotEmpty() == true,
                onDateClick = onDateClick,
                onEditStampClick = onEditStampClick,
                onDeleteClick = { showDeleteDialogFor = it }
            )
        }
    }
}

/**
 * タイムラインに表示する行リストを構築する。
 *
 * 新しい順に並んだ [items] を受け取り、以下のルールで行を組み立てる：
 * 1. 先頭に「今日 or 月末」の DateHeader を挿入する。
 * 2. アイテムの日付が前のアイテムの日付より古くなる（日付跨ぎ）タイミングで
 *    DateBoundary（00:00 + 日付）を挿入する。
 */
private fun buildTimelineRows(
    items: List<TimelineItem>?,
    month: LocalDate
): List<TimelineRow> {
    val rows = mutableListOf<TimelineRow>()

    // 先頭日付: 今日が表示月内なら今日、それ以外は月末
    val today = LocalDate.now()
    val lastDayOfMonth = YearMonth.of(month.year, month.month).atEndOfMonth()
    val headerDate = if (today.year == month.year && today.month == month.month) today else lastDayOfMonth
    rows.add(TimelineRow.DateHeader(headerDate))

    if (items.isNullOrEmpty()) return rows

    var prevDate: LocalDate = headerDate

    for (item in items) {
        if (item !is StampItem) continue

        val itemDate = Instant.ofEpochMilli(item.timestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()

        // 日付が変わったら DateBoundary を挿入（逆順なので prevDate > itemDate のとき跨ぎ）
        if (itemDate < prevDate) {
            rows.add(TimelineRow.DateBoundary(itemDate))
            prevDate = itemDate
        }

        rows.add(TimelineRow.Stamp(item))
    }

    return rows
}

@Composable
private fun TimelineList(
    timelineRows: List<TimelineRow>,
    listState: LazyListState,
    isFiltered: Boolean,
    hasItems: Boolean,
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
            if (!hasItems) {
                // DateHeader（先頭の現在日）は常に表示
                timelineRows.filterIsInstance<TimelineRow.DateHeader>().firstOrNull()?.let { header ->
                    item(key = "date_header_${header.date}") {
                        TimelineDateRow(date = header.date, onClick = onDateClick)
                    }
                }
                item {
                    Text(
                        text = if (!isFiltered) "この月の記録はありません。" else "条件に合う記録はありません。",
                        modifier = Modifier
                            .padding(16.dp)
                            .padding(start = 52.dp)
                    )
                }
            } else {
                items(
                    items = timelineRows,
                    key = { row ->
                        when (row) {
                            is TimelineRow.DateHeader -> "header_${row.date}"
                            is TimelineRow.DateBoundary -> "boundary_${row.date}"
                            is TimelineRow.Stamp -> "stamp_${row.item.timestamp}"
                        }
                    }
                ) { row ->
                    when (row) {
                        is TimelineRow.DateHeader ->
                            TimelineDateRow(date = row.date, onClick = onDateClick)

                        is TimelineRow.DateBoundary ->
                            TimelineDateBoundaryRow(date = row.date, onClick = onDateClick)

                        is TimelineRow.Stamp ->
                            TimelineItemCard(
                                timestamp = row.item.timestamp,
                                stampType = row.item.type,
                                note = row.item.note,
                                operatorName = row.item.operatorName,
                                onEditClick = { onEditStampClick(row.item.timestamp) },
                                onDeleteClick = { onDeleteClick(row.item) }
                            )
                    }
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

/** 先頭の現在日ヘッダー行 */
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

/** 日付跨ぎ行: 時刻列に「0:00」、その隣に日付を表示 */
@Composable
private fun TimelineDateBoundaryRow(
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
        // 時刻列: 0:00
        Box(
            modifier = Modifier
                .width(52.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(top = 12.dp, bottom = 12.dp, end = 8.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            Text(
                text = "0:00",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End
            )
        }

        // 日付
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
