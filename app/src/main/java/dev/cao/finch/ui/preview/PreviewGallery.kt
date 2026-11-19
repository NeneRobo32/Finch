package dev.cao.finch.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.cao.finch.data.BucketTotal
import dev.cao.finch.data.DailyTotal
import dev.cao.finch.data.Platform
import dev.cao.finch.data.PlatformTotal
import dev.cao.finch.data.TopGameRow
import dev.cao.finch.ui.HeatmapCard
import dev.cao.finch.ui.HeroTotalCard
import dev.cao.finch.ui.MonthReportCard
import dev.cao.finch.ui.PlatformCard
import dev.cao.finch.ui.RhythmCard
import dev.cao.finch.ui.StatsMode
import dev.cao.finch.ui.TopGamesCard
import dev.cao.finch.ui.design.FinchBackdrop
import dev.cao.finch.ui.design.FinchBadge
import dev.cao.finch.ui.design.FinchButton
import dev.cao.finch.ui.design.FinchButtonVariant
import dev.cao.finch.ui.design.FinchCard
import dev.cao.finch.ui.design.FinchCheckbox
import dev.cao.finch.ui.design.FinchChip
import dev.cao.finch.ui.design.FinchCollapsibleGroup
import dev.cao.finch.ui.design.FinchDialog
import dev.cao.finch.ui.design.FinchEmptyState
import dev.cao.finch.ui.design.FinchHeader
import dev.cao.finch.ui.design.FinchIconButton
import dev.cao.finch.ui.design.FinchInsetDivider
import dev.cao.finch.ui.design.FinchListRow
import dev.cao.finch.ui.design.FinchLoading
import dev.cao.finch.ui.design.FinchProgressBar
import dev.cao.finch.ui.design.FinchSectionTitle
import dev.cao.finch.ui.design.FinchSegmented
import dev.cao.finch.ui.design.FinchSheet
import dev.cao.finch.ui.design.FinchSlider
import dev.cao.finch.ui.design.FinchSpace
import dev.cao.finch.ui.design.FinchStars
import dev.cao.finch.ui.design.FinchStatTile
import dev.cao.finch.ui.design.FinchSwitch
import dev.cao.finch.ui.design.FinchTextField
import dev.cao.finch.ui.design.GlassStyle
import dev.cao.finch.ui.design.GlassSurface
import dev.cao.finch.ui.design.InsetSurface
import dev.cao.finch.ui.design.LocalFinchBackdrop
import dev.cao.finch.ui.theme.BackgroundStyle
import dev.cao.finch.ui.theme.FinchShapes
import dev.cao.finch.ui.theme.LocalGlassEnabled
import dev.cao.finch.ui.theme.finchBundle
import java.time.YearMonth

/**
 * 预览画廊（v0.30）：把设计系统的组件和带假数据的统计卡摆在一页里，
 * 真机上滚一遍就能看出改动有没有砸。
 *
 * 为什么不做 `@Preview`：Compose 的 `@Preview` 只能靠 Android Studio / layoutlib 渲染，
 * 这台开发机没装 Studio，改一行 UI 就得装机才能看，成本太高。
 * 这个画廊是本 App 内的一个普通页面，直接在手机上打开即可，且渲染的就是真实组件
 * （不是抽出来的近似副本），所以颜色、玻璃、动画、字重全是线上那一套。
 *
 * 打开路径：设置 → 关于 → 预览画廊。
 */
