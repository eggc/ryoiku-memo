package net.eggc.ryoikumemo.ui.feature.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.eggc.ryoikumemo.data.StampType
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

/** 日付列の幅 */
internal val DATE_COLUMN_WIDTH = 36.dp

/** 時刻列の幅 */
internal val TIME_COLUMN_WIDTH = 44.dp

@Composable
fun TimelineItemCard(
    timestamp: Long,
    date: LocalDate,
    showDate: Boolean,
    stampType: StampType,
    note: String,
    operatorName: String?,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top
    ) {
        // 1. 日付の列
        Box(
            modifier = Modifier
                .width(DATE_COLUMN_WIDTH)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(top = 18.dp, start = 4.dp, end = 2.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            if (showDate) {
                val day = date.dayOfMonth
                val weekday = when (date.dayOfWeek) {
                    java.time.DayOfWeek.MONDAY -> "月"
                    java.time.DayOfWeek.TUESDAY -> "火"
                    java.time.DayOfWeek.WEDNESDAY -> "水"
                    java.time.DayOfWeek.THURSDAY -> "木"
                    java.time.DayOfWeek.FRIDAY -> "金"
                    java.time.DayOfWeek.SATURDAY -> "土"
                    java.time.DayOfWeek.SUNDAY -> "日"
                }
                Text(
                    text = "${day}日\n($weekday)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    lineHeight = MaterialTheme.typography.labelSmall.fontSize * 1.3
                )
            }
        }

        // 2. 時刻の列
        Box(
            modifier = Modifier
                .width(TIME_COLUMN_WIDTH)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(top = 20.dp, end = 6.dp)
        ) {
            Text(
                text = SimpleDateFormat("H:mm", Locale.getDefault()).format(Date(timestamp)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End
            )
        }

        // 3. カード部分
        Card(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        stampType.icon,
                        contentDescription = stampType.label,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stampType.label,
                        style = MaterialTheme.typography.titleSmall
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = operatorName ?: "不明",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        modifier = Modifier.width(50.dp),
                        textAlign = TextAlign.End,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "メニュー",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("コピー") },
                                leadingIcon = {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    val time = SimpleDateFormat("H:mm", Locale.getDefault())
                                        .format(Date(timestamp))
                                    val textToCopy = if (note.isNotBlank()) {
                                        "[$time] ${stampType.label}\n$note"
                                    } else {
                                        "[$time] ${stampType.label}"
                                    }
                                    clipboardManager.setText(AnnotatedString(textToCopy))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("編集") },
                                leadingIcon = {
                                    Icon(Icons.Default.Edit, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    onEditClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("削除", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onDeleteClick()
                                }
                            )
                        }
                    }
                }
                if (note.isNotBlank()) {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}
