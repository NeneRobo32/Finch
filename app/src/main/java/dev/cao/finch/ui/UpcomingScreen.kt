package dev.cao.finch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.cao.finch.R
import dev.cao.finch.data.GameRepository
import dev.cao.finch.data.Platform
import dev.cao.finch.ui.design.FinchBadge
import dev.cao.finch.ui.design.FinchButton
import dev.cao.finch.ui.design.FinchButtonVariant
import dev.cao.finch.ui.design.FinchCard
import dev.cao.finch.ui.design.FinchChip
import dev.cao.finch.ui.design.FinchEmptyState
import dev.cao.finch.ui.design.FinchHeader
import dev.cao.finch.ui.design.FinchIconButton
import dev.cao.finch.ui.design.FinchLoading
import dev.cao.finch.ui.design.FinchSectionTitle
import dev.cao.finch.ui.design.FinchSheet
import dev.cao.finch.ui.design.FinchSpace
import dev.cao.finch.ui.design.FinchTextField
import dev.cao.finch.ui.design.GlassStyle
import dev.cao.finch.ui.design.GlassSurface
import dev.cao.finch.ui.theme.FinchShapes
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 发售日程页（2026-10 UI 重构）：
 * 页头 → 数据源胶囊 → 搜索 → 我的关注 → 分组列表（玻璃横卡）。
 * 数据源切换、关注、入库与平台补选逻辑不变。
 */
