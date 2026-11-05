package dev.cao.finch.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import dev.cao.finch.TimeFormatter
import dev.cao.finch.data.Game
import dev.cao.finch.data.GameStatsRow
import dev.cao.finch.data.PlaySession
import dev.cao.finch.timer.TimerServiceBridge
import dev.cao.finch.ui.design.FinchConfirmDialog
import dev.cao.finch.ui.design.FinchSpace
import dev.cao.finch.ui.gamedetail.ConfettiOverlay
import dev.cao.finch.ui.gamedetail.HltbAuto
import dev.cao.finch.ui.gamedetail.HltbEmptyRow
import dev.cao.finch.ui.gamedetail.HltbProgressCard
import java.time.Duration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 游戏详情页（2026-10 UI 重构；2026-11 v0.25.0 拆出视觉块到 GameDetailCards.kt）：
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
    val localView = LocalView.current
    val hostActivity = LocalActivity.current
    DisposableEffect(hostActivity) {
        val window = hostActivity?.window
        val controller = window?.let {
            WindowCompat.getInsetsController(it, localView)
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
            DetailCover(game = game, coverScale = coverAnim.value)

            Column(Modifier.padding(horizontal = FinchSpace.screen)) {
                Spacer(Modifier.height(FinchSpace.lg))

                DetailTitle(game)

                Spacer(Modifier.height(FinchSpace.lg))

                TimerConsole(
                    running = running,
                    paused = paused,
                    elapsedText = elapsedText,
                    onStart = onStart,
                    onStop = onStop,
                    onPause = onPause,
                    onResume = onResume,
                )

                Spacer(Modifier.height(FinchSpace.md))

                DetailStatRow(
                    totalLabel = totalLabel,
                    totalValue = totalValue,
                    sessionCount = "${stats.sessionCount}",
                    lastPlayed = stats.lastPlayedAt?.let { relativeTime(it) } ?: "—",
                )

                if (showRecordGap) {
                    Spacer(Modifier.height(FinchSpace.sm))
                    RecordGapNote(recordValue = recordValue, recordGapMin = recordGapMin)
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

                StatusRatingCard(game = game, viewModel = viewModel)

                Spacer(Modifier.height(FinchSpace.md))

                ThoughtsCard(
                    thoughts = thoughts,
                    thoughtSaved = thoughtSaved,
                    onThoughtsChange = {
                        thoughts = it
                        thoughtSaved = false
                    },
                    onSave = {
                        viewModel.setThoughts(game.id, thoughts)
                        thoughtSaved = true
                    },
                )

                Spacer(Modifier.height(FinchSpace.md))

                SessionsCard(
                    sessions = sessions,
                    onDelete = { pendingDelete = it },
                    onEdit = { pendingEdit = it },
                )

                Spacer(Modifier.height(FinchSpace.xxl))
            }
        }

        DetailTopBar(
            game = game,
            onBack = onBack,
            onToggleFavorite = { viewModel.toggleFavorite(game.id) },
        )

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
            onConfirm = { start, end, note ->
                scope.launch {
                    val r = viewModel.updateSessionTimes(s.id, start, end, note)
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
