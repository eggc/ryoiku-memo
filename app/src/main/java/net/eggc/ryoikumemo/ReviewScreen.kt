package net.eggc.ryoikumemo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.eggc.ryoikumemo.data.Note
import net.eggc.ryoikumemo.data.NoteRepository
import net.eggc.ryoikumemo.data.StampItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private val BASE_MONTH = LocalDate.of(2020, 1, 1)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReviewScreen(
    modifier: Modifier = Modifier,
    noteRepository: NoteRepository,
    note: Note,
    currentMonth: LocalDate,
    onMonthChange: (LocalDate) -> Unit
) {
    var selectedIndex by remember { mutableStateOf(0) }
    val options = listOf("日記", "睡眠グラフ")

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

    Column(modifier = modifier.fillMaxSize()) {
        MonthSelector(currentMonth = currentMonth, onMonthChange = onMonthChange)

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            options.forEachIndexed { index, label ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    onClick = { selectedIndex = index },
                    selected = index == selectedIndex
                ) {
                    Text(label)
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val month = BASE_MONTH.plusMonths(page.toLong())
            if (selectedIndex == 0) {
                DiaryView(
                    noteRepository = noteRepository,
                    note = note,
                    month = month
                )
            } else {
                GraphMonthPage(
                    noteRepository = noteRepository,
                    note = note,
                    month = month
                )
            }
        }
    }
}

@Composable
fun DiaryView(
    noteRepository: NoteRepository,
    note: Note,
    month: LocalDate
) {
    var diaryData by remember { mutableStateOf<Map<Int, List<StampItem>>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(note.id, month) {
        isLoading = true
        val items = noteRepository.getTimelineItemsForMonth(note.ownerId, note.id, note.sharedId, month)
        val grouped = items.filterIsInstance<StampItem>()
            .filter { it.note.isNotBlank() }
            .sortedBy { it.timestamp }
            .groupBy {
                Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate().dayOfMonth
            }
        diaryData = grouped
        isLoading = false
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (diaryData.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "この月の日記はありません。",
                modifier = Modifier.padding(16.dp)
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            val daysInMonth = month.lengthOfMonth()
            items((1..daysInMonth).toList()) { day ->
                val items = diaryData[day] ?: emptyList()
                if (items.isNotEmpty()) {
                    DiaryRow(day = day, items = items)
                }
            }
        }
    }
}

@Composable
fun DiaryRow(day: Int, items: List<StampItem>) {
    val fontSize = 16.sp
    val lineHeight = 24.sp // 16sp * 1.5

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        // Note lines background
        val lineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        Canvas(modifier = Modifier.matchParentSize()) {
            val lineHeightPx = lineHeight.toPx()
            var y = lineHeightPx
            while (y <= size.height + 1f) {
                drawLine(
                    color = lineColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.5f
                )
                y += lineHeightPx
            }
        }

        val annotatedString = buildAnnotatedString {
            // Day prefix (styled as a character)
            withStyle(SpanStyle(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )) {
                append(day.toString())
            }
            withStyle(SpanStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )) {
                append("日")
            }
            append("\n") // 改行を入れて日にちだけの行にする

            items.forEachIndexed { index, item ->
                if (index > 0) append("\n\n")
                appendInlineContent("icon_$index", "[icon]")
                append(" ")
                append(item.note)
            }
        }

        val inlineContent = items.mapIndexed { index, item ->
            "icon_$index" to InlineTextContent(
                Placeholder(width = fontSize, height = fontSize, placeholderVerticalAlign = PlaceholderVerticalAlign.Center)
            ) {
                Icon(
                    imageVector = item.type.icon,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }.toMap()

        Text(
            text = annotatedString,
            inlineContent = inlineContent,
            style = TextStyle(
                fontSize = fontSize,
                lineHeight = lineHeight,
                color = MaterialTheme.colorScheme.onSurface,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.None
                )
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