@Composable
fun UpcomingScreen(
    upcomingViewModel: UpcomingViewModel = viewModel(),
) {
    val state by upcomingViewModel.state.collectAsState()
    var platformPickerFor by remember { mutableStateOf<UpcomingViewModel.UpcomingEntry?>(null) }
    var query by remember { mutableStateOf("") }
    val source by upcomingViewModel.source.collectAsState()
    val followKeys by upcomingViewModel.followKeys.collectAsState()
    val follows by upcomingViewModel.follows.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = FinchSpace.screen),
    ) {
        Spacer(Modifier.height(FinchSpace.sm))
        FinchHeader(
            title = "发售日程",
            subtitle = "游民星空 / eShop / Steam / Bangumi 多源聚合",
            actions = {
                FinchIconButton(
                    icon = Icons.Filled.Refresh,
                    contentDescription = "刷新日程",
                    onClick = { upcomingViewModel.refresh() },
                )
            },
        )
        Spacer(Modifier.height(FinchSpace.md))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(FinchSpace.sm),
        ) {
            UpcomingViewModel.Source.entries.forEach { s ->
                FinchChip(
                    label = s.label,
                    selected = source == s,
                    onClick = { upcomingViewModel.setSource(s) },
                )
            }
        }
        Spacer(Modifier.height(FinchSpace.sm))
        FinchTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = "搜索发售日程…",
            leadingIcon = Icons.Filled.Search,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(FinchSpace.md))

        val s = state
        when (s) {
            is UpcomingViewModel.LoadState.Failed -> {
                FinchEmptyState(
                    icon = Icons.Filled.Event,
                    title = "日程加载失败",
                    message = s.message,
                    actionLabel = "重试",
                    onAction = { upcomingViewModel.refresh() },
                    modifier = Modifier.padding(top = FinchSpace.sm),
                )
            }

            is UpcomingViewModel.LoadState.Ready -> {
                val q = query.trim().lowercase()
                val searchPool = if (q.isEmpty()) s.entries else s.allEntries
                val filtered = if (q.isEmpty()) searchPool else searchPool.filter {
                    it.name.lowercase().contains(q) || (it.nameCn?.lowercase()?.contains(q) == true)
                }
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(FinchSpace.sm),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        top = FinchSpace.xs,
                        bottom = FinchSpace.bottomBarClearance,
                    ),
                ) {
                    if (follows.isNotEmpty()) {
                        item(key = "follows") {
                            FinchCard(
                                title = "我的关注",
                                icon = Icons.Filled.Star,
                                titleExtra = "${follows.size}",
                                style = GlassStyle.Card,
                            ) {
                                follows.take(10).forEach { f ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            f.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Spacer(Modifier.width(FinchSpace.sm))
                                        val today = LocalDate.now()
                                        val countdown = remember(f.dateIso, today) {
                                            val d = runCatching {
                                                LocalDate.parse(f.dateIso, DateTimeFormatter.ISO_DATE)
                                            }.getOrNull()
                                            if (d == null) "日期待定"
                                            else dev.cao.finch.notify.ReleaseCheckWorker.countdownText(today, d, f.notifyDays)
                                                ?: d.format(DateTimeFormatter.ofPattern("M月d日"))
                                        }
                                        FinchBadge(countdown)
                                    }
                                }
                                Spacer(Modifier.height(FinchSpace.xs))
                                Text(
                                    "每天检查一次，发售前 3 天发通知（省电模式下可能延迟）",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (s.note?.startsWith("缓存") == true) {
                        item(key = "cache_note") {
                            Text(
                                "正在刷新在线数据…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = FinchSpace.xs),
                            )
                        }
                    }
                    if (filtered.isEmpty() && q.isNotEmpty()) {
                        item(key = "no_match") {
                            FinchEmptyState(
                                icon = Icons.Filled.Search,
                                title = "没有匹配「$query」",
                                message = "试试游戏名的英文原名，或换一个数据源。",
                            )
                        }
                    } else if (filtered.isEmpty() && s.entries.isEmpty()) {
                        item(key = "no_data") {
                            FinchEmptyState(
                                icon = Icons.Filled.Event,
                                title = "没有符合条件的大作发售信息",
                                message = "数据来自游民星空 / eShop / Steam / Bangumi，点右上角刷新重试。",
                                actionLabel = "刷新",
                                onAction = { upcomingViewModel.refresh() },
                            )
                        }
                    }
                    val now = LocalDate.now()
                    val buckets = filtered.groupBy { e ->
                        val d = e.date
                        when {
                            d != null && d <= now -> "recent"
                            d != null && d.year == now.year -> "coming"
                            else -> "later"
                        }
                    }
                    listOf(
                        "recent" to "本年度已发售",
                        "coming" to "本年度待发售",
                        "later" to "更远期",
                    ).forEach { (key, label) ->
                        val items = buckets[key].orEmpty()
                        if (items.isNotEmpty()) {
                            item(key = "header_$key") {
                                FinchSectionTitle(label, trailing = "${items.size} 款")
                            }
                            items.forEach { entry ->
                                item(
                                    key = "entry_" + (entry.source + ":" +
                                        (entry.bangumiId?.toString() ?: entry.steamAppId?.toString() ?: entry.name))
                                ) {
                                    UpcomingRow(
                                        entry = entry,
                                        followed = upcomingViewModel.followKeyOf(entry) in followKeys,
                                        onToggleFollow = { upcomingViewModel.toggleFollow(entry) },
                                        onAdd = {
                                            if (entry.platforms.isEmpty()) {
                                                platformPickerFor = entry
                                            } else {
                                                upcomingViewModel.addToLibrary(entry, emptySet()) {}
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    FinchLoading(label = "正在拉取发售日程…")
                }
            }
        }
    }

    platformPickerFor?.let { entry ->
        PlatformPickerSheet(
            entryName = entry.name,
            onConfirm = { plats ->
                upcomingViewModel.addToLibrary(entry, plats) {}
                platformPickerFor = null
            },
            onDismiss = { platformPickerFor = null },
        )
    }
}

@Composable
private fun UpcomingRow(
    entry: UpcomingViewModel.UpcomingEntry,
    followed: Boolean,
    onToggleFollow: () -> Unit,
    onAdd: () -> Unit,
) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        style = GlassStyle.Tile,
        shape = FinchShapes.tile,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FinchSpace.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GameCover(
                url = entry.coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(52.dp)
                    .clip(FinchShapes.thumb),
                requestSize = 104,
                placeholder = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Event,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
            )
            Spacer(Modifier.width(FinchSpace.md))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                entry.nameCn?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (entry.date != null) {
                        val days = ChronoUnit.DAYS.between(LocalDate.now(), entry.date)
                        val dateText = entry.date.format(DateTimeFormatter.ofPattern("M月d日"))
                        val daysText = when {
                            days == 0L -> "今天发售"
                            days == 1L -> "明天"
                            days in 2..30 -> "${days}天后"
                            else -> dateText
                        }
                        FinchBadge(
                            daysText,
                            tint = if (days in 0..7) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(FinchSpace.sm))
                    } else if (entry.releaseText != null) {
                        Text(
                            entry.releaseText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(FinchSpace.sm))
                    }
                    val plats = entry.platforms.filter { it != Platform.Multi }
                    if (plats.isNotEmpty()) {
                        Text(
                            GameRepository.labelFor(plats),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(Modifier.width(FinchSpace.xs))
            if (entry.added) {
                FinchBadge("已在库")
            } else {
                FinchIconButton(
                    icon = if (followed) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = stringResource(if (followed) R.string.a11y_unfollow else R.string.a11y_follow),
                    onClick = onToggleFollow,
                    ghost = true,
                    tint = if (followed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FinchButton(
                    text = "入库",
                    icon = Icons.Filled.Add,
                    variant = FinchButtonVariant.Glass,
                    compact = true,
                    onClick = onAdd,
                )
            }
        }
    }
}

/** 无平台信息时的勾选底部弹窗 */
@Composable
private fun PlatformPickerSheet(
    entryName: String,
    onConfirm: (Set<Platform>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(setOf<Platform>()) }
    FinchSheet(
        title = "「$entryName」是什么平台？",
        subtitle = "可多选，选完加入游戏库。",
        onDismiss = onDismiss,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(FinchSpace.sm)) {
            listOf(Platform.PC, Platform.SWITCH, Platform.PS).forEach { p ->
                FinchChip(
                    label = platformLabel(p),
                    selected = p in selected,
                    onClick = {
                        selected = if (p in selected) selected - p else selected + p
                    },
                )
            }
        }
        Spacer(Modifier.height(FinchSpace.xl))
        Row(
            horizontalArrangement = Arrangement.spacedBy(FinchSpace.sm),
            modifier = Modifier.align(Alignment.End),
        ) {
            FinchButton(text = "取消", variant = FinchButtonVariant.Ghost, onClick = onDismiss)
            FinchButton(
                text = "加入游戏库",
                enabled = selected.isNotEmpty(),
                onClick = { onConfirm(selected) },
            )
        }
        Spacer(Modifier.height(FinchSpace.sm))
    }
}
