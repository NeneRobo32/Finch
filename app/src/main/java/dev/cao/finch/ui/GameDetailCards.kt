package dev.cao.finch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.cao.finch.R
import dev.cao.finch.TimeFormatter
import dev.cao.finch.data.Game
import dev.cao.finch.data.GameRepository
import dev.cao.finch.data.GameStatus
import dev.cao.finch.data.PlaySession
import dev.cao.finch.ui.design.FinchButton
import dev.cao.finch.ui.design.FinchButtonVariant
import dev.cao.finch.ui.design.FinchCard
import dev.cao.finch.ui.design.FinchCheckbox
import dev.cao.finch.ui.design.FinchChip
import dev.cao.finch.ui.design.FinchDivider
import dev.cao.finch.ui.design.FinchInsetDivider
import dev.cao.finch.ui.design.FinchProgressBar
import dev.cao.finch.ui.design.FinchSpace
import dev.cao.finch.ui.design.FinchStars
import dev.cao.finch.ui.design.FinchStatTile
import dev.cao.finch.ui.design.FinchTextField
import dev.cao.finch.ui.design.finchClickable
import dev.cao.finch.ui.theme.FinchShapes
import java.time.Duration

// 游戏详情页的各个视觉块（v0.25.0 从 GameDetailScreen.kt 拆出，纯搬运不改行为）。
// 都是同 package 的 internal 组合函数，只有详情页自己用。

/** 封面全出血区：顶部压暗，保证状态栏与悬浮按钮可读 */
@Composable
internal fun DetailCover(game: Game, coverScale: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp))
            .graphicsLayer {
                scaleX = coverScale
                scaleY = coverScale
                alpha = coverScale
            },
    ) {
        GameCover(
            url = game.coverUrl,
            contentDescription = stringResource(R.string.a11y_cover, game.name),
            modifier = Modifier.fillMaxSize(),
            placeholder = {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.surfaceVariant,
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        platformIcon(game.platform),
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    )
                }
            },
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.5f),
                        0.40f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.12f),
                    )
                )
        )
    }
}

