package com.example.ryoiku_memo

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import com.example.ryoiku_memo.ui.theme.RyoikumemoTheme
import java.text.SimpleDateFormat
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

@OptIn(ExperimentalMaterial3Api::class)
@PreviewScreenSizes
@Composable
fun RyoikumemoApp() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    var editingMemoId by rememberSaveable { mutableStateOf<Long?>(null) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            it.icon,
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = {
                        currentDestination = it
                        if (it == AppDestinations.ADD_MEMO) {
                            editingMemoId = null
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
                AppDestinations.HOME -> HomeScreen(
                    modifier = Modifier.padding(innerPadding),
                    onEditClick = { memoId ->
                        editingMemoId = memoId
                        currentDestination = AppDestinations.ADD_MEMO
                    }
                )

                AppDestinations.ADD_MEMO -> AddMemoScreen(
                    modifier = Modifier.padding(innerPadding),
                    memoId = editingMemoId,
                    onMemoSaved = {
                        currentDestination = AppDestinations.HOME
                        editingMemoId = null
                    }
                )
                AppDestinations.STAMP -> StampScreen(modifier = Modifier.padding(innerPadding))
                AppDestinations.SETTINGS -> SettingsScreen(modifier = Modifier.padding(innerPadding))
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Home", Icons.Default.Home),
    ADD_MEMO("メモ作成", Icons.Default.Add),
    STAMP("スタンプ", Icons.Default.AccessTime),
    SETTINGS("設定", Icons.Default.Settings),
}

@Composable
fun HomeScreen(modifier: Modifier = Modifier, onEditClick: (Long) -> Unit) {
    val context = LocalContext.current
    val sharedPref = context.getSharedPreferences("memo_prefs", Context.MODE_PRIVATE)
    var memos by remember { mutableStateOf(getMemos(sharedPref)) }
    var showDeleteDialogFor by remember { mutableStateOf<Long?>(null) }

    if (showDeleteDialogFor != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialogFor = null },
            title = { Text("メモの削除") },
            text = { Text("このメモを削除しますか？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        with(sharedPref.edit()) {
                            remove(showDeleteDialogFor.toString())
                            apply()
                        }
                        memos = getMemos(sharedPref) // Refresh the list
                        showDeleteDialogFor = null
                        Toast.makeText(context, "メモを削除しました", Toast.LENGTH_SHORT).show()
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

    LazyColumn(modifier = modifier.padding(8.dp)) {
        if (memos.isEmpty()) {
            item {
                Text(
                    text = "まだメモはありません。",
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            items(memos) { (timestamp, memoText) ->
                MemoCard(
                    timestamp = timestamp,
                    text = memoText,
                    onEditClick = { onEditClick(timestamp) },
                    onDeleteClick = { showDeleteDialogFor = timestamp }
                )
            }
        }
    }
}

private fun getMemos(sharedPreferences: android.content.SharedPreferences): List<Pair<Long, String>> {
    return sharedPreferences.all.mapNotNull { (key, value) ->
        try {
            val timestamp = key.toLong()
            val memoText = value as? String
            if (memoText != null) {
                timestamp to memoText
            } else {
                null
            }
        } catch (e: NumberFormatException) {
            null
        }
    }.sortedByDescending { it.first }
}

@Composable
fun MemoCard(timestamp: Long, text: String, onEditClick: () -> Unit, onDeleteClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(timestamp)),
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
fun AddMemoScreen(modifier: Modifier = Modifier, memoId: Long?, onMemoSaved: () -> Unit) {
    val context = LocalContext.current
    val sharedPref = context.getSharedPreferences("memo_prefs", Context.MODE_PRIVATE)

    val initialText = remember(memoId) {
        if (memoId != null) {
            sharedPref.getString(memoId.toString(), "") ?: ""
        } else {
            ""
        }
    }
    var text by rememberSaveable(initialText) { mutableStateOf(initialText) }

    Column(modifier = modifier) {
        TextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .weight(1f),
            label = { Text("メモ内容") }
        )
        Button(
            onClick = {
                if (text.isNotBlank()) {
                    with(sharedPref.edit()) {
                        val key = (memoId ?: System.currentTimeMillis()).toString()
                        putString(key, text)
                        apply()
                    }
                    Toast.makeText(context, "メモを保存しました", Toast.LENGTH_SHORT).show()
                    onMemoSaved()
                } else {
                    Toast.makeText(context, "メモ内容が空です", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Text("確定")
        }
    }
}

@Composable
fun StampScreen(modifier: Modifier = Modifier) {
    Text(
        text = "スタンプ画面です。",
        modifier = modifier.padding(16.dp)
    )
}

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    Text(
        text = "設定画面です。",
        modifier = modifier.padding(16.dp)
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    RyoikumemoTheme {
        HomeScreen(onEditClick = {})
    }
}
