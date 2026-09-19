package dev.cao.finch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import dev.cao.finch.data.GameRepository
import dev.cao.finch.data.Platform
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpcomingScreen(
    upcomingViewModel: UpcomingViewModel = viewModel(),
    backdrop: com.kyant.backdrop.Backdrop? = null,
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("发售日程", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "数据源：游民星空 / eShop / Steam / Bangumi（可切换，搜索不受大作过滤）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { upcomingViewModel.refresh() }) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("刷新")
            }
        }

        // 数据源切换（横向滚动，防溢出）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UpcomingViewModel.Source.entries.forEach { s ->
                androidx.compose.material3.FilterChip(
                    selected = source == s,
                    onClick = { upcomingViewModel.setSource(s) },
                    label = { Text(s.label) },
                )
            }
        }

        // 搜索框（对发售日程里的游戏按名字过滤）
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("搜索发售日程…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // 我的关注（置顶，有关注才出现）
        if (follows.isNotEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth(), backdrop = backdrop) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "我的关注（${follows.size}）",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    follows.take(10).forEach { f ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
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
                            Spacer(Modifier.width(8.dp))
                            val countdown = remember(f.dateIso) {
                                val d = runCatching {
                                    LocalDate.parse(f.dateIso, DateTimeFormatter.ISO_DATE)
                                }.getOrNull()
                                if (d == null) "日期待定"
                                else dev.cao.finch.notify.ReleaseCheckWorker.countdownText(LocalDate.now(), d, f.notifyDays)
                                    ?: d.format(DateTimeFormatter.ofPattern("M月d日"))
                            }
                            Text(
                                countdown,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                            )
                        }
                    }
                    Text(
                        "每天检查一次，发售前 3 天发通知（省电模式下可能延迟）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        val s = state
        when (s) {
            is UpcomingViewModel.LoadState.Failed -> {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("加载失败", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                        Text(s.message, style = MaterialTheme.typography.bodySmall)
                        if (s.hadCache) Text("下拉前先展示的是上次的缓存数据", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { upcomingViewModel.refresh() }) { Text("重试") }
                    }
                }
            }
            is UpcomingViewModel.LoadState.Ready -> {
                if (s.note == "缓存") {
                    Text(
                        "正在刷新在线数据…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val q = query.trim().lowercase()
                // 搜索走全量（不过滤大作），这样小作也能搜到
                val searchPool = if (q.isEmpty()) s.entries else s.allEntries
                val filtered = if (q.isEmpty()) searchPool else searchPool.filter {
                    it.name.lowercase().contains(q) || (it.nameCn?.lowercase()?.contains(q) == true)
                }
                if (filtered.isEmpty() && q.isEmpty() && s.entries.isEmpty()) {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("没有符合条件的大作发售信息")
                            Spacer(Modifier.height(4.dp))
                            Text("数据来自 游民星空 / eShop / Steam / Bangumi，点右上角刷新重试。", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 120.dp),
                ) {
                    val now = LocalDate.now()
                    if (filtered.isEmpty() && q.isNotEmpty()) {
                        item {
                            Text(
                                "没有匹配「$query」的发售日程",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                            )
                        }
                    }
                    val buckets = filtered.groupBy { e ->
                        val d = e.date
                        when {
                            d != null && d <= now -> "recent"            // 已发售（含今天）
                            d != null && d.year == now.year -> "coming"  // 本年度剩余
                            else -> "later"                              // 更远期/无日期
                        }
                    }
                    // 段标题 + 条目（全年不截断）
                    listOf("recent" to "本年度已发售", "coming" to "本年度待发售", "later" to "更远期").forEach { (key, label) ->
                        val items = buckets[key].orEmpty()
                        if (items.isNotEmpty()) {
                            item(key = "header_$key") {
                                Text(
                                    label + "（${items.size}）",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            items.forEach { entry ->
                                item(key = "entry_" + (entry.source + ":" + (entry.bangumiId?.toString() ?: entry.steamAppId?.toString() ?: entry.name))) {
                                    UpcomingRow(
                                        entry = entry,
                                        backdrop = backdrop,
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
                    CircularProgressIndicator()
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
    backdrop: com.kyant.backdrop.Backdrop?,
    followed: Boolean,
    onToggleFollow: () -> Unit,
    onAdd: () -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth(), backdrop = backdrop) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                if (entry.coverUrl != null) {
                    AsyncImage(
                        model = entry.coverUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Event, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(entry.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    entry.nameCn?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
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
                            Text(
                                daysText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (days in 0..7) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(8.dp))
                        } else if (entry.releaseText != null) {
                            Text(
                                entry.releaseText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        val plats = entry.platforms.filter { it != Platform.Multi }
                        if (plats.isNotEmpty()) {
                            Text(
                                GameRepository.labelFor(plats),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (entry.added) {
                Text(
                    "已在库",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onToggleFollow) {
                        Text(if (followed) "★" else "☆", maxLines = 1)
                    }
                    Button(onClick = onAdd) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text("入库")
                    }
                }
            }
        }
    }
}

/** 无平台信息时的勾选底部弹窗 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlatformPickerSheet(
    entryName: String,
    onConfirm: (Set<Platform>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(setOf<Platform>()) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("「$entryName」是什么平台？", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Platform.PC, Platform.SWITCH, Platform.PS).forEach { p ->
                    androidx.compose.material3.FilterChip(
                        selected = p in selected,
                        onClick = {
                            selected = if (p in selected) selected - p else selected + p
                        },
                        label = { Text(platformLabel(p)) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.End)) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Button(enabled = selected.isNotEmpty(), onClick = { onConfirm(selected) }) { Text("加入游戏库") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
