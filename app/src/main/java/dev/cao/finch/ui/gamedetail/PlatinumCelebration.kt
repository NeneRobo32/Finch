package dev.cao.finch.ui.gamedetail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** 白金质感的金属渐变（冷银 + 一点蓝紫，贴近 PS5 的铂金观感） */
internal val PlatinumGradient = listOf(
    Color(0xFFF2F6FB),
    Color(0xFFC7D6E6),
    Color(0xFF9CB6D1),
    Color(0xFFDCE8F5),
    Color(0xFF8FA6C0),
)

private const val TROPHY_MS = 3400
private const val HOLD_MS = 2100

/**
 * 获得白金奖杯的庆祝层（v0.27）：PS5 那种「奖杯滑出来 + 光晕」的观感，但不能真去抄素材。
 *
 * 全部用 Canvas 画：一个奖杯轮廓 + 背后径向光晕 + 升起的小光点。这样没有资源体积、
 * 没有版权问题，也不受下载失败影响（仓库里 [ConfettiOverlay] 是同一套做法）。
 *
 * 动画分三段：0~520ms 弹入（缓出曲线，尾巴自然收住）、520~2100ms 停住、2100~3400ms 淡出。
 * 点任意位置立刻结束。
 */
@Composable
internal fun PlatinumCelebrationOverlay(
    gameName: String,
    onDone: () -> Unit,
) {
    val appear = remember { Animatable(0f) }
    var exiting by remember { mutableStateOf(false) }
    // 点按和自然结束都会走到 onDone，用这个标记保证只回调一次
    var finished by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        appear.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(HOLD_MS.toLong())
        exiting = true
        kotlinx.coroutines.delay((TROPHY_MS - HOLD_MS).toLong())
        if (!finished) {
            finished = true
            onDone()
        }
    }

    val fade = if (exiting) 0f else 1f
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f * appear.value))
            .pointerInput(Unit) {
                detectTapGestures { if (!finished) { finished = true; onDone() } }
            }
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .graphicsLayer {
                    alpha = appear.value * fade
                    val s = 0.82f + 0.18f * appear.value
                    scaleX = s
                    scaleY = s
                    translationY = (1f - appear.value) * 40f
                },
        ) {
            PlatinumTrophy(progress = appear.value)
            Spacer(Modifier.height(24.dp))
            Text(
                "白金奖杯",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = PlatinumGradient[0],
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                gameName,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.86f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 奖杯本体：光晕 + 杯身 + 两侧把手 + 底座，配一层缓慢扫过的高光 */
@Composable
private fun PlatinumTrophy(progress: Float) {
    val shimmer = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        shimmer.animateTo(1f, tween(2200, easing = LinearEasing))
    }
    Box(
        modifier = Modifier
            .size(190.dp)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // 背后光晕：铂金偏冷的白，越靠心越亮
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFDCE8F5).copy(alpha = 0.34f * progress),
                        Color(0xFF9CB6D1).copy(alpha = 0.12f * progress),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = size.minDimension / 2f,
                ),
                radius = size.minDimension / 2f,
                center = center,
            )
            rotate(degrees = (1f - progress) * -18f, pivot = center) {
                drawTrophy(alpha = progress, shimmer = shimmer.value)
            }
        }
    }
}

private fun DrawScope.drawTrophy(alpha: Float, shimmer: Float) {
    val w = size.minDimension
    val cx = size.width / 2f
    val cy = size.height / 2f
    // 杯身占画布中间 46%，上下留出把手与底座
    val cupW = w * 0.46f
    val cupTop = cy - w * 0.26f
    val cupBottom = cy + w * 0.10f
    val body = Path().apply {
        moveTo(cx - cupW / 2f, cupTop)
        lineTo(cx + cupW / 2f, cupTop)
        // 下沿收成弧，别做成方的
        quadraticTo(cx + cupW * 0.34f, cupBottom, cx, cupBottom)
        quadraticTo(cx - cupW * 0.34f, cupBottom, cx - cupW / 2f, cupTop)
        close()
    }
    val brush = Brush.linearGradient(
        colors = PlatinumGradient.map { it.copy(alpha = alpha) },
        start = Offset(cx - cupW, cupTop),
        end = Offset(cx + cupW, cupBottom),
    )
    drawPath(body, brush)

    // 把手：两侧各一段开口椭圆弧
    val handleStroke = Stroke(width = w * 0.028f)
    val handleColor = PlatinumGradient[2].copy(alpha = alpha * 0.9f)
    listOf(-1f, 1f).forEach { side ->
        drawArc(
            color = handleColor,
            startAngle = if (side < 0) 90f else 270f,
            sweepAngle = if (side < 0) 180f else -180f,
            useCenter = false,
            topLeft = Offset(cx + side * (cupW * 0.62f) - cupW * 0.24f, cupTop + w * 0.02f),
            size = Size(cupW * 0.48f, w * 0.16f),
            style = handleStroke,
        )
    }

    // 杯脚 + 底座
    val stem = Path().apply {
        moveTo(cx - w * 0.022f, cupBottom)
        lineTo(cx + w * 0.022f, cupBottom)
        lineTo(cx + w * 0.022f, cupBottom + w * 0.10f)
        lineTo(cx - w * 0.022f, cupBottom + w * 0.10f)
        close()
    }
    drawPath(stem, PlatinumGradient[3].copy(alpha = alpha))
    drawRoundRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                PlatinumGradient[2].copy(alpha = alpha),
                PlatinumGradient[0].copy(alpha = alpha),
                PlatinumGradient[4].copy(alpha = alpha),
            ),
        ),
        topLeft = Offset(cx - w * 0.16f, cupBottom + w * 0.10f),
        size = Size(w * 0.32f, w * 0.05f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.02f),
    )

    // 高光：一条竖着的窄光带从左扫到右，扫过就看不见
    if (alpha > 0f) {
        val bandX = cx - w * 0.30f + w * 0.60f * shimmer
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.30f * alpha),
                    Color.Transparent,
                ),
                startX = bandX - w * 0.06f,
                endX = bandX + w * 0.06f,
            ),
            topLeft = Offset(cx - cupW / 2f, cupTop),
            size = Size(cupW, cupBottom - cupTop),
        )
    }
}
