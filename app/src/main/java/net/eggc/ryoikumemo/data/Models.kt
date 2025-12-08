package net.eggc.ryoikumemo.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.SentimentVeryDissatisfied
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector

sealed interface TimelineItem {
    val timestamp: Long
}

data class StampItem(
    override val timestamp: Long,
    val type: StampType,
    val note: String
) : TimelineItem

sealed interface TimelineFilter {
    data object All : TimelineFilter
    data class StampOnly(val type: StampType) : TimelineFilter
}

enum class StampType(val label: String, val icon: ImageVector) {
    SLEEP("ねる", Icons.Default.Bedtime),
    WAKE_UP("おきる", Icons.Default.WbSunny),
    PAINFUL("つらい", Icons.Default.SentimentVeryDissatisfied),
    FUN("たのしい", Icons.Default.SentimentSatisfied),
    TANTRUM("かんしゃく", Icons.Default.LocalFireDepartment),
    MEDICATION("おくすり", Icons.Default.Medication),
    MEMO("メモ", Icons.Default.StickyNote2),
}
