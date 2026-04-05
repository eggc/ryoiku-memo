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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.eggc.ryoikumemo.data.StampType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TimelineItemCard(
    timestamp: Long,
    stampType: StampType,
    note: String,
    operatorName: String?,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top
    ) {
        // 1. 時刻の列
        Box(
            modifier = Modifier
                .width(52.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(top = 20.dp, end = 8.dp) // カードのパディング 16dp + 内部パディング 4dp = 20dp
        ) {
            Text(
                text = SimpleDateFormat("H:mm", Locale.getDefault()).format(Date(timestamp)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End
            )
        }

        // 2. カード部分
        Card(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 16.dp), // 12dpから16dpへ増やし、上下合わせて32dp（約1.5行分）の余白を確保
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            ),
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
                                text = { Text("編集") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
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
