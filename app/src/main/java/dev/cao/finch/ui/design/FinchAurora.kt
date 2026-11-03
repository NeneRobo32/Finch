package dev.cao.finch.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import dev.cao.finch.ui.theme.finchBundle
import kotlin.random.Random

/**
 * 全屏氛围背景（2026-10 UI 重构的视觉底子）。
 *
 * 玻璃只有在「有明暗层次的光」上才有折射感：纯色 surface 底会让所有玻璃卡看起来像
 * 一块灰色贴纸。这里用主题自带的三团大光斑 + 垂直明暗过渡，做一个静态、不闪、几乎不耗电的底。
 *
 * 关键约束：**必须是静态的**——[com.kyant.backdrop] 采样的是这一层，
 * 背景一旦动画，所有玻璃面板都会跟着闪烁/掉帧。
 */
@Composable
fun FinchAurora(modifier: Modifier = Modifier) {
    val bundle = finchBundle()
    val ambient = bundle.ambient
    val dark = bundle.isDark
    // 固定种子 → 每次重组画出来的一模一样，采样层不会跳
    val grains = remember { buildGrains() }

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(ambient.base)

        // 三团错位光斑：右上暖、左中冷、底部回光
        glow(ambient.glowA, Offset(w * 0.92f, h * 0.06f), w * 0.85f, 0.55f * ambient.intensity)
        glow(ambient.glowB, Offset(w * 0.02f, h * 0.42f), w * 0.95f, 0.38f * ambient.intensity)
        glow(ambient.glowC, Offset(w * 0.58f, h * 1.04f), w * 1.0f, 0.4f * ambient.intensity)

        // 顶部压暗（状态栏可读）+ 底部抬亮（底栏玻璃下有层次）
        drawRect(
            Brush.verticalGradient(
                0f to (if (dark) Color.Black.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.18f)),
                0.28f to Color.Transparent,
                0.82f to Color.Transparent,
                1f to (if (dark) Color.Black.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.22f)),
            )
        )

        // 细微颗粒：透镜折射本质是把采样源的像素挪位，源如果是纯平滑渐变，
        // 挪几个像素前后几乎没区别——玻璃就成了半透明色板。
        // 这层颗粒提供高频细节，折射边缘才看得出来；它只有十几个灰阶，仍是静态绘制。
        drawGrains(grains)
    }
}

/**
 * 一颗氛围颗粒。[radius] 与 [dx]/[dy] 都是屏幕宽的相对比例，
 * 换算放到绘制时做，这样不同屏幕密度下颗粒大小观感一致。
 */
private class Grain(
    val dx: Float,
    val dy: Float,
    val radius: Float,
    val alpha: Float,
    val light: Boolean,
)

private const val GRAIN_COUNT = 620

/**
 * 三层颗粒：
 * - 细粒（大多数）：提供纹理，让 blur 之后仍有颗粒感，不至于糊成一片纯色
 * - 中团：尺寸正好落在折射位移量级上，透镜边缘能把它们「拉」出可见的弯曲
 * - 大团散景（少数）：提供低频明暗结构，玻璃压在什么上就有区别
 */
private fun buildGrains(): List<Grain> {
    val random = Random(20261005)
    return List(GRAIN_COUNT) {
        val tier = random.nextFloat()
        val (radius, alpha) = when {
            tier > 0.965f -> 0.05f + random.nextFloat() * 0.055f to 0.05f + random.nextFloat() * 0.05f
            tier > 0.78f -> 0.012f + random.nextFloat() * 0.018f to 0.04f + random.nextFloat() * 0.05f
            else -> 0.0018f + random.nextFloat() * 0.0045f to 0.02f + random.nextFloat() * 0.055f
        }
        Grain(
            dx = random.nextFloat(),
            dy = random.nextFloat(),
            radius = radius,
            alpha = alpha,
            light = random.nextBoolean(),
        )
    }
}

private fun DrawScope.drawGrains(grains: List<Grain>) {
    val w = size.width
    val h = size.height
    grains.forEach { grain ->
        val paint = if (grain.light) Color.White else Color.Black
        drawCircle(
            color = paint.copy(alpha = grain.alpha),
            radius = grain.radius * w,
            center = Offset(grain.dx * w, grain.dy * h),
        )
    }
}

private fun DrawScope.glow(color: Color, center: Offset, radius: Float, alpha: Float) {
    if (radius <= 0f || alpha <= 0f) return
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to color.copy(alpha = alpha),
                0.55f to color.copy(alpha = alpha * 0.45f),
                1f to color.copy(alpha = 0f),
            ),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

/** 局部光晕：给 hero 卡 / 空状态背后加一层主题色辉光（不含采样，不参与玻璃） */
@Composable
fun FinchGlow(
    modifier: Modifier = Modifier,
    color: Color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
) {
    Canvas(modifier) {
        drawCircle(
            brush = Brush.radialGradient(
                listOf(color.copy(alpha = 0.35f), Color.Transparent),
                center = center,
                radius = size.minDimension * 0.7f,
            ),
            radius = size.minDimension * 0.7f,
            center = center,
        )
    }
}
