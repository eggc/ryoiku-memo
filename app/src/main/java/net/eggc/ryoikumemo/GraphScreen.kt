package net.eggc.ryoikumemo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.eggc.ryoikumemo.data.Note
import net.eggc.ryoikumemo.data.NoteRepository
import net.eggc.ryoikumemo.data.StampItem
import net.eggc.ryoikumemo.data.StampType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private val BASE_MONTH = LocalDate.of(2020, 1, 1)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GraphScreen(
    modifier: Modifier = Modifier,
    noteRepository: NoteRepository,
    note: Note,
    currentMonth: LocalDate,
    onMonthChange: (LocalDate) -> Unit
) {
    val initialPage = remember { ChronoUnit.MONTHS.between(BASE_MONTH, currentMonth.withDayOfMonth(1)).toInt() }
    val pagerState = rememberPagerState(initialPage = initialPage) { 1200 } // 100 years

    // Sync pager -> external state
    LaunchedEffect(pagerState.currentPage) {
        val month = BASE_MONTH.plusMonths(pagerState.currentPage.toLong())
        if (!month.isEqual(currentMonth.withDayOfMonth(1))) {
            onMonthChange(month)
        }
    }

    // Sync external state -> pager
    LaunchedEffect(currentMonth) {
        val page = ChronoUnit.MONTHS.between(BASE_MONTH, currentMonth.withDayOfMonth(1)).toInt()
        if (pagerState.currentPage != page) {
            pagerState.animateScrollToPage(page)
        }
    }

    Column(modifier = modifier) {
        MonthSelector(currentMonth = currentMonth, onMonthChange = onMonthChange)

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val month = BASE_MONTH.plusMonths(page.toLong())
            GraphMonthPage(
                noteRepository = noteRepository,
                note = note,
                month = month
            )
        }
    }
}

@Composable
fun GraphMonthPage(
    noteRepository: NoteRepository,
    note: Note,
    month: LocalDate
) {
    // addSnapshotListener を利用した Flow に変更
    val timelineItems by remember(note.id, month) {
        noteRepository.getTimelineItemsForMonthFlow(note.ownerId, note.id, month)
    }.collectAsState(initial = null)

    val sleepData = remember(timelineItems) {
        val items = timelineItems ?: emptyList()
        val sleepWakeItems = items.filterIsInstance<StampItem>().filter {
            it.type == StampType.SLEEP || it.type == StampType.WAKE_UP
        }.sortedBy { it.timestamp }

        val dailyData = mutableMapOf<Int, MutableList<StampItem>>()
        for (item in sleepWakeItems) {
            val date = Instant.ofEpochMilli(item.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
            if (date.month == month.month && date.year == month.year) {
                dailyData.getOrPut(date.dayOfMonth) { mutableListOf() }.add(item)
            }
        }

        val processedData = mutableMapOf<Int, List<Pair<Float, Float>>>()
        for ((day, dayItems) in dailyData) {
            val intervals = mutableListOf<Pair<Float, Float>>()
            var start: StampItem? = null
            for (item in dayItems) {
                if (item.type == StampType.SLEEP) {
                    if (start != null) { // Previous sleep was not closed
                        val startInstant = Instant.ofEpochMilli(start!!.timestamp).atZone(ZoneId.systemDefault())
                        val endInstant = startInstant.toLocalDate().atTime(23, 59).atZone(ZoneId.systemDefault())
                        val startMinutes = startInstant.hour * 60 + startInstant.minute
                        val endMinutes = endInstant.hour * 60 + endInstant.minute
                        intervals.add(Pair(startMinutes.toFloat(), endMinutes.toFloat()))
                    }
                    start = item
                } else if (item.type == StampType.WAKE_UP) {
                    val startInstant = if (start != null) {
                        Instant.ofEpochMilli(start.timestamp).atZone(ZoneId.systemDefault())
                    } else {
                        Instant.ofEpochMilli(item.timestamp).atZone(ZoneId.systemDefault()).truncatedTo(ChronoUnit.DAYS)
                    }
                    val endInstant = Instant.ofEpochMilli(item.timestamp).atZone(ZoneId.systemDefault())
                    val startMinutes = startInstant.hour * 60 + startInstant.minute
                    val endMinutes = endInstant.hour * 60 + endInstant.minute
                    intervals.add(Pair(startMinutes.toFloat(), endMinutes.toFloat()))
                    start = null
                }
            }
            if (start != null) { // Unclosed sleep at end of day
                val startInstant = Instant.ofEpochMilli(start.timestamp).atZone(ZoneId.systemDefault())
                val endInstant = startInstant.toLocalDate().atTime(23, 59).atZone(ZoneId.systemDefault())
                val startMinutes = startInstant.hour * 60 + startInstant.minute
                val endMinutes = endInstant.hour * 60 + endInstant.minute
                intervals.add(Pair(startMinutes.toFloat(), endMinutes.toFloat()))
            }
            processedData[day] = intervals
        }
        processedData
    }

    val yAxisLabelWidth = 30.dp
    val rightPadding = 20.dp
    val headerHeight = 30.dp
    val bottomPadding = 30.dp
    val dayHeight = 48.dp

    if (timelineItems == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            SleepChartHeader(yAxisLabelWidth, rightPadding, headerHeight)
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                SleepChartBody(
                    sleepData = sleepData,
                    month = month,
                    yAxisLabelWidth = yAxisLabelWidth,
                    rightPadding = rightPadding,
                    bottomPadding = bottomPadding,
                    dayHeight = dayHeight
                )
            }
        }
    }
}

