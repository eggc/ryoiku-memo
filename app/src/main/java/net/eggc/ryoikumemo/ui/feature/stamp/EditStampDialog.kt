package net.eggc.ryoikumemo.ui.feature.stamp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.eggc.ryoikumemo.data.Note
import net.eggc.ryoikumemo.data.NoteRepository
import net.eggc.ryoikumemo.data.StampType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditStampDialog(
    stampType: StampType,
    initialTimestamp: Long = System.currentTimeMillis(),
    initialNote: String = "",
    noteRepository: NoteRepository,
    note: Note,
    onDismiss: () -> Unit,
    onConfirm: (Long, String) -> Unit
) {
    var timestamp by remember { mutableStateOf(initialTimestamp) }
    var memoText by remember { mutableStateOf(initialNote) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var suggestionsExpanded by remember { mutableStateOf(false) }

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
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) {
                        Text(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM/dd")))
                    }
                    Button(onClick = { showTimePicker = true }, modifier = Modifier.weight(1f)) {
                        Text(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")))
                    }
                }

                TextField(
                    value = memoText,
                    onValueChange = { memoText = it },
                    label = { Text("詳細 (任意)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )

                // アコーディオン方式のサジェスト
                if (suggestions.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { suggestionsExpanded = !suggestionsExpanded }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "過去の履歴から選択",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = if (suggestionsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        if (suggestionsExpanded) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                suggestions.forEach { suggestion ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                memoText = suggestion
                                                suggestionsExpanded = false
                                            },
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text(
                                            text = suggestion,
                                            modifier = Modifier.padding(12.dp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
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
