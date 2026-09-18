package dev.cao.finch.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cao.finch.data.Platform

/** 跨屏共享的小 UI 工具：统计格 / 平台图标 / 平台短名 / 相对时间 */

/** 玻璃小统计格（详情页三格统计等） */
@Composable
internal fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier, shape = RoundedCornerShape(18.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** 平台 → 图标（卡片占位、详情页无封面时） */
internal fun platformIcon(platform: Platform): ImageVector = when (platform) {
    Platform.PC -> Icons.Filled.Computer
    Platform.SWITCH -> Icons.Filled.VideogameAsset
    Platform.PS -> Icons.Filled.SportsEsports
    Platform.Multi -> Icons.Filled.DevicesOther
}

/** 平台 → 短标签 */
fun platformLabel(platform: Platform): String = when (platform) {
    Platform.PC -> "PC"
    Platform.SWITCH -> "Switch"
    Platform.PS -> "PS"
    Platform.Multi -> "多平台"
}

/** 相对时间人性化：刚刚 / X 分钟前 / X 小时前 / 昨天 / X 天前 / 日期 */
internal fun relativeTime(epochMillis: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - epochMillis
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3600_000 -> "${diff / 60_000} 分钟前"
        diff < 24 * 3600_000 -> "${diff / 3600_000} 小时前"
        diff < 48 * 3600_000 -> "昨天"
        diff < 30 * 24 * 3600_000 -> "${diff / (24 * 3600_000)} 天前"
        else -> {
            val d = java.time.Instant.ofEpochMilli(epochMillis)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            "%d-%02d".format(d.year, d.monthValue)
        }
    }
}
