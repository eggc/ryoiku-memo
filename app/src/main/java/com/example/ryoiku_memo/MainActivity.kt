package net.eggc.ryoikumemo

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.SentimentVeryDissatisfied
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.auth
import com.google.firebase.Firebase
import net.eggc.ryoikumemo.ui.theme.RyoikumemoTheme
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RyoikumemoTheme {
                RyoikumemoApp()
            }
        }
    }
}

// タイムラインの項目を表す共通のデータ構造
sealed interface TimelineItem {
    val timestamp: Long
}

data class DiaryItem(
    override val timestamp: Long,
    val text: String,
    val date: String // "yyyy-MM-dd"
) : TimelineItem

data class StampItem(
    override val timestamp: Long,
    val type: StampType,
    val note: String
) : TimelineItem

sealed interface TimelineFilter {
    data object All : TimelineFilter
    data object DiaryOnly : TimelineFilter
    data class StampOnly(val type: StampType) : TimelineFilter
}


@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreenSizes
@Composable
fun RyoikumemoApp() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.TIMELINE) }
    var editingDiaryDate by rememberSaveable { mutableStateOf<String?>(null) }
    var editingStampId by rememberSaveable { mutableStateOf<Long?>(null) }
    val context = LocalContext.current

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.filter { it.icon != null }.forEach { dest ->
                item(
                    icon = {
                        Icon(
                            dest.icon!!,
                            contentDescription = dest.label
                        )
                    },
                    label = { Text(dest.label) },
                    selected = dest == currentDestination,
                    onClick = {
                        currentDestination = dest
                        editingStampId = null
                        if (dest == AppDestinations.DIARY) {
                            editingDiaryDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                        }
                    }
                )
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(title = { Text("療育メモ") })
            }
        ) { innerPadding ->
            when (currentDestination) {
                AppDestinations.TIMELINE -> TimelineScreen(
                    modifier = Modifier.padding(innerPadding),
                    onEditDiaryClick = { date ->
                        editingDiaryDate = date
                        currentDestination = AppDestinations.DIARY
                    },
                    onEditStampClick = { stampId ->
                        editingStampId = stampId
                        currentDestination = AppDestinations.EDIT_STAMP
                    }
                )

                AppDestinations.DIARY -> DiaryScreen(
                    modifier = Modifier.padding(innerPadding),
                    date = editingDiaryDate!!,
                    onDiarySaved = {
                        currentDestination = AppDestinations.TIMELINE
                        editingDiaryDate = null
                    }
                )

                AppDestinations.STAMP -> StampScreen(
                    modifier = Modifier.padding(innerPadding),
                    onStampSaved = { currentDestination = AppDestinations.TIMELINE }
                )

                AppDestinations.EDIT_STAMP -> EditStampScreen(
                    modifier = Modifier.padding(innerPadding),
                    stampId = editingStampId!!,
                    onStampUpdated = {
                        currentDestination = AppDestinations.TIMELINE
                        editingStampId = null
                    }
                )

                AppDestinations.SETTINGS -> SettingsScreen(
                    modifier = Modifier.padding(innerPadding),
                    onLogoutClick = {
                        Firebase.auth.signOut()
                        val intent = Intent(context, AuthActivity::class.java)
                        (context as? Activity)?.startActivity(intent)
                        (context as? Activity)?.finish()
                    }
                )
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector?,
) {
    TIMELINE("タイムライン", Icons.Default.Timeline),
    DIARY("日記", Icons.Default.Book),
    STAMP("スタンプ", Icons.Default.AccessTime),
    SETTINGS("設定", Icons.Default.Settings),
    EDIT_STAMP("スタンプ編集", null)
}

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditStampScreen(modifier: Modifier = Modifier, stampId: Long, onStampUpdated: () -> Unit) {
    val context = LocalContext.current
    val sharedPref = context.getSharedPreferences("stamp_prefs", Context.MODE_PRIVATE)
    val stampValueString = sharedPref.getString(stampId.toString(), null)

    if (stampValueString == null) {
        Text("エラー: スタンプが見つかりません", modifier = modifier.padding(16.dp))
        return
    }

    val parts = stampValueString.split('|', limit = 2)
    val stampType = StampType.valueOf(parts[0])
    val initialNote = if (parts.size > 1) parts[1] else ""

    val initialCalendar = Calendar.getInstance().apply { timeInMillis = stampId }

    var year by rememberSaveable { mutableStateOf(initialCalendar.get(Calendar.YEAR).toString()) }
    var month by rememberSaveable { mutableStateOf((initialCalendar.get(Calendar.MONTH) + 1).toString()) }
    var day by rememberSaveable { mutableStateOf(initialCalendar.get(Calendar.DAY_OF_MONTH).toString()) }
    var hour by rememberSaveable { mutableStateOf(initialCalendar.get(Calendar.HOUR_OF_DAY).toString()) }
    var minute by rememberSaveable { mutableStateOf(initialCalendar.get(Calendar.MINUTE).toString()) }
    var note by rememberSaveable { mutableStateOf(initialNote) }

    fun getNoteSuggestions(): List<String> {
        return sharedPref.all.values
            .mapNotNull { it as? String }
            .map { it.split('|', limit = 2) }
            .filter { it.size > 1 && it[1].isNotBlank() }
            .map { it[1] }
            .distinct()
            .take(10)
    }

    val suggestions = remember { getNoteSuggestions() }
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("スタンプの編集", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(stampType.icon, contentDescription = null, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(stampType.label, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(modifier = Modifier.height(24.dp))

        // Date Fields
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextField(value = year, onValueChange = { year = it }, label = { Text("年") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Spacer(modifier = Modifier.width(8.dp))
            TextField(value = month, onValueChange = { month = it }, label = { Text("月") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Spacer(modifier = Modifier.width(8.dp))
            TextField(value = day, onValueChange = { day = it }, label = { Text("日") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Time Fields
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextField(value = hour, onValueChange = { hour = it }, label = { Text("時") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Spacer(modifier = Modifier.width(8.dp))
            TextField(value = minute, onValueChange = { minute = it }, label = { Text("分") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }

        Spacer(modifier = Modifier.height(16.dp))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            TextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("一言メモ") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable)
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                suggestions.forEach { selectionOption ->
                    DropdownMenuItem(
                        text = { Text(selectionOption) },
                        onClick = {
                            note = selectionOption
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(onClick = {
            val newCalendar = Calendar.getInstance()
            try {
                newCalendar.set(
                    year.toInt(),
                    month.toInt() - 1, // Calendar.MONTH is 0-indexed
                    day.toInt(),
                    hour.toInt(),
                    minute.toInt()
                )
                val newTimestamp = newCalendar.timeInMillis
                val newStampValue = "${stampType.name}|${note}"

                with(sharedPref.edit()) {
                    remove(stampId.toString())
                    putString(newTimestamp.toString(), newStampValue)
                    apply()
                }
                Toast.makeText(context, "スタンプを更新しました", Toast.LENGTH_SHORT).show()
                onStampUpdated()

            } catch (e: Exception) {
                Toast.makeText(context, "日時の入力に誤りがあります", Toast.LENGTH_SHORT).show()
            }
        }) {
            Text("保存")
        }
    }
}

@Composable
fun DiaryScreen(modifier: Modifier = Modifier, date: String, onDiarySaved: () -> Unit) {
    val context = LocalContext.current
    val sharedPref = context.getSharedPreferences("diary_prefs", Context.MODE_PRIVATE)

    val initialDate = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd"))

    var year by rememberSaveable { mutableStateOf(initialDate.year.toString()) }
    var month by rememberSaveable { mutableStateOf(initialDate.monthValue.toString()) }
    var day by rememberSaveable { mutableStateOf(initialDate.dayOfMonth.toString()) }

    val initialText = remember(date) { sharedPref.getString(date, "") ?: "" }
    var text by rememberSaveable(initialText) { mutableStateOf(initialText) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("日記の編集", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextField(value = year, onValueChange = { year = it }, label = { Text("年") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Spacer(modifier = Modifier.width(8.dp))
            TextField(value = month, onValueChange = { month = it }, label = { Text("月") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Spacer(modifier = Modifier.width(8.dp))
            TextField(value = day, onValueChange = { day = it }, label = { Text("日") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            label = { Text("内容") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                try {
                    val newDateStr = LocalDate.of(year.toInt(), month.toInt(), day.toInt()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

                    if (newDateStr != date && sharedPref.contains(newDateStr)) {
                        Toast.makeText(context, "その日付の日記はすでに存在します", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    with(sharedPref.edit()) {
                        if (newDateStr != date) {
                            remove(date)
                        }
                        putString(newDateStr, text)
                        apply()
                    }
                    Toast.makeText(context, "日記を保存しました", Toast.LENGTH_SHORT).show()
                    onDiarySaved()
                } catch (e: Exception) {
                    Toast.makeText(context, "日付の入力に誤りがあります", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("保存")
        }
    }
}


enum class StampType(val label: String, val icon: ImageVector) {
    SLEEP("ねる", Icons.Default.Bedtime),
    WAKE_UP("おきる", Icons.Default.WbSunny),
    TANTRUM("かんしゃく", Icons.Default.SentimentVeryDissatisfied),
    MEDICATION("おくすり", Icons.Default.Medication),
    FUN("たのしい", Icons.Default.SentimentSatisfied)
}

@Composable
fun StampScreen(modifier: Modifier = Modifier, onStampSaved: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StampType.entries.forEach { stampType ->
            StampCard(
                label = stampType.label,
                icon = stampType.icon,
                onClick = {
                    val sharedPref = context.getSharedPreferences("stamp_prefs", Context.MODE_PRIVATE)
                    with(sharedPref.edit()) {
                        // Persist with an empty note field for consistency
                        putString(System.currentTimeMillis().toString(), "${stampType.name}|")
                        apply()
                    }
                    Toast.makeText(context, "${stampType.label}を記録しました", Toast.LENGTH_SHORT).show()
                    onStampSaved()
                }
            )
        }
    }
}

@Composable
fun StampCard(label: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = label, style = MaterialTheme.typography.titleLarge)
        }
    }
}


@Composable
fun SettingsScreen(modifier: Modifier = Modifier, onLogoutClick: () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = onLogoutClick) {
            Text("ログアウト")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TimelineScreenPreview() {
    RyoikumemoTheme {
        TimelineScreen(onEditDiaryClick = {}, onEditStampClick = {})
    }
}
