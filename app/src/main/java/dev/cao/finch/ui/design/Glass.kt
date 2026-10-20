package dev.cao.finch.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import dev.cao.finch.ui.theme.FinchShapes
import dev.cao.finch.ui.theme.finchBundle
import dev.cao.finch.ui.theme.glassPalette

/**
 * 全局玻璃采样源：由 [dev.cao.finch.ui.FinchAppScaffold] 在根部提供，
 * 卡片/按钮/底栏共享同一块「静态氛围背景」，滚动时不重采样，玻璃不闪。
 */
val LocalFinchBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/**
 * 玻璃层级：同一套折射配方，按「面板在视觉层级中的位置」调强度。
 * 之前的实现只有一个强度，导致底栏、内容卡、列表行看起来一样平。
 */
enum class GlassStyle(
    internal val tintScale: Float,
    internal val blurDp: Float,
    internal val refractionHeightDp: Float,
    internal val refractionAmountDp: Float,
    internal val sheen: Float,
    internal val edgeAlpha: Float,
) {
    /** 悬浮底栏 / 顶栏：最厚、折射最强，是「悬浮在内容之上」的层级 */
    Chrome(tintScale = 0.62f, blurDp = 10f, refractionHeightDp = 26f, refractionAmountDp = 30f, sheen = 0.2f, edgeAlpha = 0.55f),

    /** 内容卡：默认层级 */
    Card(tintScale = 0.5f, blurDp = 4f, refractionHeightDp = 14f, refractionAmountDp = 22f, sheen = 0.16f, edgeAlpha = 0.45f),

    /** 小格 / 列表行：轻量，列表里几十个也不至于糊成一片 */
    Tile(tintScale = 0.42f, blurDp = 3f, refractionHeightDp = 10f, refractionAmountDp = 16f, sheen = 0.12f, edgeAlpha = 0.38f),

    /** Hero / 封面容器：让封面透出来，只做轻微玻璃 */
    Hero(tintScale = 0.3f, blurDp = 2f, refractionHeightDp = 8f, refractionAmountDp = 12f, sheen = 0.1f, edgeAlpha = 0.3f),
    ;
}

/**
 * 玻璃是否再叠一层模糊（视觉开关）。
 * 采样源只有全屏静态氛围背景——三团大光斑 + 垂直渐变，平滑到 blur 前后几乎无差别；
 * 关掉少一遍全表面像素运算，且滚动时 LayerBackdrop 会随位移重录，省的是每帧开销。
 * 将来若要采样带细节的层（封面 / 内容），改回 true。
 */
private const val GLASS_BLUR = false

/**
 * 统一的玻璃面板（Liquid Glass，基于 Kyant0/backdrop 2.0.0）：
 * vibrancy 提亮 → blur 模糊 → lens 边缘折射，再叠主题色 + 顶部光晕。
 *
 * @param style 层级强度
 * @param shape 形状（默认连续圆角内容卡）
 * @param backdrop 采样源；默认取根部的 [LocalFinchBackdrop]
 * @param onClick 可点击时自带涟漪 + 按压缩放
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    style: GlassStyle = GlassStyle.Card,
    shape: Shape = FinchShapes.card,
    backdrop: Backdrop? = LocalFinchBackdrop.current,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val glass = glassPalette()
    val dark = finchBundle().isDark
    // 只有调用方没传采样源时才自建：原先无条件 rememberLayerBackdrop()，
    // 每张卡片都白占一个 GraphicsLayer（一屏十几张，纯浪费）
    val layer = if (backdrop != null) backdrop else rememberOwnBackdrop()
    val source = interactionSource ?: remember { MutableInteractionSource() }

    val edge = remember(glass, style) { glass.edge.copy(alpha = glass.edge.alpha * style.edgeAlpha) }
    val border = remember(edge) { BorderStroke(1.dp, edge) }
    val tint = remember(glass, style) { glass.tint.copy(alpha = glass.tint.alpha * style.tintScale) }
    // 顶部一层极淡的高光，玻璃「有厚度」的关键；抽出来是为了不在每帧绘制时重新分配 Brush
    val sheen = remember(style, dark) {
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = style.sheen * if (dark) 0.5f else 1f),
            0.45f to Color.Transparent,
            1f to Color.White.copy(alpha = style.sheen * 0.18f),
        )
    }
    // 深色玻璃影子里加一点主题色，浅色用中性黑，避免脏灰
    val shadowColor = if (dark) Color.Black.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.10f)
    val highlight = remember(dark) { Highlight.Default.copy(alpha = if (dark) 0.55f else 0.8f) }
    val shadow = remember(dark, shadowColor) {
        Shadow.Default.copy(radius = 18.dp, color = shadowColor, alpha = if (dark) 0.9f else 0.7f)
    }

    // 整条玻璃绘制链锁成同一个 Modifier 实例：drawBackdrop 每次调用都会 new 一个 ShapeProvider，
    // 而 ShapeProvider 按引用比较 —— 卡片只要重组一次，就会重建 shader 链并重录 5 个离屏层
    // （shadow / highlight 的 BlurMaskFilter 也在其中）。锁住之后重组不再触发重录，视觉零变化。
    val glassModifier = remember(layer, shape, style, tint, sheen, highlight, shadow) {
        Modifier.drawBackdrop(
            backdrop = layer,
            shape = { shape },
            effects = {
                vibrancy()
                // 采样源只有全屏氛围背景（三团大光斑 + 垂直渐变，没有高频细节），blur 在这里
                // 几乎不产生可见差别，却要对整块面板多跑一遍像素；而且 LayerBackdrop 是坐标
                // 相关的，卡片一移动（滚动）就要重录重算一次。先关掉：若某个面板看出渐变色带，
                // 把 GLASS_BLUR 改回 true 即可恢复。
                if (GLASS_BLUR) blur(style.blurDp.dp.toPx())
                lens(style.refractionHeightDp.dp.toPx(), style.refractionAmountDp.dp.toPx())
            },
            highlight = { highlight },
            shadow = { shadow },
            onDrawSurface = {
                drawRect(tint)
                drawRect(sheen)
            },
        )
    }

    Box(
        modifier
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.finchClickable(
                        enabled = enabled,
                        interactionSource = source,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier
                }
            )
            .then(glassModifier)
            .border(border, shape),
        content = content,
    )
}

/** backdrop 缺省时的兜底采样源（单独抽出来，才能只在需要时创建） */
@Composable
private fun rememberOwnBackdrop(): Backdrop = rememberLayerBackdrop()
