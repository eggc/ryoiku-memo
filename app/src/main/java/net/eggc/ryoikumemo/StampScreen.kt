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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import net.eggc.ryoikumemo.data.StampType
import net.eggc.ryoikumemo.data.TimelineRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StampScreen(
    modifier: Modifier = Modifier,
    timelineRepository: TimelineRepository,
    noteId: String,
    onStampSaved: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val appPreferences = remember { AppPreferences(context) }

    var isCustomizing by remember { mutableStateOf(false) }
    var hiddenStampTypes by remember { mutableStateOf(appPreferences.getHiddenStampTypes()) }

    val visibleStampTypes = StampType.entries.filter { !hiddenStampTypes.contains(it.name) }

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
                        if (!isCustomizing) {
                            coroutineScope.launch {
                                timelineRepository.saveStamp(noteId, stampType, "")
                                Toast.makeText(context, "${stampType.label}を記録しました", Toast.LENGTH_SHORT).show()
                                onStampSaved()
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
