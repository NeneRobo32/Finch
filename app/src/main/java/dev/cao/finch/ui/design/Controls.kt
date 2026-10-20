package dev.cao.finch.ui.design

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.cao.finch.ui.liquid.LiquidButton
import dev.cao.finch.ui.liquid.LiquidSlider
import dev.cao.finch.ui.liquid.LiquidToggle
import dev.cao.finch.ui.theme.FinchShapes
import dev.cao.finch.ui.theme.glassPalette

/**
 * Finch 控件库（2026-10 UI 重构）：
 * 所有按钮 / 筛选 / 输入 / 开关都从这里取，屏幕里不再直接用 Material 默认组件，
 * 保证 Liquid Glass 语言、圆角、动效、禁用态在整个 App 里一致。
 */

enum class FinchButtonVariant {
    /** 主题渐变实心：页面主操作（开玩、添加、同步） */
    Primary,

    /** 玻璃底 + 细描边：次级操作 */
    Glass,

    /** 纯文字：低优先级（取消、编辑、清除） */
    Ghost,

    /** 错误色实心：删除、退出等破坏性操作 */
    Danger,
}

/**
 * 内嵌平面玻璃：卡片 / 弹窗内部的次级控件（筛选胶囊、次级按钮、分段轨道）。
 *
 * 不做折射采样——嵌套玻璃会采到根背景而不是父卡片，视觉上像「挖了个洞」；
 * 这里改用半透明表面 + 细描边，色调与玻璃一致，同时省掉一层 RenderEffect。
 */
@Composable
fun InsetSurface(
    modifier: Modifier = Modifier,
    shape: Shape = FinchShapes.tile,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.finchClickable(enabled = enabled, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .background(scheme.surfaceVariant.copy(alpha = if (enabled) 0.55f else 0.3f))
            .border(1.dp, scheme.outline.copy(alpha = 0.3f), shape),
        content = content,
    )
}

/**
 * 统一按钮：高度 46dp、胶囊形、按压缩放 + 涟漪。
 * @param loading 显示转圈并禁用点击（同步/保存中）
 */
@Composable
fun FinchButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: FinchButtonVariant = FinchButtonVariant.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    /** 紧凑模式：列表行内的小操作（入库、关注），高度更矮 */
    compact: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val active = enabled && !loading
    val content: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(15.dp),
                    strokeWidth = 2.dp,
                    color = buttonContentColor(variant),
                )
                Spacer(Modifier.width(8.dp))
            } else if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = buttonContentColor(variant))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = buttonContentColor(variant),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    when (variant) {
        FinchButtonVariant.Primary, FinchButtonVariant.Danger -> {
            val base = if (variant == FinchButtonVariant.Danger) scheme.error else scheme.primary
            val backdrop = LocalFinchBackdrop.current ?: rememberLayerBackdrop()
            // 主操作走官方 LiquidButton：玻璃折射 + 按下形变 + InteractiveHighlight；
            // 用主题色 tint（Hue + 75% 叠色）保留玻璃质感，同时保证白字对比度。
            LiquidButton(
                onClick = onClick,
                backdrop = backdrop,
                enabled = active,
                tint = base,
                modifier = modifier
                    .shadow(
                        elevation = 10.dp,
                        shape = FinchShapes.pill,
                        ambientColor = base.copy(alpha = 0.35f),
                        spotColor = base.copy(alpha = 0.45f),
                    )
                    .alphaIf(!active, 0.45f),
                height = if (compact) 36.dp else 48.dp,
                horizontalPadding = if (compact) 12.dp else 20.dp,
            ) { content() }
        }

        FinchButtonVariant.Glass -> {
            InsetSurface(
                modifier = modifier.alphaIf(!active, 0.45f),
                shape = FinchShapes.pill,
                enabled = active,
                onClick = onClick,
            ) {
                Box(Modifier.padding(horizontal = if (compact) 12.dp else 20.dp, vertical = if (compact) 8.dp else 13.dp)) { content() }
            }
        }

        FinchButtonVariant.Ghost -> {
            Box(
                modifier
                    .finchClickable(enabled = active, onClick = onClick)
                    .alphaIf(!active, 0.45f)
                    .clip(FinchShapes.pill)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { content() }
        }
    }
}

@Composable
private fun buttonContentColor(variant: FinchButtonVariant): Color = when (variant) {
    FinchButtonVariant.Primary -> MaterialTheme.colorScheme.onPrimary
    FinchButtonVariant.Danger -> MaterialTheme.colorScheme.onError
    FinchButtonVariant.Glass, FinchButtonVariant.Ghost -> MaterialTheme.colorScheme.onSurface
}

/**
 * 图标按钮：默认玻璃圆底，[ghost] 时只有点击涟漪。
 * 尺寸固定 40dp，保证顶栏左右一致。
 */
