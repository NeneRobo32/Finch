package dev.cao.finch.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.outlined.ShoppingBag
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.cao.finch.R
import dev.cao.finch.data.ReleaseFollow
import dev.cao.finch.ui.design.FinchButton
import dev.cao.finch.ui.design.FinchButtonVariant
import dev.cao.finch.ui.design.FinchEmptyState
import dev.cao.finch.ui.design.FinchHeader
import dev.cao.finch.ui.design.FinchIconButton
import dev.cao.finch.ui.design.FinchLoading
import dev.cao.finch.ui.design.FinchSegmented
import dev.cao.finch.ui.design.FinchSpace
import dev.cao.finch.ui.design.GlassStyle
import dev.cao.finch.ui.design.GlassSurface
import dev.cao.finch.ui.design.finchEnter
import dev.cao.finch.ui.theme.FinchShapes
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * 愿望单子视图（v0.29.1）：日程页里点「愿望单」条进来，不再是独立 tab。
 *
 * 只回答三个问题 —— 现在多少钱、打了多少折、什么时候发售 / 折扣什么时候结束。
 * 数据来自 release_follows 的价格快照（离线可看）；右上角刷新会单独拉一次港服数据回写快照，
 * 不受日程列表当前选中的数据源影响。
 */
@Composable
internal fun WishlistPane(
    upcomingViewModel: UpcomingViewModel,
    onBack: () -> Unit,
) {
    val follows by upcomingViewModel.follows.collectAsState()
    val refreshing by upcomingViewModel.wishlistRefreshing.collectAsState()
    val context = LocalContext.current
    var filter by rememberSaveable { mutableStateOf(WishFilter.ALL) }

    val shown = remember(follows, filter) {
        when (filter) {
            WishFilter.ALL -> follows
            WishFilter.DEAL -> follows.filter { it.discountText != null }
            WishFilter.UPCOMING -> follows.filter { it.discountText == null }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = FinchSpace.screen),
    ) {
        Spacer(Modifier.height(FinchSpace.sm))
        // 子视图的返回键放在标题左侧（Android 常规位置），刷新仍在右侧
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FinchIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回日程",
                onClick = onBack,
                ghost = true,
            )
            Spacer(Modifier.width(FinchSpace.xs))
            FinchHeader(
                title = "愿望单",
                subtitle = "PSN 港服价格 / 折扣 / 发售日",
                modifier = Modifier.weight(1f),
                actions = {
                    FinchIconButton(
                        icon = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.a11y_wishlist_refresh),
                        onClick = { upcomingViewModel.refreshWishlist() },
                    )
                },
            )
        }
        Spacer(Modifier.height(FinchSpace.md))
        FinchSegmented(
            options = WishFilter.entries.toList(),
            selected = filter,
            onSelect = { filter = it },
            label = { it.label },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(FinchSpace.md))

        if (refreshing) {
            FinchLoading(modifier = Modifier.fillMaxWidth().padding(vertical = FinchSpace.sm))
        }

        when {
            follows.isEmpty() && !refreshing -> {
                FinchEmptyState(
                    icon = Icons.Filled.StarBorder,
                    title = "愿望单还是空的",
                    message = "在日程页点卡片右边的 ☆ 关注想买的游戏，价格、折扣和发售日会汇总到这里。",
                    modifier = Modifier.padding(top = FinchSpace.sm),
                )
            }

            shown.isEmpty() -> {
                FinchEmptyState(
                    icon = Icons.Outlined.ShoppingBag,
                    title = "这个分类下没有游戏",
                    message = when (filter) {
                        WishFilter.DEAL -> "关注的游戏现在都没在打折，换个分类看看。"
                        else -> "关注的游戏都在打折，换个分类看看。"
                    },
                    modifier = Modifier.padding(top = FinchSpace.sm),
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(FinchSpace.md),
                    contentPadding = PaddingValues(
                        top = FinchSpace.xs,
                        bottom = FinchSpace.bottomBarClearance,
                    ),
                ) {
                    item(key = "summary") {
                        WishSummary(follows)
                    }
                    items(
                        count = shown.size,
                        key = { i -> "wish_" + shown[i].key },
                    ) { i ->
                        WishCard(
                            follow = shown[i],
                            onUnfollow = { upcomingViewModel.toggleFollowByKey(shown[i].key) },
                            onOpenStore = {
                                val url = "https://store.playstation.com/zh-hans-hk/product/" + shown[i].productId
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, url.toUri())
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** 愿望单分类：全部 / 打折中 / 待打折（原价或未打折） */
private enum class WishFilter(val label: String) {
    ALL("全部"), DEAL("打折中"), UPCOMING("待打折"),
}

/** 顶部汇总：关注数 / 打折数 / 最大折扣，压成一条窄带，不抢列表首屏 */
@Composable
private fun WishSummary(follows: List<ReleaseFollow>) {
    val scheme = MaterialTheme.colorScheme
    val deals = follows.count { it.discountText != null }
    val best = follows.mapNotNull { it.discountText }.maxByOrNull { discountValue(it) }
    GlassSurface(
        modifier = Modifier.fillMaxWidth().finchEnter(),
        style = GlassStyle.Tile,
        shape = FinchShapes.card,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(FinchSpace.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(FinchSpace.md),
            ) {
                SummaryCell("关注", "${follows.size}", Modifier.weight(1f))
                SummaryCell("打折中", "$deals", Modifier.weight(1f))
                SummaryCell("最大折扣", best ?: "—", Modifier.weight(1f), highlight = best != null)
            }
            Spacer(Modifier.height(FinchSpace.sm))
            Text(
                "价格随日程刷新更新，离线也能看上次同步到的价",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SummaryCell(label: String, value: String, modifier: Modifier, highlight: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (highlight) scheme.primary else scheme.onSurface,
            maxLines = 1,
        )
    }
}

/**
 * 愿望单卡片：复用日程页的 [StoreEntryCard]，两页版式一致 —— 同一个游戏在两页看到的价是同一个快照。
 * 这里只额外提供「取消关注」和「商店」两个动作。
 */
@Composable
private fun WishCard(
    follow: ReleaseFollow,
    onUnfollow: () -> Unit,
    onOpenStore: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val date = parseIso(follow.dateIso)
    val saleEnd = parseIso(follow.saleEndIso)
    StoreEntryCard(
        name = follow.name,
        nameCn = null,
        coverUrl = follow.coverUrl,
        date = date,
        // 老关注记录可能没有日期，给一句文案占位，免得标题下面空一块
        releaseText = if (date == null) "发售日待定" else null,
        platforms = emptySet(),
        priceText = follow.priceText,
        basePriceText = follow.basePriceText,
        discountText = follow.discountText,
        saleEnd = saleEnd,
        pricePlaceholder = if (follow.priceText == null) "价格待同步" else null,
        followed = true,
        onToggleFollow = onUnfollow,
        action = {
            if (follow.productId != null) {
                // 不带图标：卡片右列只有 100dp 出头，图标会把按钮顶到折行
                FinchButton(
                    text = "商店",
                    variant = FinchButtonVariant.Glass,
                    compact = true,
                    onClick = onOpenStore,
                )
            } else {
                // 老关注记录没有 productId，只能取消关注，给个静态提示占位
                Text(
                    "无商店链接",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        },
    )
}

/** "-70%" → 70（排序用，解析失败按 0） */
private fun discountValue(text: String): Int =
    text.filter { it.isDigit() }.toIntOrNull() ?: 0

private fun parseIso(iso: String?): LocalDate? {
    if (iso.isNullOrBlank()) return null
    return try {
        LocalDate.parse(iso)
    } catch (e: DateTimeParseException) {
        null
    }
}
