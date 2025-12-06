package com.example.ryoiku_memo

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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

@PreviewScreenSizes
@Composable
fun RyoikumemoApp() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }

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
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            when (currentDestination) {
                AppDestinations.HOME -> HomeScreen(
                    modifier = Modifier.padding(innerPadding)
                )

                AppDestinations.ADD_MEMO -> AddMemoScreen(modifier = Modifier.padding(innerPadding)) {
                    currentDestination = AppDestinations.HOME
                }

                AppDestinations.PROFILE -> ProfileScreen(modifier = Modifier.padding(innerPadding))
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
    PROFILE("Profile", Icons.Default.AccountBox),
}

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sharedPref = context.getSharedPreferences("memo_prefs", Context.MODE_PRIVATE)

    val memos = sharedPref.all.mapNotNull { (key, value) ->
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
                MemoCard(timestamp = timestamp, text = memoText)
            }
        }
    }
}

@Composable
fun MemoCard(timestamp: Long, text: String) {
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
        }
    }
}

@Composable
fun AddMemoScreen(modifier: Modifier = Modifier, onMemoSaved: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val context = LocalContext.current

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
                    val sharedPref = context.getSharedPreferences("memo_prefs", Context.MODE_PRIVATE)
                    with(sharedPref.edit()) {
                        putString(System.currentTimeMillis().toString(), text)
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
fun ProfileScreen(modifier: Modifier = Modifier) {
    Text(
        text = "プロフィール画面です。",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    RyoikumemoTheme {
        HomeScreen()
    }
}
