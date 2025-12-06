package net.eggc.ryoikumemo

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
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
