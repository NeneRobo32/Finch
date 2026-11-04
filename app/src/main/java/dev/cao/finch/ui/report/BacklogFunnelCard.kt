package dev.cao.finch.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.cao.finch.TimeFormatter
import dev.cao.finch.data.Game
import dev.cao.finch.data.GameStatus
import dev.cao.finch.ui.design.FinchCard
import dev.cao.finch.ui.design.FinchSpace
import dev.cao.finch.ui.theme.FinchShapes
import java.time.Duration
import kotlin.math.roundToInt

/**
 * 库存漏斗（v0.24）：想玩 → 在玩 → 搁置 → 通关 → 全成就。
 *
 * 一条按数量分段的堆叠条 ＋ 每段一行明细。称「漏斗」是因为它把库里的存量
 * 摊成一条从「想玩」流向「全成就」的管道：条越长说明积压越多，
 * 越靠右说明这批库存正在被消耗掉。
 */
@Composable
internal fun BacklogFunnelCard(
    games: List<Game>,
    totalsMs: Map<Long, Long>,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val funnel = backlogFunnel(games, totalsMs)
    val total = funnel.sumOf { it.count }
    val rate = completionRate(games)

    FinchCard(
        modifier = modifier,
        title = "库存漏斗",
        icon = Icons.Filled.FilterAlt,
        titleExtra = "${(rate * 100).roundToInt()}% 已通关",
    ) {
        if (total == 0) {
            Text(
                "游戏库还是空的，添加几款游戏后这里会长出一条漏斗。",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            return@FinchCard
        }

        // 堆叠条：按数量分段，0 计数的段不占宽度（但仍然出现在下面的列表里）
        Row(
            Modifier
                .fillMaxWidth()
                .height(18.dp)
                .clip(FinchShapes.pill)
                .background(scheme.surfaceVariant.copy(alpha = 0.55f)),
        ) {
            funnel.forEach { entry ->
                val frac = entry.count.toFloat() / total
                if (frac > 0f) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(frac)
                            .background(statusColor(entry.status)),
                    )
                }
            }
        }

        Spacer(Modifier.height(FinchSpace.md))

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            funnel.forEach { entry ->
                val frac = if (total > 0) entry.count.toFloat() / total else 0f
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(9.dp)
                            .clip(FinchShapes.thumb)
                            .background(statusColor(entry.status)),
                    )
                    Spacer(Modifier.width(FinchSpace.sm))
                    Text(
                        entry.status.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.width(46.dp),
                    )
                    Text(
                        "${entry.count} 款",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.width(48.dp),
                    )
                    Text(
                        "${(frac * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.width(40.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        TimeFormatter.hoursMinutes(Duration.ofMillis(entry.totalMs)),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // 吃灰提醒：只在真有想玩/搁置的存货时才占位
        val aged = agedBacklog(games)
        if (aged.isNotEmpty()) {
            Spacer(Modifier.height(FinchSpace.md))
            Text(
                "吃灰最久：" + aged.joinToString("、") { it.name },
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 状态 → 条形/色点颜色（越靠「完成」越暖，与统计页的 primary/tertiary 语言一致） */
@Composable
private fun statusColor(status: GameStatus): Color {
    val scheme = MaterialTheme.colorScheme
    return when (status) {
        GameStatus.WANT -> scheme.primary.copy(alpha = 0.28f)
        GameStatus.PLAYING -> scheme.primary
        GameStatus.PAUSED -> scheme.primary.copy(alpha = 0.55f)
        GameStatus.COMPLETED -> scheme.tertiary.copy(alpha = 0.75f)
        GameStatus.MASTERED -> scheme.tertiary
    }
}

/** 小圆角标签：年度报告里给「通关 / 全成就」这类词上色 */
@Composable
internal fun reportChip(text: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
