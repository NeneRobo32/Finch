package dev.cao.finch.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
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
import dev.cao.finch.ui.theme.LocalGlassEnabled
import dev.cao.finch.ui.theme.finchBundle
import dev.cao.finch.ui.theme.glassPalette

/**
 * 全局玻璃采样源：由 [dev.cao.finch.ui.FinchAppScaffold] 在根部提供，
 * 卡片/按钮/底栏共享同一块「静态氛围背景」，滚动时不重采样，玻璃不闪。
 *
 * 注意这个值会被 [GlassSurface] 在卡片内部改写：卡片的子控件采样到的是
 * 「卡片自己的玻璃层」，于是按钮/开关能在卡片上做出真实的层间折射，
 * 而不是去采最底下的氛围背景（那样看起来像挖了个洞）。
 */
val LocalFinchBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/**
 * 玻璃嵌套深度。只允许「根背景 → 卡片」这一层再导出采样源，
 * 卡片里的卡片不再继续导出——每导出一层就要多录一张离屏层，
 * 而视觉上再深一层已经看不出差别了。
 */
private val LocalGlassDepth = compositionLocalOf { 0 }

/**
 * 玻璃化打开后的折射配方。玻璃要"看得出是玻璃"，采样源必须有细节：
 * 平滑渐变被位移几个像素后几乎不变，看起来就像一块半透明色板。
 * 所以 [FinchAurora] 加了细微颗粒，这里的折射强度也调得比原来更明显。
 */
private const val GLASS_CHROMATIC = true

/** 最多导出几层嵌套采样源（根背景 → 卡片 → 卡内控件用卡片层），再深不再导出 */
private const val MAX_GLASS_DEPTH = 1

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
    Chrome(tintScale = 0.30f, blurDp = 6f, refractionHeightDp = 30f, refractionAmountDp = 40f, sheen = 0.22f, edgeAlpha = 0.62f),

    /** 内容卡：默认层级 */
    Card(tintScale = 0.22f, blurDp = 2.5f, refractionHeightDp = 24f, refractionAmountDp = 32f, sheen = 0.18f, edgeAlpha = 0.52f),

    /** 小格 / 列表行：轻量，列表里几十个也不至于糊成一片 */
    Tile(tintScale = 0.18f, blurDp = 2f, refractionHeightDp = 18f, refractionAmountDp = 24f, sheen = 0.14f, edgeAlpha = 0.46f),

    /** Hero / 封面容器：让封面透出来，只做轻微玻璃 */
    Hero(tintScale = 0.12f, blurDp = 1.5f, refractionHeightDp = 14f, refractionAmountDp = 18f, sheen = 0.10f, edgeAlpha = 0.38f),
    ;
}

/**
 * 玻璃是否再叠一层模糊。
 * 采样源只有全屏静态氛围背景——三团大光斑 + 垂直渐变，平滑到 blur 前后差别很小，
 * 却要对整块面板多跑一遍像素（LayerBackdrop 还会随位移重录）。
 * 所以只在「全面玻璃化」开关打开时才走这条更厚的链路，关掉时根本不进 drawBackdrop。
 */
private const val GLASS_BLUR = true

