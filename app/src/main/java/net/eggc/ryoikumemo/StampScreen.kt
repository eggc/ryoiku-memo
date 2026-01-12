package net.eggc.ryoikumemo

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
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
import net.eggc.ryoikumemo.data.AppPreferences
import net.eggc.ryoikumemo.data.Note
import net.eggc.ryoikumemo.data.NoteRepository
import net.eggc.ryoikumemo.data.StampType

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

    var isCustomizing by remember { mutableStateOf(false) }
    var hiddenStampTypes by remember { mutableStateOf(appPreferences.getHiddenStampTypes()) }
    var showMemoDialog by remember { mutableStateOf<StampType?>(null) }
    var memoText by remember { mutableStateOf("") }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }

    val visibleStampTypes = StampType.entries.filter { !hiddenStampTypes.contains(it.name) }

    if (showMemoDialog != null && note != null) {
        val stampType = showMemoDialog!!
        var expanded by remember { mutableStateOf(false) }

        // Fetch suggestions only for types that need them (like OUTING)
        LaunchedEffect(stampType) {
            if (stampType == StampType.OUTING) {
                suggestions = noteRepository.getStampNoteSuggestions(note.ownerId, note.id, stampType)
            } else {
                suggestions = emptyList()
            }
        }

        AlertDialog(
            onDismissRequest = { 
                showMemoDialog = null
                memoText = ""
            },
            title = { Text("${stampType.label}の内容を入力") },
            text = {
                Column {
                    if (stampType == StampType.OUTING) {
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextField(
                                value = memoText,
                                onValueChange = { if (it.length <= 2048) memoText = it },
                                label = { Text("場所・詳細") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable),
                                singleLine = true
                            )
                            if (suggestions.isNotEmpty()) {
                                ExposedDropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false },
                                ) {
                                    suggestions.forEach { selectionOption ->
                                        DropdownMenuItem(
                                            text = { Text(selectionOption) },
                                            onClick = {
                                                memoText = selectionOption
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Regular multi-line field for MEMO
                        TextField(
                            value = memoText,
                            onValueChange = { if (it.length <= 2048) memoText = it },
                            label = { Text("詳細") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = false,
                            minLines = 3
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            noteRepository.saveStamp(note.ownerId, note.id, stampType, memoText)
                            Toast.makeText(context, "${stampType.label}を記録しました", Toast.LENGTH_SHORT).show()
                            showMemoDialog = null
                            memoText = ""
                            onStampSaved()
                        }
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showMemoDialog = null
                        memoText = ""
                    }
                ) {
                    Text("キャンセル")
                }
            }
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
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
                            if (stampType == StampType.MEMO || stampType == StampType.OUTING) {
                                showMemoDialog = stampType
                            } else {
                                coroutineScope.launch {
                                    noteRepository.saveStamp(note.ownerId, note.id, stampType, "")
                                    Toast.makeText(context, "${stampType.label}を記録しました", Toast.LENGTH_SHORT).show()
                                    onStampSaved()
                                }
                            }
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
    }
}
