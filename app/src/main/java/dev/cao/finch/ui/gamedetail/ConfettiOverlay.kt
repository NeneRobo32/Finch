package dev.cao.finch.ui.gamedetail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate

/** 彩带粒子配色（通关庆祝） */
internal val ConfettiColors = listOf(
    Color(0xFFF5B301),
    Color(0xFFE05565),
    Color(0xFF6C5CE7),
    Color(0xFF00B894),
    Color(0xFF0984E3),
    Color(0xFFFD79A8),
)

internal data class ConfettiPiece(
    val xFrac: Float,
    val delayMs: Int,
    val fallMs: Int,
    val drift: Float,
    val side: Float,
    val color: Color,
    val spin: Float,
    val round: Boolean,
)

/** 通关庆祝彩带：一次性粒子（Canvas 绘制，播完自动移除），不拦截触摸 */
@Composable
internal fun ConfettiOverlay(modifier: Modifier = Modifier, onDone: () -> Unit) {
    val pieces = remember {
        val rnd = kotlin.random.Random(20260917)
        List(56) {
            ConfettiPiece(
                xFrac = rnd.nextFloat(),
                delayMs = (rnd.nextFloat() * 400).toInt(),
                fallMs = 1500 + rnd.nextInt(900),
                drift = (rnd.nextFloat() - 0.5f) * 0.18f,
                side = 9f + rnd.nextFloat() * 9f,
                color = ConfettiColors[rnd.nextInt(ConfettiColors.size)],
                spin = (rnd.nextFloat() - 0.5f) * 900f,
                round = rnd.nextBoolean(),
            )
        }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(durationMillis = 2600, easing = LinearEasing))
        onDone()
    }
    Canvas(modifier) {
        val now = progress.value * 2600f
        pieces.forEach { p ->
            val local = ((now - p.delayMs) / p.fallMs).coerceIn(0f, 1f)
            if (local > 0f && local < 1f) {
                val y = -40f + local * size.height * (0.72f + p.xFrac * 0.4f)
                val x = size.width * (p.xFrac + p.drift * local)
                val alpha = if (local < 0.8f) 1f else (1f - local) / 0.2f
                rotate(p.spin * local) {
                    if (p.round) {
                        drawCircle(p.color, radius = p.side / 2f, center = Offset(x, y), alpha = alpha)
                    } else {
                        drawRect(
                            p.color,
                            topLeft = Offset(x - p.side / 2f, y - p.side * 0.3f),
                            size = Size(p.side, p.side * 0.6f),
                            alpha = alpha,
                        )
                    }
                }
            }
        }
    }
}
