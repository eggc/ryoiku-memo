package net.eggc.ryoikumemo.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppDestinations(
    val label: String,
    val icon: ImageVector?,
) {
    TIMELINE("タイムライン", Icons.Default.Timeline),
    STAMP("きろく", Icons.Default.AccessTime),
    REVIEW("ふりかえり", Icons.Default.AutoStories),
    NOTE("ノート", null),
    SETTINGS("設定", Icons.Default.Settings),
    TERMS("利用規約", null),
    PRIVACY_POLICY("プライバシーポリシー", null),
    GRAPH("グラフ", null),
    EDIT_STAMP("スタンプ編集", null)
}
