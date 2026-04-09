package net.eggc.ryoikumemo.ui.feature.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ToolsScreen(
    modifier: Modifier = Modifier,
    onTaskClick: () -> Unit,
    onSleepGraphClick: () -> Unit,
    onTimerClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "ツール",
            style = MaterialTheme.typography.headlineSmall
        )

        ToolItemCard(
            label = "タスク",
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            onClick = onTaskClick
        )

        ToolItemCard(
            label = "睡眠グラフ",
            icon = {
                Icon(
                    imageVector = Icons.Default.AutoStories,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            onClick = onSleepGraphClick
        )

        ToolItemCard(
            label = "タイマー",
            icon = {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            onClick = onTimerClick
        )
    }
}

@Composable
private fun ToolItemCard(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Text(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                text = label,
                style = MaterialTheme.typography.titleMedium
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
