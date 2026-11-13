package dev.cao.finch.ui.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.cao.finch.ui.theme.FinchShapes
import dev.cao.finch.ui.theme.LocalGlassEnabled
import dev.cao.finch.ui.theme.finchBundle
import dev.cao.finch.ui.theme.glassPalette

/**
 * 容器组件（2026-10 UI 重构）：
 * 卡片 / 页头 / 分组标题 / 空状态 / 弹窗 / 底部弹层 / 菜单 / Snackbar，
 * 全部共用同一套圆角、玻璃层级与内边距，屏幕只组合，不再自己画卡片。
 */

/**
 * 内容卡：玻璃底 + 可选标题行（图标 + 标题 + 右侧操作）。
 * @param style GlassStyle.Card 默认；需要更轻的列表卡用 Tile
 * @param enterOrder 进场动画的排序（同屏多张卡依次浮现）；null = 不参与错开，一起出现
 * @param animateIn 是否播进场动画；弹窗等需要绝对静止的场合可置 false
 */
@Composable
fun FinchCard(
    modifier: Modifier = Modifier,
    style: GlassStyle = GlassStyle.Card,
    title: String? = null,
    subtitle: String? = null,
    icon: ImageVector? = null,
    titleExtra: String? = null,
    titleExtraColor: Color = MaterialTheme.colorScheme.primary,
    action: @Composable (RowScope.() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(FinchSpace.xl, FinchSpace.lg),
    enterOrder: Int? = null,
    animateIn: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassSurface(
        modifier = modifier.then(
            Modifier.finchEnter(order = enterOrder ?: 0, enabled = animateIn),
        ),
        style = style,
    ) {
        Column(Modifier.padding(contentPadding)) {
            if (title != null) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (icon != null) {
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(FinchShapes.thumb)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                        }
                        Spacer(Modifier.width(FinchSpace.md))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (subtitle != null) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (titleExtra != null) {
                        Text(
                            titleExtra,
                            style = MaterialTheme.typography.labelSmall,
                            color = titleExtraColor,
                        )
                    }
                    action?.let {
                        Spacer(Modifier.width(FinchSpace.xs))
                        it()
                    }
                }
                Spacer(Modifier.height(FinchSpace.md))
            }
            content()
        }
    }
}

/** 页头：大标题 + 一行说明 + 右侧操作（所有 tab 页统一） */
@Composable
fun FinchHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable (RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    // 2026-11 UI 审查：页头副标题 11sp 在玻璃底上偏虚，升到 labelMedium
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        actions?.let { Row(verticalAlignment = Alignment.CenterVertically, content = it) }
    }
}

/**
 * 分组标题：列表内的段落标题（如「正在玩」「本月已发售」）。
 *
 * 2026-11 UI 审查 D1：原来 14sp + onSurfaceVariant + trailing 再降 alpha，在玻璃上跟卡片正文同级，
 * 分段感被削弱。现在标题用 onSurface + SemiBold（字号不变），右侧计数改成 [FinchBadge] 小胶囊。
 */
@Composable
fun FinchSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = FinchSpace.sm, bottom = FinchSpace.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            FinchBadge(trailing)
        }
    }
}

/** 统计小格：标签 + 自适应单行数字（详情页三格 / 总结页四格） */
@Composable
fun FinchStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    style: GlassStyle = GlassStyle.Tile,
    enterOrder: Int = 0,
    animateIn: Boolean = true,
) {
    GlassSurface(
        modifier = modifier.then(Modifier.finchEnter(order = enterOrder, enabled = animateIn)),
        style = style,
        shape = FinchShapes.tile,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = FinchSpace.sm, vertical = FinchSpace.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 21.sp, stepSize = 1.sp),
            )
        }
    }
}