@Composable
fun PreviewGallery(onClose: () -> Unit) {
    val glassEnabledNow = LocalGlassEnabled.current
    // 自带一份采样源：画廊整页覆盖在设置页之上，若沿用外层的 backgroundBackdrop，
    // 玻璃采到的仍是被盖住的氛围光，位置对不上会出现「透出别处内容」的错位感。
    val backdrop = rememberLayerBackdrop()
    var glass by remember { mutableStateOf(glassEnabledNow) }
    var showDialog by remember { mutableStateOf(false) }
    var showSheet by remember { mutableStateOf(false) }
    var chip by remember { mutableStateOf(1) }
    var mode by remember { mutableStateOf(StatsMode.MONTH) }
    var text by remember { mutableStateOf("") }
    var toggle by remember { mutableStateOf(true) }
    var check by remember { mutableStateOf(true) }
    var slider by remember { mutableStateOf(0.35f) }
    var stars by remember { mutableStateOf<Int?>(4) }

    CompositionLocalProvider(
        LocalFinchBackdrop provides backdrop,
        LocalGlassEnabled provides glass,
    ) {
        Box(Modifier.fillMaxSize()) {
            // 背景层必须在玻璃面板之下并且录进 backdrop，玻璃才有东西可采样
            Box(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop),
            ) {
                FinchBackdrop(
                    style = BackgroundStyle.AURORA,
                    imagePath = "",
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = FinchSpace.screen),
            ) {
                Spacer(Modifier.height(FinchSpace.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FinchHeader(
                        title = "预览画廊",
                        subtitle = "组件与统计卡的真实渲染 · 改 UI 自查用",
                        modifier = Modifier.weight(1f),
                    )
                    FinchIconButton(
                        icon = Icons.Filled.Close,
                        contentDescription = "关闭预览画廊",
                        onClick = onClose,
                    )
                }

                Spacer(Modifier.height(FinchSpace.md))
                FinchCard(title = "渲染开关", icon = Icons.Filled.Info) {
                    FinchListRow(
                        title = "玻璃化",
                        subtitle = "关掉即实色面板，用来对比两种观感",
                        trailing = {
                            FinchSwitch(checked = glass, onCheckedChange = { glass = it })
                        },
                    )
                    FinchInsetDivider()
                    Text(
                        "主题：${finchBundle().let { if (it.isDark) "深色" else "浅色" }} · " +
                            dev.cao.finch.ui.theme.LocalFinchTheme.current.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(FinchSpace.md))
                ColorSection()

                Spacer(Modifier.height(FinchSpace.md))
                TypeSection()

                Spacer(Modifier.height(FinchSpace.md))
                FinchCard(title = "控件", icon = Icons.Filled.Star) {
                    Row(horizontalArrangement = Arrangement.spacedBy(FinchSpace.sm)) {
                        FinchButton(text = "主操作", onClick = {})
                        FinchButton(text = "玻璃", onClick = {}, variant = FinchButtonVariant.Glass)
                    }
                    Spacer(Modifier.height(FinchSpace.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(FinchSpace.sm)) {
                        FinchButton(text = "次要", onClick = {}, variant = FinchButtonVariant.Ghost)
                        FinchButton(text = "删除", onClick = {}, variant = FinchButtonVariant.Danger)
                    }
                    Spacer(Modifier.height(FinchSpace.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(FinchSpace.sm)) {
                        FinchButton(text = "禁用", onClick = {}, enabled = false)
                        FinchButton(text = "同步中", onClick = {}, loading = true)
                        FinchButton(
                            text = "紧凑",
                            onClick = {},
                            compact = true,
                            icon = Icons.Filled.Add,
                        )
                    }

                    Spacer(Modifier.height(FinchSpace.lg))
                    FinchSectionTitle("小标签", trailing = "3")
                    Row(horizontalArrangement = Arrangement.spacedBy(FinchSpace.sm)) {
                        FinchChip("全部", selected = chip == 0, onClick = { chip = 0 })
                        FinchChip("在玩", selected = chip == 1, onClick = { chip = 1 })
                        FinchChip("已通关", selected = chip == 2, onClick = { chip = 2 })
                    }

                    Spacer(Modifier.height(FinchSpace.lg))
                    FinchSegmented(
                        options = listOf(StatsMode.MONTH, StatsMode.YEAR),
                        selected = mode,
                        onSelect = { mode = it },
                        label = { if (it == StatsMode.MONTH) "月" else "年" },
                    )

                    Spacer(Modifier.height(FinchSpace.lg))
                    FinchTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = "输入框",
                        placeholder = "试试键盘弹起时的表现",
                        leadingIcon = Icons.Filled.Favorite,
                    )

                    Spacer(Modifier.height(FinchSpace.lg))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("开关", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        FinchSwitch(checked = toggle, onCheckedChange = { toggle = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("多选", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        FinchCheckbox(checked = check, onCheckedChange = { check = it })
                    }
                    Spacer(Modifier.height(FinchSpace.sm))
                    FinchSlider(
                        value = slider,
                        onValueChange = { slider = it },
                        valueRange = 0f..1f,
                    )
                    Spacer(Modifier.height(FinchSpace.sm))
                    FinchProgressBar(progress = slider)

                    Spacer(Modifier.height(FinchSpace.lg))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(FinchSpace.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FinchBadge("徽章")
                        FinchBadge("已通关")
                        FinchStars(rating = stars, onRate = { stars = it })
                    }
                    Spacer(Modifier.height(FinchSpace.md))
                    FinchLoading()
                }

                Spacer(Modifier.height(FinchSpace.md))
                ContainerSection(
                    onDialog = { showDialog = true },
                    onSheet = { showSheet = true },
                )

                Spacer(Modifier.height(FinchSpace.md))
                Text(
                    "统计卡（假数据，真实组件）",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(FinchSpace.sm))
                HeroTotalCard(
                    totalMs = Demo.totalMs,
                    prevMs = Demo.totalMs - 7L * 3600_000,
                    steamMin = 6240,
                    mode = mode,
                    goalMin = 20L * 3600_000,
                )
                Spacer(Modifier.height(FinchSpace.md))
                MonthReportCard(
                    distinctGames = 4,
                    daily = Demo.daily,
                    topGames = Demo.top,
                    mode = mode,
                )
                Spacer(Modifier.height(FinchSpace.md))
                PlatformCard(platforms = Demo.platforms, totalMs = Demo.totalMs)
                Spacer(Modifier.height(FinchSpace.md))
                TopGamesCard(topGames = Demo.top)
                Spacer(Modifier.height(FinchSpace.md))
                HeatmapCard(daily = Demo.daily, mode = mode, anchorMonth = YearMonth.now())
                Spacer(Modifier.height(FinchSpace.md))
                RhythmCard(
                    weekday = Demo.weekday,
                    months = Demo.months,
                    hours = Demo.hours,
                    mode = mode,
                )

                Spacer(Modifier.height(FinchSpace.bottomBarClearance))
            }

            if (showDialog) {
                FinchDialog(
                    title = "弹窗预览",
                    subtitle = "弹层是独立窗口，玻璃会被关掉走实底",
                    onDismiss = { showDialog = false },
                    onConfirm = { showDialog = false },
                ) {
                    Text(
                        "弹窗内的按钮、标签都应该是实色底，不该出现半透明玻璃。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(FinchSpace.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(FinchSpace.sm)) {
                        FinchChip("标签", selected = true, onClick = {})
                        FinchButton(text = "按钮", onClick = {}, compact = true)
                    }
                }
            }
            if (showSheet) {
                FinchSheet(title = "底部面板", onDismiss = { showSheet = false }) {
                    Text("面板内容", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(FinchSpace.lg))
                }
            }
        }
    }
}

/** 主题色板：Material 配色 + 玻璃 + 环境光，一眼看出换了主题后哪里塌了 */
@Composable
private fun ColorSection() {
    val bundle = finchBundle()
    val scheme = bundle.scheme
    val swatches = listOf(
        "primary" to scheme.primary,
        "secondary" to scheme.secondary,
        "tertiary" to scheme.tertiary,
        "surface" to scheme.surface,
        "surfaceVariant" to scheme.surfaceVariant,
        "outline" to scheme.outline,
        "onSurface" to scheme.onSurface,
        "accentText" to bundle.accentText,
        "glowA" to bundle.ambient.glowA,
        "glowB" to bundle.ambient.glowB,
        "glowC" to bundle.ambient.glowC,
        "glassEdge" to bundle.glass.edge,
    )
    FinchCard(title = "色板", icon = Icons.Filled.Check) {
        swatches.chunked(4).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(FinchSpace.sm),
            ) {
                row.forEach { (name, color) ->
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clip(FinchShapes.thumb)
                                .background(color),
                        )
                        Spacer(Modifier.height(FinchSpace.xxs))
                        Text(
                            name,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(FinchSpace.sm))
        }
        Text(
            "accentText 是小字专用的主题色（A2 对比度修复），按钮底色仍用 primary。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 排版：正文与小字在玻璃底上最容易糊，逐个看一遍 */
@Composable
private fun TypeSection() {
    val typography = MaterialTheme.typography
    FinchCard(title = "排版", icon = Icons.Filled.Info) {
        Text("大数字 displayMedium", style = typography.displayMedium, maxLines = 1)
        Text("页头 headlineMedium", style = typography.headlineMedium, maxLines = 1)
        Text("标题 titleLarge", style = typography.titleLarge, maxLines = 1)
        Text("小节 titleSmall SemiBold", style = typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text("正文 bodyMedium，一段稍长的中文用来判断行高与字重是否压得住玻璃底。", style = typography.bodyMedium)
        Text(
            "说明 labelMedium",
            style = typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "小字 labelSmall 11sp",
            style = typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(FinchSpace.sm))
        Text(
            "12:34 · 123.5 小时",
            style = typography.bodyMedium,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "计时跳秒时，数字宽度变化会让整行左右抖；真机上盯这一行就知道要不要上 tnum。",
            style = typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 容器与弹层：卡片、统计格、列表行、空态、折叠组、玻璃四档 */
@Composable
private fun ContainerSection(onDialog: () -> Unit, onSheet: () -> Unit) {
    FinchCard(title = "容器", icon = Icons.Filled.Star) {
        Row(horizontalArrangement = Arrangement.spacedBy(FinchSpace.sm)) {
            FinchStatTile(label = "总时长", value = "123.5h", modifier = Modifier.weight(1f))
            FinchStatTile(label = "游戏", value = "42", modifier = Modifier.weight(1f))
            FinchStatTile(label = "连续", value = "7 天", modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(FinchSpace.md))
        FinchListRow(
            title = "列表行",
            subtitle = "副标题走 labelSmall",
            meta = "12.3h",
            leading = {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(FinchShapes.thumb)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("F", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            },
            trailing = { FinchBadge("在玩") },
            onClick = {},
        )
        FinchInsetDivider(startIndent = 40.dp)
        InsetSurface(modifier = Modifier.fillMaxWidth(), onClick = {}) {
            Box(Modifier.padding(FinchSpace.lg)) {
                Text("InsetSurface：卡片内的次级面", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    Spacer(Modifier.height(FinchSpace.md))
    FinchCard(title = "玻璃四档", icon = Icons.Filled.Info) {
        GlassStyle.entries.forEach { style ->
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = FinchSpace.xs),
                style = style,
                shape = FinchShapes.card,
            ) {
                Box(Modifier.padding(FinchSpace.lg)) {
                    Text(
                        "${style.name} · 模糊 ${style.blurDp}dp · 折射 ${style.refractionAmountDp}dp",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(FinchSpace.md))
    FinchCard(title = "弹层与空态", icon = Icons.Filled.Info) {
        Row(horizontalArrangement = Arrangement.spacedBy(FinchSpace.sm)) {
            FinchButton(text = "弹窗", onClick = onDialog, variant = FinchButtonVariant.Glass)
            FinchButton(text = "底部面板", onClick = onSheet, variant = FinchButtonVariant.Glass)
        }
        Spacer(Modifier.height(FinchSpace.md))
        FinchEmptyState(
            icon = Icons.Filled.Add,
            title = "空状态",
            message = "没数据时的样子，图标 + 标题 + 说明 + 可选主操作。",
            actionLabel = "主操作",
            onAction = {},
        )
    }

    Spacer(Modifier.height(FinchSpace.md))
    FinchCollapsibleGroup(
        title = "折叠分组",
        subtitle = "展开状态用 rememberSaveable 保存",
        icon = Icons.Filled.Info,
    ) {
        Text("展开后的内容。", style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * 假数据：故意造得有点脏（有的天没数据、有的游戏没封面、Steam 时长有的空），
 * 因为真实数据的边界才是渲染最容易塌的地方。
 */
private object Demo {
    private fun h(hours: Int, minutes: Int = 0) = hours * 3_600_000L + minutes * 60_000L

    val totalMs: Long = h(37, 25)

    val top: List<TopGameRow> = listOf(
        TopGameRow(1L, "艾尔登法环", Platform.PC, null, h(18, 40), 24L, 5_124L),
        TopGameRow(2L, "塞尔达传说 王国之泪", Platform.SWITCH, null, h(9, 15), 12L, null),
        TopGameRow(3L, "最终幻想 VII 重生", Platform.PS, null, h(5, 50), 8L, null),
        TopGameRow(4L, "哈迪斯 II", Platform.PC, null, h(2, 30), 5L, 320L),
        TopGameRow(5L, "斯普拉遁 3", Platform.SWITCH, null, h(1, 10), 3L, null),
    )

    val platforms: List<PlatformTotal> = listOf(
        PlatformTotal(Platform.PC, h(21, 10)),
        PlatformTotal(Platform.SWITCH, h(10, 25)),
        PlatformTotal(Platform.PS, h(5, 50)),
        PlatformTotal(Platform.Multi, 0L),
    )

    val daily: List<DailyTotal> = run {
        val month = YearMonth.now()
        (1..month.lengthOfMonth()).map { day ->
            // 每三天里有两天玩了，剩下的空着——热力图与「最长连续」都靠这个才看得出效果
            val ms = if (day % 3 == 0) 0L else h(day % 4, (day * 7) % 60)
            DailyTotal(month.atDay(day).toString(), ms)
        }
    }

    val weekday: List<BucketTotal> = (0..6).map { BucketTotal(it, h(it % 3 + 1)) }

    val months: List<BucketTotal> = (1..12).map { BucketTotal(it, h(it % 5 * 2)) }

    val hours: List<BucketTotal> = (0..23).map { BucketTotal(it, if (it in 20..23) h(2) else h(0, 40)) }

}
