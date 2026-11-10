package dev.cao.finch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.cao.finch.R
import dev.cao.finch.data.GameRepository
import dev.cao.finch.data.Platform
import dev.cao.finch.ui.design.FinchBadge
import dev.cao.finch.ui.design.FinchButton
import dev.cao.finch.ui.design.FinchButtonVariant
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
import dev.cao.finch.ui.design.InsetSurface
import dev.cao.finch.ui.design.finchEnter
import dev.cao.finch.ui.theme.FinchShapes
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 发售日程页（v0.29.1 卡片重排）：
 * 页头 → 数据源胶囊 → 搜索 → 愿望单入口 → 分组大卡片。
 *
 * 愿望单不再是底部 tab，而是本页的子视图：点入口条进去，左上角箭头或系统返回键回到列表。
 */
@Composable
fun UpcomingScreen(upcomingViewModel: UpcomingViewModel = viewModel()) {
    // 愿望单子视图开关。用 rememberSaveable 保存，旋转屏幕后还停在愿望单里
    var showWishlist by rememberSaveable { mutableStateOf(false) }

    if (showWishlist) {
        BackHandler { showWishlist = false }
        WishlistPane(
            upcomingViewModel = upcomingViewModel,
            onBack = { showWishlist = false },
        )
        return
    }

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
            subtitle = "PSN 港服价格 / 游民星空 / eShop 三源聚合",
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

        when (val s = state) {
            is UpcomingViewModel.LoadState.Idle,
            is UpcomingViewModel.LoadState.Loading -> {
                FinchLoading(modifier = Modifier.fillMaxWidth().padding(top = FinchSpace.xxl))
            }

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
                // 搜索时用全量（不过滤大作），否则在当前源的展示列表里找
                val searchPool = if (q.isEmpty()) s.entries else s.allEntries
                val filtered = if (q.isEmpty()) {
                    s.entries
                } else {
                    searchPool.filter { e ->
                        e.name.lowercase().contains(q) || (e.nameCn?.lowercase()?.contains(q) == true)
                    }
                }
                val now = LocalDate.now()
                val buckets = remember(filtered) {
                    filtered.groupBy { e ->
                        val d = e.date
                        when {
                            e.deal -> "deal"
                            d != null && !d.isAfter(now) -> "recent"
                            d != null && d.year == now.year -> "coming"
                            else -> "later"
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(FinchSpace.md),
                    contentPadding = PaddingValues(
                        top = FinchSpace.xs,
                        bottom = FinchSpace.bottomBarClearance,
                    ),
                ) {
                    // 愿望单入口：常驻一条窄条，点进去是本页的子视图（不占底栏 tab）
                    item(key = "wishlist_entry") {
                        WishlistBar(count = follows.size, onClick = { showWishlist = true })
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
                                title = "没有符合条件的发售信息",
                                message = "数据来自 PSN 港服 / 游民星空 / eShop，点右上角刷新重试。",
                                actionLabel = "刷新",
                                onAction = { upcomingViewModel.refresh() },
                            )
                        }
                    }
                    val groups = listOf(
                        "recent" to "本年度已发售",
                        "coming" to "本年度待发售",
                        "deal" to "正在打折",
                        "later" to "更远期",
                    )
                    for ((key, label) in groups) {
                        val items = buckets[key] ?: continue
                        item(key = "header_$key") {
                            val trailing = if (key == "deal") {
                                items.firstNotNullOfOrNull { it.saleEndsAt }?.let { "至" + it.format(DATE_MD) }
                            } else {
                                "${items.size} 款"
                            }
                            FinchSectionTitle(label, trailing = trailing)
                        }
                        items.forEach { entry ->
                            item(key = "entry_" + entry.source + ":" + (entry.bangumiId ?: entry.steamAppId ?: entry.productId ?: entry.name)) {
                                UpcomingCard(
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

/** 愿望单入口条：一行搞定，不再像卡片那样占掉一整屏的高度 */
@Composable
private fun WishlistBar(count: Int, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    InsetSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = FinchShapes.pill,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FinchSpace.md, vertical = FinchSpace.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Star,
                null,
                tint = scheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(FinchSpace.sm))
            Text(
                "愿望单",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(FinchSpace.sm))
            Text(
                if (count == 0) "还没有关注" else "$count 款在关注",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * 商店条目卡（日程页与愿望单共用同一套版式）。
 *
 * 布局：左侧封面（88×118）＋右侧标题/别名/日期与平台；底部一条信息带，左价格、右操作。
 *
 * v0.29.0 的版本把「价格」单独占一整行、操作再占一行，两行之间夹着大片空白，
 * 视觉上像两级台阶；这里合成一条带后，价格和按钮同基线，卡片高度也回到紧凑比例。
 * 星标从底部操作带挪到标题右侧，既是「收藏」语义的常规位置，也不再和入库按钮抢横向空间。
 */
@Composable
internal fun StoreEntryCard(
    name: String,
    nameCn: String?,
    coverUrl: String?,
    date: LocalDate?,
    releaseText: String?,
    platforms: Set<Platform>,
    priceText: String?,
    basePriceText: String?,
    discountText: String?,
    saleEnd: LocalDate?,
    pricePlaceholder: String? = null,
    followed: Boolean? = null,
    onToggleFollow: (() -> Unit)? = null,
    action: @Composable RowScope.() -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val now = LocalDate.now()
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .finchEnter(),
        style = GlassStyle.Tile,
        shape = FinchShapes.card,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FinchSpace.md),
        ) {
            // 标题块对着封面垂直居中，避免右侧内容比封面矮时在底部留一条空
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GameCover(
                    url = coverUrl,
                    contentDescription = stringResource(R.string.a11y_cover, name),
                    modifier = Modifier
                        .size(width = 88.dp, height = 118.dp)
                        .clip(FinchShapes.thumb),
                    requestSize = 320,
                    placeholder = {
                        Box(
                            modifier = Modifier.fillMaxSize().background(scheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Event,
                                null,
                                tint = scheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    },
                )
                Spacer(Modifier.width(FinchSpace.md))
                Column(Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (onToggleFollow != null) {
                            FinchIconButton(
                                icon = if (followed == true) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = stringResource(
                                    if (followed == true) R.string.a11y_unfollow else R.string.a11y_follow,
                                ),
                                onClick = onToggleFollow,
                                ghost = true,
                                tint = if (followed == true) scheme.primary else scheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (!nameCn.isNullOrBlank() && nameCn != name) {
                        Text(
                            nameCn,
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(FinchSpace.sm))
                    StoreMetaLine(date = date, releaseText = releaseText, platforms = platforms)
                }
            }

            // 信息带：左侧价格（含折扣），右侧操作，两者同一基线
            Spacer(Modifier.height(FinchSpace.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    when {
                        priceText != null -> PriceRow(priceText, basePriceText, discountText)
                        pricePlaceholder != null -> Text(
                            pricePlaceholder,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    if (saleEnd != null) {
                        Spacer(Modifier.height(FinchSpace.xxs))
                        Text(
                            saleEndText(saleEnd),
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.width(FinchSpace.sm))
                action()
            }
        }
    }
}

/** 日期徽章 / 发售文案 + 平台，一行内用「·」分隔，比竖着堆两行省一张卡的高度 */
@Composable
private fun StoreMetaLine(date: LocalDate?, releaseText: String?, platforms: Set<Platform>) {
    val scheme = MaterialTheme.colorScheme
    val now = LocalDate.now()
    val plats = platforms - Platform.Multi
    val platText = if (plats.isEmpty()) null else GameRepository.labelFor(plats)
    val hasDatePart = date != null || !releaseText.isNullOrBlank()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FinchSpace.xs),
    ) {
        when {
            date != null -> FinchBadge(
                releaseCountdownText(date, now),
                tint = if (isUrgent(date, now)) scheme.primary else scheme.onSurfaceVariant,
            )

            !releaseText.isNullOrBlank() -> Text(
                releaseText,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (platText != null) {
            if (hasDatePart) {
                Text(
                    "·",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
            Text(
                platText,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun UpcomingCard(
    entry: UpcomingViewModel.UpcomingEntry,
    followed: Boolean,
    onToggleFollow: () -> Unit,
    onAdd: () -> Unit,
) {
    StoreEntryCard(
        name = entry.name,
        nameCn = entry.nameCn,
        coverUrl = entry.coverUrl,
        date = entry.date,
        releaseText = entry.releaseText,
        platforms = entry.platforms,
        priceText = entry.priceText,
        basePriceText = entry.basePriceText,
        discountText = entry.discountText,
        saleEnd = if (entry.deal) entry.saleEndsAt else null,
        followed = followed,
        onToggleFollow = onToggleFollow,
        action = {
            if (entry.added) {
                FinchBadge("已在库")
            } else {
                FinchButton(
                    text = "入库",
                    icon = Icons.Filled.Add,
                    variant = FinchButtonVariant.Glass,
                    compact = true,
                    onClick = onAdd,
                )
            }
        },
    )
}

/** 发售倒计时文案（日程卡与愿望单卡共用） */
internal fun releaseCountdownText(date: LocalDate, now: LocalDate): String {
    val days = ChronoUnit.DAYS.between(now, date)
    return when {
        days == 0L -> "今天发售"
        days == 1L -> "明天"
        days in 2L..30L -> "${days}天后"
        days < 0 -> date.format(DATE_MD) + " 已发售"
        else -> date.format(DATE_MD)
    }
}

/** 7 天内发售用主题色强调 */
internal fun isUrgent(date: LocalDate, now: LocalDate): Boolean =
    ChronoUnit.DAYS.between(now, date) in 0L..7L

/** 价格三件套：现价（打折时用主题色）+ 划线原价 + 折扣角标；空间不足时换行，不截断角标 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PriceRow(priceText: String, basePriceText: String?, discountText: String?) {
    val scheme = MaterialTheme.colorScheme
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FinchSpace.sm),
        verticalArrangement = Arrangement.spacedBy(FinchSpace.xxs),
    ) {
        Text(
            priceText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (discountText != null) scheme.primary else scheme.onSurface,
            maxLines = 1,
        )
        if (basePriceText != null) {
            Text(
                basePriceText,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                textDecoration = TextDecoration.LineThrough,
                maxLines = 1,
            )
        }
        if (discountText != null) {
            FinchBadge(discountText, tint = scheme.primary)
        }
    }
}

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
                    label = GameRepository.shortLabel(p),
                    selected = p in selected,
                    onClick = {
                        selected = if (p in selected) selected - p else selected + p
                    },
                )
            }
        }
        Spacer(Modifier.height(FinchSpace.xl))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            FinchButton(text = "取消", variant = FinchButtonVariant.Ghost, onClick = onDismiss)
            Spacer(Modifier.width(FinchSpace.sm))
            FinchButton(
                text = "加入游戏库",
                enabled = selected.isNotEmpty(),
                onClick = { onConfirm(selected) },
            )
        }
        Spacer(Modifier.height(FinchSpace.sm))
    }
}

private val DATE_MD: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日")

/** 折扣截止文案（日程页与愿望单页共用口径） */
internal fun saleEndText(end: LocalDate): String {
    val days = ChronoUnit.DAYS.between(LocalDate.now(), end)
    val dateText = end.format(DATE_MD)
    return when {
        days < 0 -> "优惠已结束"
        days == 0L -> "优惠今天结束"
        days == 1L -> "优惠明天结束（$dateText）"
        else -> "优惠至$dateText（还剩${days}天）"
    }
}
