package dev.cao.finch.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.VideogameAsset
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.cao.finch.R
import dev.cao.finch.TimeFormatter
import dev.cao.finch.data.Game
import dev.cao.finch.data.GameRepository
import dev.cao.finch.data.GameStatsRow
import dev.cao.finch.data.GameStatus
import dev.cao.finch.data.PlaySession
import dev.cao.finch.timer.TimerServiceBridge
import dev.cao.finch.ui.design.FinchButton
import dev.cao.finch.ui.design.FinchButtonVariant
import dev.cao.finch.ui.design.FinchCard
import dev.cao.finch.ui.design.FinchCheckbox
import dev.cao.finch.ui.design.FinchChip
import dev.cao.finch.ui.design.FinchConfirmDialog
import dev.cao.finch.ui.design.FinchDivider
import dev.cao.finch.ui.design.FinchInsetDivider
import dev.cao.finch.ui.design.FinchMotion
import dev.cao.finch.ui.design.FinchProgressBar
import dev.cao.finch.ui.design.FinchSpace
import dev.cao.finch.ui.design.FinchStars
import dev.cao.finch.ui.design.FinchStatTile
import dev.cao.finch.ui.design.FinchTextField
import dev.cao.finch.ui.design.finchClickable
import dev.cao.finch.ui.gamedetail.ConfettiOverlay
import dev.cao.finch.ui.gamedetail.HltbAuto
import dev.cao.finch.ui.gamedetail.HltbEmptyRow
import dev.cao.finch.ui.gamedetail.HltbProgressCard
import dev.cao.finch.ui.theme.FinchShapes
import java.time.Duration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 评分星的金色 */
private val StarGold = Color(0xFFF5B301)

/**
 * 游戏详情页（2026-10 UI 重构）：
 * 封面全出血 + 悬浮圆形操作按钮；内容区全部走 FinchCard 体系——
 * 计时控制台 / 三格统计 / PSN 奖杯 / 通关进度 / 状态与评分 / 感想 / 游玩记录。
 * 所有数据与计时逻辑与原版一致（计时走 TimerService，秒级文本本地刷新）。
 */