/** 标题 + 平台/状态副标题 */
@Composable
internal fun DetailTitle(game: Game) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            game.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(FinchSpace.xs))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            GameRepository.labelFor(game.platformSet()) + " · " + game.statusResolved().label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (game.completed && game.statusResolved() != GameStatus.COMPLETED) {
            Text(
                "  ·  已通关",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** 计时控制台：大号计时 + 开玩/停止 + 暂停/继续 */
@Composable
internal fun TimerConsole(
    running: Boolean,
    paused: Boolean,
    elapsedText: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
) {
    FinchCard(
        title = when {
            !running -> "计时"
            paused -> "已暂停"
            else -> "本次游玩"
        },
        icon = Icons.Filled.Timer,
        titleExtra = if (running) (if (paused) "暂停中" else "计时中") else null,
    ) {
        Text(
            elapsedText,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = if (paused) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(FinchSpace.md))
        Row(horizontalArrangement = Arrangement.spacedBy(FinchSpace.sm)) {
            FinchButton(
                text = if (running) "停止" else "开玩",
                icon = if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                variant = if (running) FinchButtonVariant.Danger else FinchButtonVariant.Primary,
                onClick = { if (running) onStop() else onStart() },
                modifier = Modifier.weight(1f),
            )
            if (running) {
                FinchButton(
                    text = if (paused) "继续" else "暂停",
                    icon = if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    variant = FinchButtonVariant.Glass,
                    onClick = { if (paused) onResume() else onPause() },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 三格统计：累计 / 会话数 / 最近游玩 */
@Composable
internal fun DetailStatRow(
    totalLabel: String,
    totalValue: String,
    sessionCount: String,
    lastPlayed: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(FinchSpace.sm)) {
        FinchStatTile(totalLabel, totalValue, Modifier.weight(1f))
        FinchStatTile("会话", sessionCount, Modifier.weight(1f))
        FinchStatTile("最近", lastPlayed, Modifier.weight(1f))
    }
}

/** 云端累计与本地记录的差额说明（v0.18.8） */
@Composable
internal fun RecordGapNote(recordValue: String, recordGapMin: Long) {
    Text(
        "本地记录 $recordValue · 差额 ${TimeFormatter.hoursMinutes(Duration.ofMinutes(recordGapMin))}" +
            "（同步只记新增时长，官方历史累计不摊成记录）",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 状态 / 已通关 / 评分 */
@Composable
internal fun StatusRatingCard(game: Game, viewModel: FinchViewModel) {
    FinchCard(title = "状态与评分", icon = Icons.Filled.Tune) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(FinchSpace.sm),
        ) {
            val cur = game.statusResolved()
            GameStatus.values().forEach { s ->
                FinchChip(
                    label = s.label,
                    selected = cur == s,
                    onClick = { viewModel.setStatus(game.id, s) },
                )
            }
            Spacer(Modifier.width(FinchSpace.xs))
        }
        Spacer(Modifier.height(FinchSpace.md))
        FinchDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .finchClickable(onClick = { viewModel.setCompleted(game.id, !game.completed) })
                .padding(vertical = FinchSpace.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FinchCheckbox(
                checked = game.completed,
                onCheckedChange = null,
            )
            Text("已通关", style = MaterialTheme.typography.bodyLarge)
            game.completedAt?.let {
                Text(
                    "  ·  ${it.format(TimeFormatter.DATE_YMD)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        FinchDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = FinchSpace.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("评分", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            FinchStars(
                rating = game.rating,
                onRate = { viewModel.setRating(game.id, it) },
                starSize = 26.dp,
                starDescription = { star, on ->
                    stringResource(
                        if (on) R.string.a11y_star_on else R.string.a11y_star_off,
                        star,
                    )
                },
            )
        }
    }
}

/** 感想：本地草稿 + 手动保存 */
@Composable
internal fun ThoughtsCard(
    thoughts: String,
    thoughtSaved: Boolean,
    onThoughtsChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    FinchCard(
        title = "感想",
        icon = Icons.Filled.EditNote,
        titleExtra = if (thoughtSaved) "已保存 ✓" else null,
    ) {
        FinchTextField(
            value = thoughts,
            onValueChange = onThoughtsChange,
            placeholder = "写点游玩感受、攻略笔记、剧透提醒…",
            singleLine = false,
            minLines = 3,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = FinchSpace.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${thoughts.length} 字",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FinchButton(
                text = "保存",
                variant = FinchButtonVariant.Glass,
                onClick = onSave,
            )
        }
    }
}

/** 游玩记录列表 */
@Composable
internal fun SessionsCard(
    sessions: List<PlaySession>,
    onDelete: (PlaySession) -> Unit,
    onEdit: (PlaySession) -> Unit,
) {
    FinchCard(
        title = "游玩记录",
        subtitle = if (sessions.isEmpty()) null else "共 ${sessions.size} 条",
        icon = Icons.Filled.History,
    ) {
        if (sessions.isEmpty()) {
            Text(
                "还没有记录，点上面的「开玩」开始第一次计时",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            sessions.forEachIndexed { index, s ->
                SessionRow(
                    s,
                    onDelete = { onDelete(s) },
                    onEdit = { onEdit(s) },
                )
                if (index != sessions.lastIndex) FinchInsetDivider()
            }
        }
    }
}

/** 悬浮顶栏（压在封面上）：深色圆底 + 白图标，滚过封面后仍可读 */
@Composable
internal fun DetailTopBar(game: Game, onBack: () -> Unit, onToggleFavorite: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = FinchSpace.md, vertical = FinchSpace.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverCircleButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "返回",
            onClick = onBack,
        )
        Box(
            Modifier
                .clip(FinchShapes.pill)
                .background(Color.Black.copy(alpha = 0.28f))
                .padding(horizontal = FinchSpace.md, vertical = 5.dp),
        ) {
            Text(
                "游戏详情",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
            )
        }
        CoverCircleButton(
            icon = if (game.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = if (game.favorite) "取消收藏" else "收藏",
            tint = if (game.favorite) Color(0xFFFF6B81) else Color.White,
            onClick = onToggleFavorite,
        )
    }
}

/** 封面上的圆形按钮：深色半透明底 + 白图标，保证任意封面上都可读 */
@Composable
internal fun CoverCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = Color.White,
) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.28f))
            .finchClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** 奖杯四围配色（白金/金/银/铜） */
private val PlatinumColor = Color(0xFF9CB6D1)
private val TrophyGoldColor = Color(0xFFE6B422)
private val TrophySilverColor = Color(0xFFAAB2BD)
private val TrophyBronzeColor = Color(0xFFB0722E)

/** PSN 奖杯卡：已获得四围 + 完成度条 */
@Composable
internal fun TrophyCard(platinum: Int, gold: Int, silver: Int, bronze: Int, progress: Int) {
    FinchCard(
        title = "PSN 奖杯",
        icon = Icons.Filled.EmojiEvents,
        titleExtra = "完成度 $progress%",
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TrophyCount("白金", platinum, PlatinumColor)
            TrophyCount("金", gold, TrophyGoldColor)
            TrophyCount("银", silver, TrophySilverColor)
            TrophyCount("铜", bronze, TrophyBronzeColor)
        }
        Spacer(Modifier.height(FinchSpace.md))
        FinchProgressBar(
            progress = progress / 100f,
            color = PlatinumColor,
            height = 8.dp,
        )
    }
}

@Composable
private fun TrophyCount(label: String, count: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text("$label $count", style = MaterialTheme.typography.labelMedium)
    }
}