@Composable
fun FinchIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    ghost: Boolean = false,
    /** 内嵌场景（弹窗 / 卡片内部）用平面玻璃，避免越界采样 */
    flat: Boolean = false,
    enabled: Boolean = true,
) {
    if (ghost) {
        IconButton(onClick = onClick, modifier = modifier.size(40.dp), enabled = enabled) {
            Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(22.dp))
        }
    } else if (flat) {
        InsetSurface(
            modifier = modifier.size(40.dp),
            shape = FinchShapes.pill,
            enabled = enabled,
            onClick = onClick,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(21.dp))
            }
        }
    } else {
        GlassSurface(
            modifier = modifier.size(40.dp),
            style = GlassStyle.Tile,
            shape = FinchShapes.pill,
            enabled = enabled,
            onClick = onClick,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(21.dp))
            }
        }
    }
}

/**
 * 筛选胶囊：选中 = 主题色实心，未选中 = 玻璃底 + 细描边。
 * 主页筛选、日程数据源、平台选择统一用它。
 */
@Composable
fun FinchChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val textColor = if (selected) scheme.onPrimary else scheme.onSurfaceVariant
    val content: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(15.dp), tint = textColor)
                Spacer(Modifier.width(5.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (selected) {
        Box(
            modifier
                .finchClickable(onClick = onClick)
                .clip(FinchShapes.pill)
                .background(scheme.primary)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) { content() }
    } else {
        InsetSurface(
            modifier = modifier,
            shape = FinchShapes.pill,
            onClick = onClick,
        ) {
            Box(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) { content() }
        }
    }
}

/**
 * 分段控件：胶囊玻璃容器 + 滑动高亮块。
 * 用于「月 / 年」这类 2~4 个固定选项的切换。
 */
@Composable
fun <T> FinchSegmented(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    val index = options.indexOf(selected).coerceAtLeast(0)
    BoxWithConstraints(modifier) {
        val cell = maxWidth / options.size
        val offset by animateDpAsState(
            targetValue = cell * index,
            animationSpec = FinchMotion.settle<androidx.compose.ui.unit.Dp>(),
            label = "segOffset",
        )
        InsetSurface(shape = FinchShapes.pill) {
            Box(Modifier.fillMaxWidth().height(46.dp).padding(4.dp)) {
                Box(
                    Modifier
                        .offset(x = offset)
                        .width(cell - 8.dp)
                        .fillMaxHeight()
                        .background(
                            Brush.linearGradient(
                                listOf(scheme.primary, lerp(scheme.primary, scheme.tertiary, 0.3f)),
                            ),
                            FinchShapes.pill,
                        ),
                )
                Row(Modifier.fillMaxSize()) {
                    options.forEach { option ->
                        val isSelected = option == selected
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(FinchShapes.pill)
                                .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(option) }),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label(option),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) scheme.onPrimary else scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 统一输入框：透明底 + 玻璃描边 + 连续圆角；聚焦时描边转主题色 */
@Composable
fun FinchTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 6,
    password: Boolean = false,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val scheme = MaterialTheme.colorScheme
    val glass = glassPalette()
    OutlinedTextField(        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        label = label?.let { { Text(it, style = MaterialTheme.typography.labelMedium) } },
        placeholder = placeholder?.let { { Text(it, style = MaterialTheme.typography.bodyLarge) } },
        leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(20.dp)) } },
        trailingIcon = trailing,
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = FinchShapes.field,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = keyboardOptions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = scheme.onSurface,
            unfocusedTextColor = scheme.onSurface,
            disabledTextColor = scheme.onSurfaceVariant,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = scheme.surfaceVariant.copy(alpha = 0.35f),
            disabledContainerColor = Color.Transparent,
            cursorColor = scheme.primary,
            focusedBorderColor = scheme.primary.copy(alpha = 0.85f),
            unfocusedBorderColor = scheme.outline.copy(alpha = 0.55f),
            disabledBorderColor = glass.edge.copy(alpha = 0.2f),
            focusedLabelColor = scheme.primary,
            unfocusedLabelColor = scheme.onSurfaceVariant,
            focusedPlaceholderColor = scheme.onSurfaceVariant.copy(alpha = 0.7f),
            unfocusedPlaceholderColor = scheme.onSurfaceVariant.copy(alpha = 0.7f),
            focusedLeadingIconColor = scheme.primary,
            unfocusedLeadingIconColor = scheme.onSurfaceVariant,
            focusedTrailingIconColor = scheme.onSurfaceVariant,
            unfocusedTrailingIconColor = scheme.onSurfaceVariant,
        ),
    )
}

/** 开关：官方 LiquidToggle（玻璃滑块 + 拖动/点击切换），颜色跟主题走 */
@Composable
fun FinchSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val backdrop = LocalFinchBackdrop.current ?: rememberLayerBackdrop()
    LiquidToggle(
        selected = { checked },
        onSelect = onCheckedChange,
        backdrop = backdrop,
        modifier = modifier.alphaIf(!enabled, 0.45f),
        enabled = enabled,
        accentColor = scheme.primary,
        trackColor = scheme.surfaceVariant.copy(alpha = 0.8f),
    )
}

