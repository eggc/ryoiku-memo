package net.eggc.ryoikumemo.ui.feature.timeline

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import net.eggc.ryoikumemo.data.AppPreferences
import net.eggc.ryoikumemo.data.Note
import net.eggc.ryoikumemo.data.StampType
import net.eggc.ryoikumemo.data.TimelineRepository
import net.eggc.ryoikumemo.ui.components.MonthSelector
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private val BASE_MONTH = LocalDate.of(2020, 1, 1)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    modifier: Modifier = Modifier,
    timelineRepository: TimelineRepository,
    note: Note,
    currentMonth: LocalDate,
    onMonthChange: (LocalDate) -> Unit,
    onEditStampClick: (Long) -> Unit,
    onAddStampClick: () -> Unit
) {
    val initialPage = remember { ChronoUnit.MONTHS.between(BASE_MONTH, currentMonth.withDayOfMonth(1)).toInt() }
    val pagerState = rememberPagerState(initialPage = initialPage) { 1200 } // 100 years
    var showJumpDatePicker by remember { mutableStateOf(false) }
    var targetDate by remember { mutableStateOf<LocalDate?>(null) }

    val context = LocalContext.current
    val appPreferences = remember { AppPreferences(context) }
    val hiddenStampTypes = remember { appPreferences.getHiddenStampTypes() }

    var selectedFilters by remember { mutableStateOf(setOf<StampType>()) }

    LaunchedEffect(pagerState.currentPage) {
        val month = BASE_MONTH.plusMonths(pagerState.currentPage.toLong())
        if (!month.isEqual(currentMonth.withDayOfMonth(1))) {
            onMonthChange(month)
        }
    }

    LaunchedEffect(currentMonth) {
        val page = ChronoUnit.MONTHS.between(BASE_MONTH, currentMonth.withDayOfMonth(1)).toInt()
        if (pagerState.currentPage != page) {
            pagerState.animateScrollToPage(page)
        }
    }

    if (showJumpDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = currentMonth.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showJumpDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                        targetDate = selectedDate
                        onMonthChange(selectedDate)
                        showJumpDatePicker = false
                    }
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDatePicker = false }) {
                    Text("キャンセル")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddStampClick,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("新規追加") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        },
        floatingActionButtonPosition = FabPosition.End
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            MonthSelector(
                currentMonth = currentMonth,
                onMonthChange = onMonthChange,
                onMonthClick = { showJumpDatePicker = true }
            )

            TimelineFilterBar(
                selectedFilters = selectedFilters,
                hiddenStampTypes = hiddenStampTypes,
                onFilterChange = { selectedFilters = it }
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val month = BASE_MONTH.plusMonths(page.toLong())
                TimelineMonthPage(
                    timelineRepository = timelineRepository,
                    note = note,
                    month = month,
                    selectedFilters = selectedFilters,
                    targetDate = if (targetDate?.year == month.year && targetDate?.month == month.month) targetDate else null,
                    onTargetDateScrolled = { targetDate = null },
                    onEditStampClick = { onEditStampClick(it) },
                    onDateClick = { showJumpDatePicker = true }
                )
            }
        }
    }
}

@Composable
fun TimelineFilterBar(
    selectedFilters: Set<StampType>,
    hiddenStampTypes: Set<String>,
    onFilterChange: (Set<StampType>) -> Unit
) {
    val filterOptions = StampType.entries.filter { !hiddenStampTypes.contains(it.name) }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(filterOptions) { type ->
            val isSelected = selectedFilters.contains(type)
            Surface(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable {
                        val newFilters = if (isSelected) {
                            selectedFilters - type
                        } else {
                            selectedFilters + type
                        }
                        onFilterChange(newFilters)
                    },
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 2.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = type.icon,
                        contentDescription = type.label,
                        modifier = Modifier.size(24.dp),
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
