package net.eggc.ryoikumemo.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppDestinations(
    val label: String,
    val icon: ImageVector?,
) {
    TIMELINE("タイムライン", Icons.Default.Timeline),
    TOOLS("ツール", Icons.Default.Build),
    TASK("タスク", null),
    REVIEW("ふりかえり", null),
    NOTE("ノート", null),
    SETTINGS("設定", Icons.Default.Settings),
    TERMS("利用規約", null),
    PRIVACY_POLICY("プライバシーポリシー", null),
    GRAPH("グラフ", null),
    EDIT_STAMP("スタンプ編集", null),
    STAMP_ADD("スタンプ追加", null)
}