/** 滑杆：官方 LiquidSlider（6dp 轨道 + 玻璃滑块），数值范围/步进由调用方控制 */
@Composable
fun FinchSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    val backdrop = LocalFinchBackdrop.current ?: rememberLayerBackdrop()
    LiquidSlider(
        value = { value },
        onValueChange = onValueChange,
        valueRange = valueRange,
        backdrop = backdrop,
        modifier = modifier.alphaIf(!enabled, 0.45f),
        enabled = enabled,
        visibilityThreshold = 0.05f,
        accentColor = accentColor,
        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
    )
}

@Composable
fun FinchCheckbox(    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    Checkbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = CheckboxDefaults.colors(
            checkedColor = scheme.primary,
            checkmarkColor = scheme.onPrimary,
            uncheckedColor = scheme.outline,
        ),
    )
}

/** 进度条：圆角轨道 + 主题渐变，数值变化走弹簧 */
@Composable
fun FinchProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 10.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = FinchMotion.enter<Float>(),
        label = "finchProgress",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(FinchShapes.pill)
            .background(trackColor),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated.coerceAtLeast(if (animated > 0f) 0.02f else 0f))
                .clip(FinchShapes.pill)
                .background(
                    Brush.horizontalGradient(
                        listOf(color, lerp(color, MaterialTheme.colorScheme.tertiary, 0.45f)),
                    )
                ),
        )
    }
}

/** 小徽章：倒计时、已在库、平台等元信息 */
@Composable
fun FinchBadge(
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Box(
        modifier
            .clip(FinchShapes.pill)
            .background(tint.copy(alpha = 0.14f))
            .border(1.dp, tint.copy(alpha = 0.2f), FinchShapes.pill)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
    }
}

/** 分隔线：比 Material 默认更细更淡，玻璃卡里不抢眼 */
@Composable
fun FinchDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
    )
}

/** 五星评分：可点选、可取消（再点一次当前星） */
@Composable
fun FinchStars(
    rating: Int?,
    onRate: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    starSize: androidx.compose.ui.unit.Dp = 30.dp,
    /** 每颗星的无障碍描述；@Composable 便于调用方用 stringResource（配置变化可刷新） */
    starDescription: (@Composable (star: Int, selected: Boolean) -> String?)? = null,
) {
    val gold = Color(0xFFF5B301)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        (1..5).forEach { star ->
            val on = rating != null && star <= rating
            val desc = starDescription?.invoke(star, on)
            Box(
                Modifier
                    .size(starSize + 8.dp)
                    .clip(CircleShape)
                    .finchClickable(onClick = { onRate(if (rating == star) null else star) }),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (on) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = desc,
                    tint = if (on) gold else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.size(starSize),
                )
            }
        }
    }
}

/**
 * 悬浮主按钮（添加游戏等）：玻璃胶囊 + 主题渐变图标底。
 * @param label 为空时是圆形 FAB
 */
@Composable
fun FinchFab(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val base = scheme.primary
    val end = lerp(scheme.primary, scheme.tertiary, 0.4f)
    // 悬浮在内容之上：不再做 backdrop 采样（玻璃会采到根背景而不是卡片，像挖了个洞），
    // 直接用主色渐变 + 阴影，和主按钮同一套强调色。
    Box(
        modifier
            .finchClickable(onClick = onClick)
            .shadow(
                elevation = 14.dp,
                shape = FinchShapes.pill,
                ambientColor = base.copy(alpha = 0.35f),
                spotColor = base.copy(alpha = 0.45f),
            )
            .clip(FinchShapes.pill)
            .background(Brush.linearGradient(listOf(base, end))),
    ) {
        Row(
            Modifier.height(56.dp).padding(horizontal = if (label == null) 0.dp else 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (label == null) {
                Box(Modifier.width(56.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = contentDescription, tint = scheme.onPrimary, modifier = Modifier.size(26.dp))
                }
            } else {
                Icon(icon, contentDescription = contentDescription, tint = scheme.onPrimary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onPrimary,
                )
            }
        }
    }
}

/** 加载态：与空状态同一视觉重量，不突然跳出一个大转圈 */
@Composable
fun FinchLoading(modifier: Modifier = Modifier, label: String = "加载中…") {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(
            modifier = Modifier.size(26.dp),
            strokeWidth = 2.5.dp,
            color = MaterialTheme.colorScheme.primary,
            trackColor = glassPalette().overlay,
        )
        Spacer(Modifier.height(12.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ===== 内部小工具 =====

private fun Modifier.alphaIf(condition: Boolean, alpha: Float): Modifier =
    if (condition) this.alpha(alpha) else this

