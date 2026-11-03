package dev.cao.finch.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import dev.cao.finch.ui.theme.BackgroundStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 背景层调度：玻璃采样的是这一层，所以换背景 = 换整套玻璃的观感。
 *
 * @param style 背景样式
 * @param imagePath [BackgroundStyle.CUSTOM] 时用的本地图片路径（空则退回氛围光）
 */
@Composable
fun FinchBackdrop(
    style: BackgroundStyle,
    imagePath: String,
    modifier: Modifier = Modifier,
) {
    when {
        style == BackgroundStyle.CUSTOM && imagePath.isNotEmpty() -> CustomImageBackground(imagePath, modifier)
        style == BackgroundStyle.PS5 -> Ps5ParticleBackground(modifier)
        else -> FinchAurora(modifier)
    }
}

/**
 * 自定义图片背景：整屏铺满裁切 + 一层贴合主题的压色。
 *
 * 图片内容不可控——用户可能选一张纯白图配浅色主题，也可能选张夜景配深色主题，
 * 反过来就全糊了。所以压色跟着主题走：浅色主题压白、深色主题压黑，
 * 保证前景文字的对比度始终够。压色不能太薄，否则深色主题 + 亮图 = 白字看不清。
 */
@Composable
private fun CustomImageBackground(imagePath: String, modifier: Modifier = Modifier) {
    val dark = dev.cao.finch.ui.theme.finchBundle().isDark
    Box(modifier) {
        AsyncImage(
            model = imagePath,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to (if (dark) Color.Black.copy(alpha = 0.62f) else Color.White.copy(alpha = 0.58f)),
                        0.45f to (if (dark) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.44f)),
                        1f to (if (dark) Color.Black.copy(alpha = 0.68f) else Color.White.copy(alpha = 0.6f)),
                    )
                )
        )
    }
}

/**
 * PS5 开机粒子风格：深空底 + 蓝青粒子向内收束 + 地平线辉光。
 *
 * 设计上刻意模仿 PS5 开机动画的气质——不是复刻，是同一套语言：
 * · 极深近黑的底，让玻璃压上去时层次分明
 * · 蓝到青的冷色粒子，大小/亮度分层，近大远小
 * · 中央偏下一条水平辉光，像地平线
 * · 每颗粒子停在自己「生命周期的某一刻」，整体是一帧凝固的开机动画
 *
 * **为什么不做成动的**：这一层是玻璃的采样源。[com.kyant.backdrop] 的
 * layerBackdrop 在内容重绘时重录整张离屏层——背景一旦逐帧动画，就是每帧多录
 * 一张全屏位图，同时所有玻璃面板的折射纹理跟着抖。代价太大，而静态一帧
 * 已经把 PS5 那个气质表达出来了。
 */
@Composable
private fun Ps5ParticleBackground(modifier: Modifier = Modifier) {
    val dark = dev.cao.finch.ui.theme.finchBundle().isDark
    // 固定种子 → 每次重组画出来一模一样，采样层不会跳
    val particles = remember { buildPs5Particles() }
    // 地平线辉光的呼吸也固定在某一相位，保持静态
    val breathe = 0.88f

    Canvas(modifier) {
        val w = size.width
        val h = size.height

        // 底色：近黑，带一点点蓝，避免纯黑显得廉价
        drawRect(if (dark) Color(0xFF04060C) else Color(0xFF0A0F1C))
        // 中央一团深蓝光晕：给画面定一个「光源」，粒子才有被照亮的感觉
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to Color(0xFF0B2A5E).copy(alpha = 0.85f),
                    0.45f to Color(0xFF071A3C).copy(alpha = 0.55f),
                    1f to Color.Transparent,
                ),
                center = Offset(w * 0.5f, h * 0.5f),
                radius = w * 0.9f,
            ),
            radius = w * 0.9f,
            center = Offset(w * 0.5f, h * 0.5f),
        )
        // 底部再压一层，做出「上面是太空、下面是地平线」的分界
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.55f to Color.Transparent,
                1f to Color(0xFF02040A).copy(alpha = if (dark) 0.95f else 0.8f),
            )
        )

        // 地平线辉光：中央偏下一条横向的光，PS5 开机那个感觉的来源
        drawHorizonGlow(w, h, breathe)

        // 粒子：每颗停在自己生命周期的某一刻（phase 就是它的 life）
        particles.forEach { p ->
            val life = p.phase
            // 半径随生命周期向内收缩
            val shrink = 1f - life * p.travel
            val cx = w * (0.5f + p.dx * shrink)
            val cy = h * (0.52f + p.dy * shrink)
            // 淡入淡出：中段最亮，两端透明
            val fade = when {
                life < 0.18f -> life / 0.18f
                life > 0.72f -> (1f - life) / 0.28f
                else -> 1f
            }
            if (fade <= 0.01f) return@forEach
            val radius = p.radius * w * (0.55f + 0.45f * shrink)
            val color = p.color.copy(alpha = p.alpha * fade)
            // 外圈柔光 + 内核亮点：粒子不是硬边圆点，否则很像噪点
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(0f to color, 0.35f to color.copy(alpha = color.alpha * 0.5f), 1f to color.copy(alpha = 0f)),
                    center = Offset(cx, cy),
                    radius = radius * 3.2f,
                ),
                radius = radius * 3.2f,
                center = Offset(cx, cy),
            )
            drawCircle(color = color, radius = radius, center = Offset(cx, cy))
        }

        // 顶部一点点冷光，别让上半屏死黑
        drawRect(
            Brush.verticalGradient(
                0f to Color(0xFF1B3A7A).copy(alpha = if (dark) 0.35f else 0.22f),
                0.42f to Color.Transparent,
            )
        )
    }
}

