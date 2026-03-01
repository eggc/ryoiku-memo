package net.eggc.ryoikumemo.ui.feature.review

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.eggc.ryoikumemo.data.Note
import net.eggc.ryoikumemo.data.TimelineRepository
import net.eggc.ryoikumemo.ui.components.MonthSelector
import net.eggc.ryoikumemo.ui.feature.graph.GraphMonthPage
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val BASE_MONTH = LocalDate.of(2020, 1, 1)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReviewScreen(
    modifier: Modifier = Modifier,
    timelineRepository: TimelineRepository,
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

    Column(modifier = modifier.fillMaxSize()) {
        MonthSelector(currentMonth = currentMonth, onMonthChange = onMonthChange)

        Text(
            text = "睡眠グラフ",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val month = BASE_MONTH.plusMonths(page.toLong())
            GraphMonthPage(
                timelineRepository = timelineRepository,
                note = note,
                month = month
            )
        }
    }
}
