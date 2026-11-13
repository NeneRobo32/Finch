package dev.cao.finch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.cao.finch.TimeFormatter
import dev.cao.finch.data.Game
import dev.cao.finch.data.GameRepository
import dev.cao.finch.data.TopGameRow
import dev.cao.finch.ui.design.FinchConfirmDialog
import dev.cao.finch.ui.design.FinchDropdownItem
import dev.cao.finch.ui.design.FinchDropdownMenu
import dev.cao.finch.ui.design.FinchIconButton
import dev.cao.finch.ui.design.FinchSpace
import dev.cao.finch.ui.design.GlassStyle
import dev.cao.finch.ui.design.GlassSurface
import dev.cao.finch.ui.design.finchEnter
import dev.cao.finch.ui.theme.FinchShapes
import dev.cao.finch.ui.theme.finchBundle
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun homeSubtitle(gameCount: Int, monthMs: Long): String {
    val hour = LocalTime.now().hour
    val greeting = when (hour) {
        in 5..10 -> "早上好"
        in 11..13 -> "中午好"
        in 14..17 -> "下午好"
        in 18..22 -> "晚上好"
        else -> "夜深了"
    }
    val date = LocalDate.now().format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA))
    val month = if (monthMs > 0) {
        " · 本月 ${TimeFormatter.hoursMinutes(java.time.Duration.ofMillis(monthMs))}"
    } else {
        ""
    }
    return "$greeting · $date · $gameCount 款游戏$month"
}

/** 本月「玩得最多」hero：封面 + 底部渐变，或无数据时的主题渐变引导卡 */
@Composable
internal fun MonthHeroCard(top: TopGameRow?, monthTotal: Long) {
    val scheme = MaterialTheme.colorScheme
    // 审查 A1：原来定死 196dp，用户把系统字号调到 1.3x 时 49sp 的数字和 26sp 标题会被裁掉。
    // 这里跟着字号放大（1.0x 时仍是 196dp，上限 1.45x 保证封面不变形）。
    val height = 196.dp * LocalDensity.current.fontScale.coerceIn(1f, 1.45f)
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .finchEnter(),
        style = GlassStyle.Hero,
        shape = FinchShapes.hero,
    ) {
        if (top != null) {
            GameCover(
                url = top.coverUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                placeholder = {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Brush.linearGradient(listOf(scheme.primaryContainer, scheme.surfaceVariant))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.VideogameAsset,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = scheme.primary.copy(alpha = 0.5f),
                        )
                    }
                },
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.05f),
                            0.45f to Color.Black.copy(alpha = 0.18f),
                            1f to Color.Black.copy(alpha = 0.82f),
                        )
                    )
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(FinchSpace.xl),
            ) {
                Text(
                    "本月玩得最多",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    top.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        TimeFormatter.hoursMinutes(java.time.Duration.ofMillis(top.totalMs)),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Spacer(Modifier.width(FinchSpace.sm))
                    Text(
                        "本月累计 ${TimeFormatter.hoursMinutes(java.time.Duration.ofMillis(monthTotal))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(scheme.primary, scheme.tertiary))),
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(150.dp)
                    .background(
                        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent)),
                        CircleShape,
                    ),
            )
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .size(90.dp)
                    .background(
                        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent)),
                        CircleShape,
                    ),
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(FinchSpace.xl),
            ) {
                Text("本月玩得最多", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
                Spacer(Modifier.height(4.dp))
                Text(
                    "本月还没有游玩记录",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "点开任意游戏，按「开玩」开始计时",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
        }
    }
}

/**
 * 游戏横卡：封面缩略 + 名称/元信息 + 右侧评分与更多菜单；长按删除。
 * 审查 D2：原来的箭头圆钮不带任何说明、也不响应长按（长按被整卡吃掉），
 * 改成「更多」按钮 → 菜单（详情/删除），点按和长按都能走到删除确认。
 */
@Composable
internal fun GameRowCard(
    game: Game,
    lastPlayedAt: Long?,
    totalMs: Long?,
    running: Boolean,
    paused: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    GlassSurface(
        // 滚动时新滑进视野的卡片同样播一次浮现（order=0 不叠延迟：
        // 它们本来就是依次出现的，再加错开反而像卡顿）
        modifier = Modifier.fillMaxWidth().finchEnter(),
        // 列表行（内容全是 Text）用 Tile 档：Card 会为每行多录一张离屏层，
        // 而这一行里没有任何需要折射的控件。圆角保持 card(24) 不动，三档归位留到后续。
        style = GlassStyle.Tile,
        shape = FinchShapes.card,
        onClick = onClick,
        onLongClick = { confirmDelete = true },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(FinchSpace.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(96.dp)
                    .height(72.dp)
                    .clip(FinchShapes.thumb)
                    .background(scheme.surfaceVariant),
            ) {
                GameCover(
                    url = game.coverUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    requestSize = 192,
                    placeholder = {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                platformIcon(game.platform),
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                    },
                )
                if (running) {
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(5.dp)
                            .clip(FinchShapes.pill)
                            .background(if (paused) scheme.tertiary else Color(0xFFE5484D))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            if (paused) "暂停" else "计时",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                        )
                    }
                }
            }
            Spacer(Modifier.width(FinchSpace.md))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    game.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(FinchShapes.pill)
                            .background(platformColor(game.platform)),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        GameRepository.labelFor(game.platformSet()) + " · " + game.statusResolved().label,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (game.completed && game.statusResolved() != dev.cao.finch.data.GameStatus.COMPLETED) {
                        Text(
                            " · 已通关",
                            style = MaterialTheme.typography.labelSmall,
                            // 审查 A2：11sp 小字用 primary 在浅色下对比度不足，改用 accentText
                            color = finchBundle().accentText,
                        )
                    }
                }
                val metaLine = buildString {
                    lastPlayedAt?.let { append("上次玩 · ${relativeTime(it)}") }
                    totalMs?.takeIf { it > 0 }?.let {
                        if (isNotEmpty()) append("  ·  ")
                        append("累计 ${TimeFormatter.hoursMinutes(java.time.Duration.ofMillis(it))}")
                    }
                    if (isEmpty()) append("还没有记录")
                }
                Text(
                    metaLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (lastPlayedAt != null && System.currentTimeMillis() - lastPlayedAt < 7L * 24 * 3600 * 1000) {
                        // 审查 A2：7 天内的强调色同样换 accentText
                        finchBundle().accentText
                    } else {
                        scheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(FinchSpace.sm))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                game.rating?.takeIf { it > 0 }?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFF5B301), modifier = Modifier.size(13.dp))
                        Text(" $it", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                    }
                }
                Box {
                    // 审查 D2：34dp 的箭头圆钮既无说明也不可长按（长按被整卡吃掉），换成显式的「更多」菜单
                    FinchIconButton(
                        icon = Icons.Filled.MoreVert,
                        contentDescription = "更多操作",
                        onClick = { menuOpen = true },
                        ghost = true,
                    )
                    FinchDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        FinchDropdownItem(text = "查看详情", onClick = {
                            menuOpen = false
                            onClick()
                        })
                        FinchDropdownItem(text = "删除", onClick = {
                            menuOpen = false
                            confirmDelete = true
                        })
                    }
                }
            }
        }
    }
    if (confirmDelete) {
        FinchConfirmDialog(
            title = "删除游戏？",
            message = "将同时删除「${game.name}」的所有计时记录，删除后不可恢复。",
            confirmLabel = "删除",
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
