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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.eggc.ryoikumemo.data.StampItem
import net.eggc.ryoikumemo.data.TimelineRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditStampScreen(
    modifier: Modifier = Modifier,
    stampId: Long,
    timelineRepository: TimelineRepository,
    noteId: String,
    onStampUpdated: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var stampItem by remember { mutableStateOf<StampItem?>(null) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    var currentTimestamp by remember { mutableStateOf(stampId) }

    LaunchedEffect(stampId, noteId) {
        stampItem = timelineRepository.getTimelineItems(noteId).find { it.timestamp == stampId } as? StampItem
        suggestions = timelineRepository.getTimelineItems(noteId)
            .filterIsInstance<StampItem>()
            .map { it.note }
            .filter { it.isNotBlank() }
            .distinct()
            .take(10)
    }

    if (stampItem == null) {
        Text("エラー: スタンプが見つかりません", modifier = modifier.padding(16.dp))
        return
    }

    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = currentTimestamp)
    val timePickerState = rememberTimePickerState(
        initialHour = Instant.ofEpochMilli(currentTimestamp).atZone(ZoneId.systemDefault()).hour,
        initialMinute = Instant.ofEpochMilli(currentTimestamp).atZone(ZoneId.systemDefault()).minute,
        is24Hour = true
    )

    var note by remember(stampItem?.note) { mutableStateOf(stampItem?.note ?: "") }

    var expanded by remember { mutableStateOf(false) }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val selectedDate = Calendar.getInstance().apply {
                        timeInMillis = datePickerState.selectedDateMillis!!
                    }
                    val currentCalendar = Calendar.getInstance().apply {
                        timeInMillis = currentTimestamp
                    }
                    currentCalendar.set(selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH), selectedDate.get(Calendar.DAY_OF_MONTH))
                    currentTimestamp = currentCalendar.timeInMillis
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
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(text = "時刻の選択") },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val currentCalendar = Calendar.getInstance().apply {
                            timeInMillis = currentTimestamp
                        }
                        currentCalendar.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                        currentCalendar.set(Calendar.MINUTE, timePickerState.minute)
                        currentTimestamp = currentCalendar.timeInMillis
                        showTimePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showTimePicker = false }
                ) {
                    Text("キャンセル")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("スタンプの編集", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(stampItem!!.type.icon, contentDescription = null, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(stampItem!!.type.label, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) {
                Text(Instant.ofEpochMilli(currentTimestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy/MM/dd")))
            }
            Button(onClick = { showTimePicker = true }, modifier = Modifier.weight(1f)) {
                Text(Instant.ofEpochMilli(currentTimestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")))
            }
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
            coroutineScope.launch {
                timelineRepository.deleteTimelineItem(noteId, stampItem!!)
                timelineRepository.saveStamp(noteId, stampItem!!.type, note, currentTimestamp)
                Toast.makeText(context, "スタンプを更新しました", Toast.LENGTH_SHORT).show()
                onStampUpdated()
            }
        }) {
            Text("保存")
        }
    }
}