/**
 * 统一的玻璃面板（Liquid Glass，基于 Kyant0/backdrop 2.0.0）：
 * vibrancy 提亮 → blur 模糊 → lens 边缘折射，再叠主题色 + 顶部光晕。
 *
 * 「设置 → 实验性功能 → 全面玻璃化」关闭时退回实色面板（[SolidPanel]）：
 * 外观接近旧版，且完全不碰 drawBackdrop —— 不分配离屏层、不重录快照。
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
    if (!LocalGlassEnabled.current) {
        SolidPanel(
            modifier = modifier,
            style = style,
            shape = shape,
            enabled = enabled,
            interactionSource = interactionSource,
            onClick = onClick,
            onLongClick = onLongClick,
            content = content,
        )
        return
    }

    val glass = glassPalette()
    val dark = finchBundle().isDark
    // 只有调用方没传采样源时才自建：原先无条件 rememberLayerBackdrop()，
    // 每张卡片都白占一个 GraphicsLayer（一屏十几张，纯浪费）
    val layer = if (backdrop != null) backdrop else rememberOwnBackdrop()
    val source = interactionSource ?: remember { MutableInteractionSource() }

    // 嵌套玻璃：把这张面板「已经画好的玻璃结果」导出成一层采样源，交给卡内控件用。
    // 没有这一步，卡里的按钮/开关只能采到根部的氛围背景，折射区域对不上，
    // 看起来就是在卡片上挖了个洞——所以以前 InsetSurface 干脆放弃了采样。
    //
    // 只有大卡片（Card / Chrome）才导出：列表里的 Tile 行几十个一起滚动，
    // 每行多录一张离屏层不划算，而且那些行里基本没有需要折射的控件。
    // 深度也限一层——再深一眼看不出差别，纯属多录。
    val depth = LocalGlassDepth.current
    val canExport = depth < MAX_GLASS_DEPTH &&
        (style == GlassStyle.Card || style == GlassStyle.Chrome)
    val exportLayer = if (canExport) rememberLayerBackdrop() else null

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
    val glassModifier = remember(layer, shape, style, tint, sheen, highlight, shadow, exportLayer) {
        Modifier.drawBackdrop(
            backdrop = layer,
            shape = { shape },
            effects = {
                vibrancy()
                // 打开「全面玻璃化」后这里才真正跑 blur：采样源是氛围光（三团大光斑 + 垂直渐变），
                // blur 的增益主要体现在有高频内容的场景；关掉开关时根本不进 drawBackdrop，
                // 所以这条更重的链路只在用户主动开启时承担开销。
                if (GLASS_BLUR) blur(style.blurDp.dp.toPx())
                // depthEffect：边缘折射带上一点纵深变形，玻璃「有厚度」的主要来源；
                // chromaticAberration：虹彩边——真实玻璃折射短波长的偏差，是这个效果最像玻璃的地方。
                // 两个开关都由运行时着色器实现，不支持时库内部会自己跳过。
                lens(
                    refractionHeight = style.refractionHeightDp.dp.toPx(),
                    refractionAmount = style.refractionAmountDp.dp.toPx(),
                    depthEffect = true,
                    chromaticAberration = GLASS_CHROMATIC,
                )
            },
            highlight = { highlight },
            shadow = { shadow },
            exportedBackdrop = exportLayer,
            onDrawSurface = {
                drawRect(tint)
                drawRect(sheen)
            },
        )
    }

    // clip 必须排在 glassModifier 之后：ShadowElement 在 drawContent() 之前把阴影
    // 画进自己的层并向外扩 radius*2，外层若先 clip，这块阴影每帧照算 BlurMaskFilter
    // 却一点看不见。放到 glassModifier 之后，它只裁内容（涟漪、封面），阴影就出来了。
    // 顺序参考 Containers.kt 的 FinchDialog：阴影在外、裁剪在内。
    val boxModifier = modifier
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
        .clip(shape)
        .border(border, shape)

    if (exportLayer != null) {
        // 卡内控件（开关/滑块/分段/芯片）采样这张面板自己的玻璃层，
        // 才能在卡片上做出真实的层间折射，而不是去采最底下的氛围光。
        CompositionLocalProvider(
            LocalFinchBackdrop provides exportLayer,
            LocalGlassDepth provides (depth + 1),
        ) {
            Box(boxModifier, content = content)
        }
    } else {
        Box(boxModifier, content = content)
    }
}

/** backdrop 缺省时的兜底采样源（单独抽出来，才能只在需要时创建） */
@Composable
private fun rememberOwnBackdrop(): Backdrop = rememberLayerBackdrop()

/**
 * 实色面板：玻璃化开关关闭时的降级外观。
 * 用一档极淡的竖向渐变代替折射，压在氛围光上仍有层次，但不做任何采样。
 * 层级越厚（[GlassStyle.Chrome]）底色越实、描边越亮，保持原有的视觉分层。
 */
@Composable
private fun SolidPanel(
    modifier: Modifier,
    style: GlassStyle,
    shape: Shape,
    enabled: Boolean,
    interactionSource: MutableInteractionSource?,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    content: @Composable BoxScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = finchBundle().isDark
    val source = interactionSource ?: remember { MutableInteractionSource() }
    // 玻璃越厚 → 底色越不透明；1.0f - tintScale 把四档映射成 0.38f..0.70f
    val fill = remember(scheme, style, dark, enabled) {
        val base = if (dark) scheme.surface else scheme.surfaceContainerLow
        val alpha = when {
            !enabled -> 0.34f
            style == GlassStyle.Chrome -> 0.70f
            style == GlassStyle.Card -> 0.58f
            style == GlassStyle.Tile -> 0.50f
            else -> 0.42f
        }
        Brush.verticalGradient(
            0f to base.copy(alpha = alpha),
            1f to scheme.surfaceContainerHigh.copy(alpha = alpha),
        )
    }
    val stroke = remember(scheme, style, dark) {
        val base = if (dark) scheme.outlineVariant else scheme.outline
        val alpha = if (dark) style.edgeAlpha * 0.9f else style.edgeAlpha * 0.7f
        BorderStroke(1.dp, base.copy(alpha = alpha))
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
            .background(fill, shape)
            .border(stroke, shape),
        content = content,
    )
}