@Composable
fun GameDetailScreen(
    game: Game,
    running: Boolean,
    paused: Boolean = false,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    viewModel: FinchViewModel,
) {
    // 本次会话已玩时长（实时走秒，扣掉暂停；空闲时不每秒空转）
    var elapsedText by remember { mutableStateOf("00:00:00") }
    LaunchedEffect(running, paused) {
        if (!running) {
            elapsedText = "00:00:00"
            return@LaunchedEffect
        }
        while (true) {
            val started = TimerServiceBridge.startedAtMillis
            val pauseMs = TimerServiceBridge.pauseAccumMs + TimerServiceBridge.currentPauseMs()
            elapsedText = if (started > 0) {
                TimeFormatter.hms(Duration.ofMillis((System.currentTimeMillis() - started - pauseMs).coerceAtLeast(0L)))
            } else {
                "00:00:00"
            }
            delay(1000)
        }
    }

    // 通关庆祝：仅在 未通关 → 已通关 的瞬间触发一次
    var celebrate by remember { mutableStateOf(false) }
    val prevCompleted = remember { mutableStateOf(game.completed) }
    LaunchedEffect(game.completed) {
        if (game.completed && !prevCompleted.value) celebrate = true
        prevCompleted.value = game.completed
    }

    // 感想本地态（进入页面时从库读，保存按钮持久化）
    var thoughts by remember(game.id) { mutableStateOf(game.thoughts.orEmpty()) }
    var thoughtSaved by remember { mutableStateOf(false) }
    LaunchedEffect(thoughtSaved) {
        if (thoughtSaved) {
            delay(2000)
            thoughtSaved = false
        }
    }

    var pendingDelete by remember { mutableStateOf<PlaySession?>(null) }
    var pendingEdit by remember { mutableStateOf<PlaySession?>(null) }
    var sessionEditError by remember { mutableStateOf<String?>(null) }

    val stats by remember(game.id) { viewModel.gameStats(game.id) }
        .collectAsState(initial = GameStatsRow(0L, 0L, null))
    val sessions by remember(game.id) { viewModel.sessionsFor(game.id) }
        .collectAsState(initial = emptyList())

    // 累计口径（v0.18.3）：云平台官方总时长优先（PS 游戏统一按 PSN 同步时间），
    // 本地会话只做「游玩记录」明细，不再与官方累计各显示一个数
    val sessionMinutes = (stats.totalMs ?: 0L) / 60_000
    val totalMinutes = game.effectivePlaytimeMin(sessionMinutes)
    val totalLabel = game.cloudPlaytimeSource()?.let { "累计 · $it" } ?: "累计"
    val totalValue = TimeFormatter.hoursMinutes(Duration.ofMinutes(totalMinutes))
    // 云端累计 vs 本地记录的差额（v0.18.8）：云端只给终身累计、不给日期，所以差额不再
    // 用「推算的历史会话」补满——补出来的日期是编的，会把本月/热力图撑出几千小时。
    // 这里如实展示差额，让用户知道记录页为什么比累计少。
    val recordValue = TimeFormatter.hoursMinutes(Duration.ofMinutes(sessionMinutes))
    val recordGapMin = totalMinutes - sessionMinutes
    val showRecordGap = game.cloudPlaytimeSource() != null && recordGapMin >= 30

    val coverAnim = remember { Animatable(0.97f) }
    LaunchedEffect(Unit) {
        coverAnim.animateTo(1f, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow))
    }

    // 详情页封面顶到状态栏：浅色主题下把状态栏图标临时切成白色，
    // 离开页面再恢复（否则浅色主题的深色图标压在深色封面上看不清）
    val localView = androidx.compose.ui.platform.LocalView.current
    val hostActivity = androidx.activity.compose.LocalActivity.current
    androidx.compose.runtime.DisposableEffect(hostActivity) {
        val window = hostActivity?.window
        val controller = window?.let {
            androidx.core.view.WindowCompat.getInsetsController(it, localView)
        }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose {
            if (controller != null && previous != null) {
                controller.isAppearanceLightStatusBars = previous
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = FinchSpace.bottomBarClearance),
        ) {
            // ---- 封面全出血：顶部压暗保证状态栏/按钮可读 ----
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp))
                    .graphicsLayer {
                        scaleX = coverAnim.value
                        scaleY = coverAnim.value
                        alpha = coverAnim.value
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

            Column(Modifier.padding(horizontal = FinchSpace.screen)) {
                Spacer(Modifier.height(FinchSpace.lg))

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

                Spacer(Modifier.height(FinchSpace.lg))

                // ---- 计时控制台 ----
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

                Spacer(Modifier.height(FinchSpace.md))

                // ---- 三格统计 ----
                Row(horizontalArrangement = Arrangement.spacedBy(FinchSpace.sm)) {
                    FinchStatTile(totalLabel, totalValue, Modifier.weight(1f))
                    FinchStatTile("会话", "${stats.sessionCount}", Modifier.weight(1f))
                    FinchStatTile("最近", stats.lastPlayedAt?.let { relativeTime(it) } ?: "—", Modifier.weight(1f))
                }

                // ---- 累计与记录的差额说明（v0.18.8）----
                if (showRecordGap) {
                    Spacer(Modifier.height(FinchSpace.sm))
                    Text(
                        "本地记录 $recordValue · 差额 ${TimeFormatter.hoursMinutes(Duration.ofMinutes(recordGapMin))}" +
                            "（同步只记新增时长，官方历史累计不摊成记录）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // ---- PSN 奖杯 ----
                if (game.trophyProgress != null) {
                    Spacer(Modifier.height(FinchSpace.md))
                    TrophyCard(
                        platinum = game.trophyPlatinum ?: 0,
                        gold = game.trophyGold ?: 0,
                        silver = game.trophySilver ?: 0,
                        bronze = game.trophyBronze ?: 0,
                        progress = game.trophyProgress,
                    )
                }

                // ---- 通关进度（HLTB）：已玩与顶部「累计」同一口径，避免两个数打架 ----
                val playedMin = totalMinutes
                val hltbMin = game.hltbMainMin
                var hltbAutoState by remember(game.id) { mutableStateOf(HltbAuto.IDLE) }
                var hltbFailReason by remember(game.id) { mutableStateOf<String?>(null) }
                LaunchedEffect(game.id) {
                    val g = game
                    if ((g.hltbMainMin ?: 0) <= 0 && (g.hltbExtraMin ?: 0) <= 0 && (g.hltb100Min ?: 0) <= 0) {
                        hltbAutoState = HltbAuto.LOADING
                        viewModel.fetchHltbTimes(g.id, null) { r ->
                            hltbAutoState = if (r.isSuccess) HltbAuto.DONE else HltbAuto.FAILED
                            hltbFailReason = r.exceptionOrNull()?.message
                        }
                    } else {
                        hltbAutoState = HltbAuto.DONE
                    }
                }
                Spacer(Modifier.height(FinchSpace.md))
                if (hltbMin != null && hltbMin > 0) {
                    HltbProgressCard(
                        playedMin = playedMin,
                        hltbMin = hltbMin,
                        hltbExtraMin = game.hltbExtraMin,
                        hltb100Min = game.hltb100Min,
                        completed = game.completed,
                        viewModel = viewModel,
                        gameId = game.id,
                        onEdit = { viewModel.setHltb(game.id, it) },
                    )
                } else {
                    HltbEmptyRow(
                        viewModel = viewModel,
                        gameId = game.id,
                        autoState = hltbAutoState,
                        failReason = hltbFailReason,
                        onRetry = {
                            hltbAutoState = HltbAuto.LOADING
                            hltbFailReason = null
                            viewModel.fetchHltbTimes(game.id, null) { r ->
                                hltbAutoState = if (r.isSuccess) HltbAuto.DONE else HltbAuto.FAILED
                                hltbFailReason = r.exceptionOrNull()?.message
                            }
                        },
                        onSave = { viewModel.setHltb(game.id, it) },
                    )
                }

                Spacer(Modifier.height(FinchSpace.md))

                // ---- 状态 / 已通关 / 评分 ----
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

                Spacer(Modifier.height(FinchSpace.md))

                // ---- 感想 ----
                FinchCard(
                    title = "感想",
                    icon = Icons.Filled.EditNote,
                    titleExtra = if (thoughtSaved) "已保存 ✓" else null,
                ) {
                    FinchTextField(
                        value = thoughts,
                        onValueChange = {
                            thoughts = it
                            thoughtSaved = false
                        },
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
                            onClick = {
                                viewModel.setThoughts(game.id, thoughts)
                                thoughtSaved = true
                            },
                        )
                    }
                }

                Spacer(Modifier.height(FinchSpace.md))

                // ---- 游玩记录 ----
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
                                onDelete = { pendingDelete = s },
                                onEdit = { pendingEdit = s },
                            )
                            if (index != sessions.lastIndex) FinchInsetDivider()
                        }
                    }
                }

                Spacer(Modifier.height(FinchSpace.xxl))
            }
        }

        // ---- 悬浮顶栏（压在封面上）：深色圆底 + 白图标，滚过封面后仍可读 ----
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
                onClick = { viewModel.toggleFavorite(game.id) },
            )
        }

        if (celebrate) {
            ConfettiOverlay(Modifier.fillMaxSize(), onDone = { celebrate = false })
        }
    }

    pendingDelete?.let { s ->
        FinchConfirmDialog(
            title = "删除这条记录？",
            message = "删除后无法恢复。",
            confirmLabel = "删除",
            onConfirm = {
                viewModel.deleteSession(s.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }

    pendingEdit?.let { s ->
        val scope = rememberCoroutineScope()
        SessionEditDialog(
            session = s,
            error = sessionEditError,
            onDismiss = { pendingEdit = null; sessionEditError = null },
            onConfirm = { start, end ->
                scope.launch {
                    val r = viewModel.updateSessionTimes(s.id, start, end)
                    if (r.isSuccess) {
                        pendingEdit = null
                        sessionEditError = null
                    } else {
                        sessionEditError = r.exceptionOrNull()?.message ?: "保存失败"
                    }
                }
            },
        )
    }
}

/** 封面上的圆形按钮：深色半透明底 + 白图标，保证任意封面上都可读 */
@Composable
private fun CoverCircleButton(
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
private fun TrophyCard(platinum: Int, gold: Int, silver: Int, bronze: Int, progress: Int) {
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

