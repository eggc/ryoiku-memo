package net.eggc.ryoikumemo

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.eggc.ryoikumemo.data.AppPreferences
import net.eggc.ryoikumemo.data.Note
import net.eggc.ryoikumemo.data.NoteRepository
import net.eggc.ryoikumemo.data.StampType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StampScreen(
    modifier: Modifier = Modifier,
    noteRepository: NoteRepository,
    note: Note?,
    onStampSaved: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val appPreferences = remember { AppPreferences(context) }

    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("スタンプ", "タスク")

    var isCustomizing by remember { mutableStateOf(false) }
    var hiddenStampTypes by remember { mutableStateOf(appPreferences.getHiddenStampTypes()) }
    var selectedStampType by remember { mutableStateOf<StampType?>(null) }

    val visibleStampTypes = StampType.entries.filter { !hiddenStampTypes.contains(it.name) }

    // 記録用ダイアログ
    if (selectedStampType != null && note != null) {
        RecordDetailsDialog(
            stampType = selectedStampType!!,
            noteRepository = noteRepository,
            note = note,
            onDismiss = { selectedStampType = null },
            onConfirm = { timestamp, noteText ->
                coroutineScope.launch {
                    noteRepository.saveStamp(note.ownerId, note.id, selectedStampType!!, noteText, timestamp)
                    Toast.makeText(context, "${selectedStampType!!.label}を記録しました", Toast.LENGTH_SHORT).show()
                    selectedStampType = null
                    onStampSaved()
                }
            }
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            tabs.forEachIndexed { index, label ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                    onClick = { selectedTabIndex = index },
                    selected = index == selectedTabIndex
                ) {
                    Text(label)
                }
            }
        }

        if (selectedTabIndex == 0) {
            // スタンプ画面
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                if (isCustomizing) {
                    TextButton(onClick = {
                        appPreferences.saveHiddenStampTypes(hiddenStampTypes)
                        isCustomizing = false
                    }) {
                        Text("完了")
                    }
                } else {
                    TextButton(onClick = { isCustomizing = true }) {
                        Text("カスタマイズ")
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 128.dp),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val itemsToShow = if (isCustomizing) StampType.entries else visibleStampTypes
                items(itemsToShow) { stampType ->
                    Card(
                        modifier = Modifier.padding(8.dp),
                        onClick = {
                            if (!isCustomizing && note != null) {
                                selectedStampType = stampType
                            }
                        }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        ) {
                            Icon(stampType.icon, contentDescription = stampType.label)
                            Text(stampType.label)
                            if (isCustomizing) {
                                Switch(
                                    checked = !hiddenStampTypes.contains(stampType.name),
                                    onCheckedChange = { isChecked ->
                                        hiddenStampTypes = if (isChecked) {
                                            hiddenStampTypes - stampType.name
                                        } else {
                                            hiddenStampTypes + stampType.name
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        } else if (note != null) {
            // タスク画面
            TaskScreen(
                noteRepository = noteRepository,
                note = note,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDetailsDialog(
    stampType: StampType,
    noteRepository: NoteRepository,
    note: Note,
    onDismiss: () -> Unit,
    onConfirm: (Long, String) -> Unit
) {
    var timestamp by remember { mutableStateOf(System.currentTimeMillis()) }
    var memoText by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(stampType) {
        if (stampType != StampType.MEMO) {
            suggestions = noteRepository.getStampNoteSuggestions(note.ownerId, note.id, stampType).take(5)
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = timestamp)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val selectedDate = Calendar.getInstance().apply {
                        timeInMillis = datePickerState.selectedDateMillis!!
                    }
                    val currentCalendar = Calendar.getInstance().apply {
                        timeInMillis = timestamp
                    }
                    currentCalendar.set(selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH), selectedDate.get(Calendar.DAY_OF_MONTH))
                    timestamp = currentCalendar.timeInMillis
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("キャンセル")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).hour,
            initialMinute = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("時刻を選択") },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val currentCalendar = Calendar.getInstance().apply {
                        timeInMillis = timestamp
                    }
                    currentCalendar.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                    currentCalendar.set(Calendar.MINUTE, timePickerState.minute)
                    timestamp = currentCalendar.timeInMillis
                    showTimePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("キャンセル")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(stampType.icon, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("${stampType.label}を記録")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) {
                        Text(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM/dd")))
                    }
                    Button(onClick = { showTimePicker = true }, modifier = Modifier.weight(1f)) {
                        Text(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")))
                    }
                }

                if (stampType == StampType.MEMO) {
                    TextField(
                        value = memoText,
                        onValueChange = { memoText = it },
                        label = { Text("詳細") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 10
                    )
                } else if (suggestions.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextField(
                            value = memoText,
                            onValueChange = { memoText = it },
                            label = { Text("詳細 (過去の履歴から選択)") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            suggestions.forEach { selectionOption ->
                                DropdownMenuItem(
                                    text = { Text(selectionOption, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    onClick = {
                                        memoText = selectionOption
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                } else {
                    TextField(
                        value = memoText,
                        onValueChange = { memoText = it },
                        label = { Text("詳細 (任意)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 6
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(timestamp, memoText) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}