/** 通用列表行：左图标/封面 + 两行文字 + 右侧内容 */
@Composable
fun FinchListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    meta: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    enterOrder: Int? = null,
    animateIn: Boolean = false,
) {
    val animated = if (animateIn) {
        modifier.then(Modifier.finchEnter(order = enterOrder ?: 0))
    } else {
        modifier
    }
    val base = if (onClick != null) {
        animated.finchClickable(onClick = onClick)
    } else {
        animated
    }
    Row(
        base.fillMaxWidth().padding(vertical = FinchSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(FinchSpace.md))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (meta != null) {
            Spacer(Modifier.width(FinchSpace.sm))
            Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.let {
            Spacer(Modifier.width(FinchSpace.xs))
            Row(verticalAlignment = Alignment.CenterVertically, content = it)
        }
    }
}

/** 空状态：图标 + 标题 + 说明 + 可选主操作，统一放在一张玻璃卡里 */
@Composable
fun FinchEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    GlassSurface(modifier = modifier.fillMaxWidth().then(Modifier.finchEnter())) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = FinchSpace.xxl, vertical = FinchSpace.xxxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(58.dp)
                    .clip(FinchShapes.pill)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(FinchSpace.lg))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(FinchSpace.xs))
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(FinchSpace.xl))
                FinchButton(text = actionLabel, onClick = onAction)
            }
        }
    }
}

/**
 * 统一弹窗：全屏遮罩 + 连续圆角玻璃面板（Dialog 独立窗口采样不到 App 背景，
 * 因此用高不透明度面板 + 内高光模拟玻璃，避免出现「越界采样」的脏边）。
 */
@Composable
fun FinchDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    confirmLabel: String? = "确定",
    dismissLabel: String = "取消",
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    /** 内容较高（如搜索列表 + 键盘）时让面板整体可滚动 */
    scrollable: Boolean = false,
    onConfirm: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val bundle = finchBundle()
    val glass = glassPalette()
    val scheme = MaterialTheme.colorScheme
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.Center) {
            // 遮罩：点空白处关闭
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (bundle.isDark) 0.5f else 0.38f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
            )
            Column(
                modifier
                    .padding(horizontal = FinchSpace.xxl)
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
                    .shadow(30.dp, FinchShapes.card, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.35f))
                    .clip(FinchShapes.card)
                    .background(
                        Brush.verticalGradient(
                            listOf(scheme.surface.copy(alpha = 0.98f), scheme.surfaceVariant.copy(alpha = 0.97f)),
                        )
                    )
                    .border(1.dp, glass.edge.copy(alpha = 0.45f), FinchShapes.card)
                    // 吞掉面板内的点击，避免穿透到遮罩关闭
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(FinchSpace.xxl),
            ) {
                // 弹层是独立窗口，采不到、也不该采主界面的背景层：
                // 置空 backdrop + 关玻璃，里面的 chip / 按钮直接走实底（UI_DESIGN_GUIDE §3.1）。
                CompositionLocalProvider(
                    LocalFinchBackdrop provides null,
                    LocalGlassEnabled provides false,
                ) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (subtitle != null) {
                        Spacer(Modifier.height(FinchSpace.xs))
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(FinchSpace.lg))
                    if (scrollable) {
                        // 内容区滚动，标题与底部按钮固定（搜索结果多、键盘弹起时仍能点到「添加」）
                        Box(
                            Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Column { content() }
                        }
                    } else {
                        content()
                    }
                    if (confirmLabel != null) {
                        Spacer(Modifier.height(FinchSpace.xl))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(FinchSpace.sm, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FinchButton(text = dismissLabel, onClick = onDismiss, variant = FinchButtonVariant.Ghost)
                            FinchButton(
                                text = confirmLabel,
                                onClick = { onConfirm?.invoke() },
                                variant = if (destructive) FinchButtonVariant.Danger else FinchButtonVariant.Primary,
                                enabled = confirmEnabled,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 二次确认弹窗（删除记录 / 覆盖恢复），文案与按钮统一 */
@Composable
fun FinchConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = "确定",
    dismissLabel: String = "取消",
    destructive: Boolean = true,
) {
    FinchDialog(
        title = title,
        subtitle = message,
        onDismiss = onDismiss,
        confirmLabel = confirmLabel,
        dismissLabel = dismissLabel,
        destructive = destructive,
        onConfirm = onConfirm,
    ) {}
}

/** 底部弹层：圆角玻璃顶 + 拖拽手柄，用于平台选择等轻量操作 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinchSheet(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val glass = glassPalette()
    val bundle = finchBundle()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = if (bundle.isDark) 0.55f else 0.4f),
        dragHandle = null,
    ) {
        Column(
            modifier
                .fillMaxWidth()
                .clip(FinchShapes.sheet)
                .background(
                    Brush.verticalGradient(
                        listOf(scheme.surface.copy(alpha = 0.99f), scheme.surfaceVariant.copy(alpha = 0.98f)),
                    )
                )
                .border(1.dp, glass.edge.copy(alpha = 0.4f), FinchShapes.sheet)
                .padding(horizontal = FinchSpace.xxl, vertical = FinchSpace.lg),
        ) {
            // 同 FinchDialog：底部弹层也是独立窗口，不继承主界面的采样层。
            CompositionLocalProvider(
                LocalFinchBackdrop provides null,
                LocalGlassEnabled provides false,
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(FinchShapes.pill)
                        .background(glass.edge.copy(alpha = 0.6f)),
                )
                Spacer(Modifier.height(FinchSpace.xl))
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (subtitle != null) {
                    Spacer(Modifier.height(FinchSpace.xs))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(FinchSpace.lg))
                content()
            }
        }
    }
}

/** 下拉菜单：与卡片同源的圆角 + 高不透明度表面 */
@Composable
fun FinchDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = FinchShapes.tile,
        tonalElevation = 0.dp,
        shadowElevation = 16.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, glassPalette().edge.copy(alpha = 0.35f)),
        content = content,
    )
}