@Composable
private fun SleepChartHeader(yAxisLabelWidth: Dp, rightPadding: Dp, headerHeight: Dp) {
    val textColor = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = Modifier
        .fillMaxWidth()
        .height(headerHeight)
        .padding(horizontal = 8.dp)
    ) { 
        val canvasWidth = size.width
        val graphWidth = canvasWidth - yAxisLabelWidth.toPx() - rightPadding.toPx()

        for (i in 0..24 step 3) {
            val x = yAxisLabelWidth.toPx() + (i.toFloat() / 24f) * graphWidth
            drawContext.canvas.nativeCanvas.drawText(
                "$i:00",
                x,
                headerHeight.toPx() - 10f,
                android.graphics.Paint().apply {
                    textSize = 30f
                    textAlign = android.graphics.Paint.Align.CENTER
                    color = textColor.toArgb()
                }
            )
        }
    }
}

@Composable
private fun SleepChartBody(
    sleepData: Map<Int, List<Pair<Float, Float>>>,
    month: LocalDate,
    yAxisLabelWidth: Dp,
    rightPadding: Dp,
    bottomPadding: Dp,
    dayHeight: Dp
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val primaryColor = MaterialTheme.colorScheme.primary
    val daysInMonth = month.lengthOfMonth()
    val totalCanvasHeight = (dayHeight * daysInMonth) + bottomPadding

    Canvas(modifier = Modifier
        .fillMaxWidth()
        .height(totalCanvasHeight)
        .padding(8.dp)) { 
        val canvasWidth = size.width
        val graphWidth = canvasWidth - yAxisLabelWidth.toPx() - rightPadding.toPx()
        val dayHeightPx = dayHeight.toPx()

        // Draw grid lines
        // Horizontal lines
        for (i in 1..daysInMonth) {
            val y = i * dayHeightPx
            drawLine(
                color = Color.LightGray,
                start = Offset(yAxisLabelWidth.toPx(), y),
                end = Offset(yAxisLabelWidth.toPx() + graphWidth, y),
                strokeWidth = 1f
            )
        }
        // Vertical lines
        for (i in 0..24 step 3) {
            val x = yAxisLabelWidth.toPx() + (i.toFloat() / 24f) * graphWidth
            drawLine(
                color = Color.LightGray,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1f
            )
        }

        // Y-Axis labels (days)
        for (i in 1..daysInMonth) {
            drawContext.canvas.nativeCanvas.drawText(
                "$i",
                0f,
                i * dayHeightPx,
                android.graphics.Paint().apply {
                    textSize = 30f
                    color = textColor.toArgb()
                }
            )
        }

        // Draw sleep data
        sleepData.forEach { (day, intervals) ->
            intervals.forEach { (startMinutes, endMinutes) ->
                val startX = yAxisLabelWidth.toPx() + (startMinutes / (24 * 60)) * graphWidth
                val endX = yAxisLabelWidth.toPx() + (endMinutes / (24 * 60)) * graphWidth
                val y = day * dayHeightPx

                // Draw line
                drawLine(
                    color = primaryColor,
                    start = Offset(startX, y),
                    end = Offset(endX, y),
                    strokeWidth = 72f
                )
            }
        }
    }
}