/** 地平线辉光：横向椭圆渐变 + 一条更亮的核心线 */
private fun DrawScope.drawHorizonGlow(w: Float, h: Float, breathe: Float) {
    val cy = h * 0.72f

    drawOval(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to Color(0xFF3FA9F5).copy(alpha = 0.30f * breathe),
                0.45f to Color(0xFF1E6FD9).copy(alpha = 0.14f * breathe),
                1f to Color.Transparent,
            ),
            center = Offset(w * 0.5f, cy),
            radius = w * 0.85f,
        ),
        topLeft = Offset(w * 0.5f - w * 0.85f, cy - h * 0.14f),
        size = Size(w * 1.7f, h * 0.28f),
    )
    // 核心线：极细极淡，只有一层，多了就假
    drawOval(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to Color(0xFF9BD8FF).copy(alpha = 0.55f * breathe),
                0.5f to Color(0xFF4FB4F0).copy(alpha = 0.18f * breathe),
                1f to Color.Transparent,
            ),
            center = Offset(w * 0.5f, cy),
            radius = w * 0.5f,
        ),
        topLeft = Offset(w * 0.5f - w * 0.5f, cy - h * 0.012f),
        size = Size(w * 1.0f, h * 0.024f),
    )
}

/**
 * 一颗 PS5 粒子。[dx]/[dy] 是相对中心的位置（±0.6 左右），
 * [travel] 是它在一个循环里向内收缩的比例，[phase] 让几百颗粒子错开。
 */
private class Ps5Particle(
    val dx: Float,
    val dy: Float,
    val radius: Float,
    val alpha: Float,
    val travel: Float,
    val phase: Float,
    val color: Color,
)

private const val PS5_PARTICLE_COUNT = 260

/**
 * 粒子分布：径向铺开（不是均匀撒），中心稀疏边缘密——收束时才像在「吸入」。
 * 颜色从蓝到青再到一点点白，靠后的粒子更暗更小（大气透视）。
 */
private fun buildPs5Particles(): List<Ps5Particle> {
    val random = Random(20261103)
    val palette = listOf(
        Color(0xFF7FD4FF),
        Color(0xFF4FB4F0),
        Color(0xFF2E7BD6),
        Color(0xFFB8E8FF),
        Color(0xFF59E0D0),
    )
    return List(PS5_PARTICLE_COUNT) {
        // 极坐标铺开：角度均匀，半径用 sqrt 让面积均匀但略偏向中心
        val angle = random.nextFloat() * 2f * PI.toFloat()
        val r = 0.18f + random.nextFloat() * 0.42f
        val depth = random.nextFloat() // 0=远 1=近
        Ps5Particle(
            dx = cos(angle) * r,
            dy = sin(angle) * r * 0.85f, // 纵向压扁一点，符合宽屏观感
            radius = 0.0009f + depth * 0.0026f,
            alpha = 0.25f + depth * 0.6f,
            travel = 0.35f + depth * 0.45f,
            phase = random.nextFloat(),
            color = palette[(depth * palette.size).toInt().coerceIn(0, palette.size - 1)],
        )
    }
}
