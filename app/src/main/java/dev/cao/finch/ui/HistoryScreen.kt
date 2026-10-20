package dev.cao.finch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.cao.finch.TimeFormatter
import dev.cao.finch.data.SessionWithGame
import dev.cao.finch.ui.design.FinchBadge
import dev.cao.finch.ui.design.FinchConfirmDialog
import dev.cao.finch.ui.design.FinchEmptyState
import dev.cao.finch.ui.design.FinchHeader
import dev.cao.finch.ui.design.FinchIconButton
import dev.cao.finch.ui.design.FinchMotion
import dev.cao.finch.ui.design.FinchSectionTitle
import dev.cao.finch.ui.design.FinchSpace
import dev.cao.finch.ui.design.GlassStyle
import dev.cao.finch.ui.design.GlassSurface
import dev.cao.finch.ui.design.finchClickable
import dev.cao.finch.ui.theme.FinchShapes
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 记录页（2026-10 UI 重构）：
 * 按天分组的玻璃列表，进行中的会话带实时走秒与计时徽章；
 * 点卡片编辑起止，右侧删除走统一确认弹窗。
 */
@Composable
fun HistoryScreen(viewModel: FinchViewModel) {
    val sessions by viewModel.recentSessions.collectAsState()

    // 有进行中的会话时每秒刷新一次“现在”，时长才会实时走（原来只在重组时算一次）
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    val hasRunning = sessions.any { it.session.endTime == null }
    LaunchedEffect(hasRunning) {
        if (!hasRunning) return@LaunchedEffect
        while (true) {
            now = LocalDateTime.now()
            delay(1000)
        }
    }

    val groups = remember(sessions) {
        sessions.groupBy { it.session.startTime.toLocalDate() }
            .toSortedMap(compareByDescending { it })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = FinchSpace.screen),
    ) {
        Spacer(Modifier.height(FinchSpace.sm))
        FinchHeader(
            title = "记录",
            subtitle = if (sessions.isEmpty()) "最近 300 条游玩会话" else "最近 ${sessions.size} 条 · 按天分组",
        )
        Spacer(Modifier.height(FinchSpace.md))

        if (sessions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                FinchEmptyState(
                    icon = Icons.Filled.History,
                    title = "还没有游玩记录",
                    message = "去「主页」点开一个游戏，按「开玩」开始计时，记录会自动出现在这里。",
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(FinchSpace.sm),
                contentPadding = PaddingValues(bottom = FinchSpace.bottomBarClearance),
            ) {
                groups.forEach { (day, items) ->
                    item(key = "day_$day") {
                        FinchSectionTitle(
                            dayLabel(day),
                            trailing = "${items.size} 条 · ${TimeFormatter.hoursMinutes(Duration.ofMillis(items.sumOf { it.session.effectiveMillis() }))}",
                        )
                    }
                    items(items.size, key = { i -> items[i].session.id }) { index ->
                        val item = items[index]
                        SessionCard(
                            item = item,
                            // 只有进行中的行才读 now：已完成的行读到的是常量 null，不会订阅秒级状态。
                            // 之前每行都传 now，计时中每秒整个列表一起重组 → 每张卡都要重录玻璃层（卡顿主因）
                            now = if (item.session.endTime == null) now else null,
                            viewModel = viewModel,
                        )
                    }
                }
            }
        }
    }
}

private fun dayLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> date.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA))
    }
}

@Composable
private fun SessionCard(
    item: SessionWithGame,
    /** 进行中会话的「现在」；已完成会话传 null（避免订阅秒级状态） */
    now: LocalDateTime?,
    viewModel: FinchViewModel,
) {
    val s = item.session
    val running = s.endTime == null
    var showConfirm by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var editError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // 已完成会话扣暂停；计时中实时走（扣暂停）
    val duration = if (s.endTime != null) {
        Duration.ofMillis(s.effectiveMillis())
    } else {
        val at = now ?: s.startTime
        Duration.between(s.startTime, at)
            .minusMillis(s.pauseAccumMs + (s.pauseStartedAt?.let { Duration.between(it, at).toMillis() } ?: 0L))
            .let { if (it.isNegative) Duration.ZERO else it }
    }

    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        style = GlassStyle.Tile,
        shape = FinchShapes.tile,
        onClick = if (running) null else ({ showEdit = true }),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = FinchSpace.md, end = FinchSpace.xs, top = FinchSpace.md, bottom = FinchSpace.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(FinchShapes.pill)
                    .background(platformColor(item.game.platform).copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    item.game.name.take(1),
                    style = MaterialTheme.typography.titleMedium,
                    color = platformColor(item.game.platform),
                )
            }
            Spacer(Modifier.width(FinchSpace.md))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.game.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (running) {
                        Spacer(Modifier.width(FinchSpace.sm))
                        FinchBadge(
                            text = if (s.isPaused()) "已暂停" else "计时中",
                            tint = if (s.isPaused()) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Text(
                    TimeFormatter.range(s.startTime, s.endTime ?: now ?: s.startTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                if (running) TimeFormatter.hms(duration) else TimeFormatter.hoursMinutes(duration),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            if (!running) {
                Spacer(Modifier.width(FinchSpace.xxs))
                FinchIconButton(
                    icon = Icons.Filled.Delete,
                    contentDescription = "删除这条记录",
                    onClick = { showConfirm = true },
                    ghost = true,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
        }
    }

    if (showEdit && s.endTime != null) {
        SessionEditDialog(
            session = s,
            error = editError,
            onDismiss = { showEdit = false; editError = null },
            onConfirm = { start, end ->
                scope.launch {
                    val r = viewModel.updateSessionTimes(s.id, start, end)
                    if (r.isSuccess) {
                        showEdit = false
                        editError = null
                    } else {
                        editError = r.exceptionOrNull()?.message ?: "保存失败"
                    }
                }
            },
        )
    }

    if (showConfirm) {
        FinchConfirmDialog(
            title = "删除这条记录？",
            message = "时长 ${TimeFormatter.hoursMinutes(duration)}，删除后不可恢复。",
            confirmLabel = "删除",
            onConfirm = {
                showConfirm = false
                viewModel.deleteSession(s.id)
            },
            onDismiss = { showConfirm = false },
        )
    }
}

@Composable
fun platformColor(platform: dev.cao.finch.data.Platform) = when (platform) {
    dev.cao.finch.data.Platform.PC -> MaterialTheme.colorScheme.primary
    dev.cao.finch.data.Platform.SWITCH -> MaterialTheme.colorScheme.tertiary
    dev.cao.finch.data.Platform.PS -> MaterialTheme.colorScheme.secondary
    dev.cao.finch.data.Platform.Multi -> MaterialTheme.colorScheme.outline
}
