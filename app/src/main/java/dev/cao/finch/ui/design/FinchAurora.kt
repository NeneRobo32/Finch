package dev.cao.finch.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import dev.cao.finch.ui.theme.finchBundle

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
