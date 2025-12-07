package net.eggc.ryoikumemo

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.eggc.ryoikumemo.data.StampItem
import net.eggc.ryoikumemo.data.TimelineRepository
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

    val initialCalendar = Calendar.getInstance().apply { timeInMillis = stampId }

    var year by rememberSaveable { mutableStateOf(initialCalendar.get(Calendar.YEAR).toString()) }
    var month by rememberSaveable { mutableStateOf((initialCalendar.get(Calendar.MONTH) + 1).toString()) }
    var day by rememberSaveable { mutableStateOf(initialCalendar.get(Calendar.DAY_OF_MONTH).toString()) }
    var hour by rememberSaveable { mutableStateOf(initialCalendar.get(Calendar.HOUR_OF_DAY).toString()) }
    var minute by rememberSaveable { mutableStateOf(initialCalendar.get(Calendar.MINUTE).toString()) }
    var note by rememberSaveable(stampItem?.note) { mutableStateOf(stampItem?.note ?: "") }

    var expanded by remember { mutableStateOf(false) }

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
            coroutineScope.launch {
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

                    timelineRepository.deleteTimelineItem(noteId, stampItem!!)
                    timelineRepository.saveStamp(noteId, stampItem!!.type, note, newTimestamp)
                    Toast.makeText(context, "スタンプを更新しました", Toast.LENGTH_SHORT).show()
                    onStampUpdated()

                } catch (e: Exception) {
                    Toast.makeText(context, "日時の入力に誤りがあります", Toast.LENGTH_SHORT).show()
                }
            }
        }) {
            Text("保存")
        }
    }
}