/** 菜单项：统一微圆角高亮与文字层级 */
@Composable
fun FinchDropdownItem(
    text: String,
    onClick: () -> Unit,
    selected: Boolean = false,
) {
    DropdownMenuItem(
        text = {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        },
        onClick = onClick,
        modifier = Modifier.padding(horizontal = FinchSpace.xs),
        contentPadding = PaddingValues(horizontal = FinchSpace.lg, vertical = FinchSpace.sm),
    )
}

/** Snackbar：玻璃胶囊，浮在悬浮底栏之上 */
@Composable
fun FinchSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState, modifier) { data ->
        GlassSurface(
            modifier = Modifier.padding(horizontal = FinchSpace.screen, vertical = FinchSpace.sm),
            style = GlassStyle.Chrome,
            shape = FinchShapes.pill,
        ) {
            Row(
                Modifier.padding(horizontal = FinchSpace.xl, vertical = FinchSpace.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    data.visuals.message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f, fill = false),
                )
                val actionLabel = data.visuals.actionLabel
                if (actionLabel != null) {
                    Spacer(Modifier.width(FinchSpace.md))
                    Text(
                        actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(FinchShapes.pill)
                            .clickable { data.performAction() }
                            .padding(horizontal = FinchSpace.sm, vertical = FinchSpace.xxs),
                    )
                }
            }
        }
    }
}

/** 玻璃列表卡片里的一条分割线（带左右缩进） */
@Composable
fun FinchInsetDivider(modifier: Modifier = Modifier, startIndent: androidx.compose.ui.unit.Dp = 0.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = startIndent)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    )
}

/**
 * 可折叠分组：设置页原来的「一长条卡片」改成「标题点开才展开内容」。
 *
 * 折叠态只用一行，长页面一眼能扫完；展开态照常用 [FinchCard] 排列。
 * 展开状态用 rememberSaveable 保存，旋转屏幕/切走再回来不会自己收起来。
 *
 * @param summary 折叠时右侧显示的一句话摘要（比如「已配置 · 自动同步关」），让收起后仍有信息量
 */
@Composable
fun FinchCollapsibleGroup(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    summary: String? = null,
    icon: ImageVector? = null,
    initiallyExpanded: Boolean = false,
    enterOrder: Int? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    // 箭头旋转跟着展开状态走，用同一个 spring 保持手感一致
    val arrow by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = FinchMotion.enter(),
        label = "groupArrow",
    )

    Column(modifier.fillMaxWidth()) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (enterOrder != null) Modifier.finchEnter(enterOrder) else Modifier),
            style = GlassStyle.Tile,
            shape = FinchShapes.tile,
            onClick = { expanded = !expanded },
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FinchSpace.lg, vertical = FinchSpace.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(FinchShapes.thumb)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(Modifier.width(FinchSpace.md))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (summary != null && !expanded) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(FinchSpace.xs))
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(arrow),
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(FinchMotion.enter()) + fadeIn(tween(160)),
            exit = shrinkVertically(FinchMotion.settle()) + fadeOut(tween(90)),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = FinchSpace.md),
                verticalArrangement = Arrangement.spacedBy(FinchSpace.md),
                content = content,
            )
        }
    }
}
