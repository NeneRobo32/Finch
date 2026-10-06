package dev.cao.finch.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import dev.cao.finch.data.Platform

/** 跨屏共享的小 UI 工具：尺寸常量 / 游戏封面 / 统计格 / 平台图标 / 平台短名 / 相对时间 */

/** 跨屏统一尺寸常量 */
internal object Dimens {
    /** 悬浮底栏（64dp 胶囊 + 抬高留白）压在内容底部，各屏底部让位统一用这个值 */
    val BottomBarOverlap = 150.dp
}

/**
 * 游戏封面统一组件（旧实现只在 url==null 时给占位、加载失败留白）：
 * 占位垫在图片底下，加载中/失败自然透出；crossfade 等由全局 ImageLoader 统一配置。
 * @param contentDescription 有意义的大封面（详情页）传「xxx 封面」；列表缩略图传 null（装饰语义，旁边有标题）
 * @param requestSize 缩略图传目标像素边长（如 88），请求端解码降采样省内存
 * @param onCoverLoaded 加载结果回调（true=出图 / false=失败），详情页据此切换顶栏前景色
 * @param placeholder 各调用点原有占位（surfaceVariant + 平台图标等），保持原视觉
 */
@Composable
internal fun GameCover(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    requestSize: Int? = null,
    onCoverLoaded: ((Boolean) -> Unit)? = null,
    placeholder: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        placeholder()
        if (url != null) {
            val context = LocalContext.current
            AsyncImage(
                model = if (requestSize != null) {
                    ImageRequest.Builder(context).data(url).size(requestSize).build()
                } else {
                    url
                },
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onSuccess = { onCoverLoaded?.invoke(true) },
                onError = { onCoverLoaded?.invoke(false) },
            )
        }
    }
}

/** 日期 + 开始/结束三输入框（会话编辑弹窗与手动补录表单共用；label 为占位示例格式提示） */
@Composable
internal fun SessionTimeFields(
    dateField: String,
    onDateChange: (String) -> Unit,
    startField: String,
    onStartChange: (String) -> Unit,
    endField: String,
    onEndChange: (String) -> Unit,
) {
    androidx.compose.material3.OutlinedTextField(
        value = dateField,
        onValueChange = onDateChange,
        label = { Text("日期 2025-8-30") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.material3.OutlinedTextField(
            value = startField,
            onValueChange = onStartChange,
            label = { Text("开始 21:30") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        androidx.compose.material3.OutlinedTextField(
            value = endField,
            onValueChange = onEndChange,
            label = { Text("结束 23:05") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
}

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
            // 带日完整日期（旧版只到 "2025-08" 有歧义）；指定 Locale 防非常规 locale 数字变形
            String.format(java.util.Locale.CHINA, "%d-%02d-%02d", d.year, d.monthValue, d.dayOfMonth)
        }
    }
}
