/*
 * 移植自 Kyant0/AndroidLiquidGlass（Apache-2.0）catalog/components/LiquidButton.kt。
 * 玻璃配方与按下形变保持上游；enabled / 高度 / 内边距由 Finch 补，颜色走主题。
 *
 * 按压反馈（2026-11-13 重做）：按下进度改由 clickable 的 interactionSource 驱动，
 * 抬手必归零——不再让自绘手势去猜「up 有没有被消费」。手指按住后滑出按钮时，
 * interactionSource 收到的是 Cancel，同样会把进度放回去，不会卡在按下态。
 * 手指位移另用 Initial 阶段「只读」跟踪，只为液形拉伸，不参与任何结束判定。
 */
package dev.cao.finch.ui.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

/**
 * 官方 AndroidLiquidGlass catalog 的 LiquidButton（照搬配方）：
 * vibrancy + blur(2dp) + lens(12dp,24dp)，按下时按指针位置做胶囊形变/位移，
 * 并叠一层按下提亮。
 *
 * 与上游的差异：enabled / 高度 / 横向内边距参数，tint 与 surfaceColor 由主题传入；
 * 按下进度改由 interactionSource 驱动（上游用自绘手势，同链存在 clickable 时会卡住）。
 */
@Composable
fun LiquidButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isInteractive: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    height: Dp = 48.dp,
    horizontalPadding: Dp = 16.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val animationScope = rememberCoroutineScope()
    val interactive = isInteractive && enabled

    // 按下进度：唯一来源是 clickable 的 Press 交互。按下 → 1，抬手/滑出/取消 → 0，
    // 全由 Clickable 负责，任何被消费的 up 都不影响这里。
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressProgress by animateFloatAsState(
        targetValue = if (interactive && pressed) 1f else 0f,
        // 按下要「说到就到」：900 刚度约 60ms 起亮，抬手回落略带回弹。
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 900f, visibilityThreshold = 0.001f),
        label = "liquidPressProgress",
    )

    // 手指相对按下点的位移，只用于液形拉伸：在 Initial 阶段读，早于 clickable 处理，
    // 且只读不消费，因此永远拿得到完整的手势轨迹。
    val dragOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var startPosition by remember { mutableStateOf(Offset.Zero) }

    Row(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(2f.dp.toPx())
                    lens(12f.dp.toPx(), 24f.dp.toPx())
                },
                layerBlock = if (interactive) {
                    {
                        val width = size.width
                        val panelHeight = size.height

                        val scale = lerp(1f, 1f + 4f.dp.toPx() / size.height, pressProgress)

                        val maxOffset = size.minDimension
                        val initialDerivative = 0.05f
                        val offset = dragOffset.value
                        translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
                        translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)

                        val maxDragScale = 4f.dp.toPx() / size.height
                        val offsetAngle = atan2(offset.y, offset.x)
                        scaleX =
                            scale +
                                maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                                (width / panelHeight).fastCoerceAtMost(1f)
                        scaleY =
                            scale +
                                maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                                (panelHeight / width).fastCoerceAtMost(1f)
                    }
                } else {
                    null
                },
                onDrawSurface = {
                    if (tint.isSpecified) {
                        drawRect(tint, blendMode = BlendMode.Hue)
                        drawRect(tint.copy(alpha = 0.75f))
                    }
                    if (surfaceColor.isSpecified) {
                        drawRect(surfaceColor)
                    }
                    // 按下提亮：短暂点击也要有可感知高光；强度随进度，松开随弹簧淡出。
                    if (pressProgress > 0f) {
                        drawRect(Color.White.copy(alpha = 0.14f * pressProgress))
                    }
                },
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = if (interactive) null else LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
            .then(
                if (interactive) {
                    Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val down = event.changes.firstOrNull { it.pressed && !it.previousPressed }
                                val up = event.changes.firstOrNull { !it.pressed && it.previousPressed }
                                val moving = event.changes.firstOrNull { it.pressed }
                                when {
                                    down != null -> {
                                        startPosition = down.position
                                        animationScope.launch { dragOffset.snapTo(Offset.Zero) }
                                    }

                                    moving != null -> {
                                        val delta = moving.position - startPosition
                                        animationScope.launch { dragOffset.snapTo(delta) }
                                    }

                                    up != null -> {
                                        animationScope.launch {
                                            dragOffset.animateTo(
                                                Offset.Zero,
                                                spring(0.5f, 300f, Offset.VisibilityThreshold),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Modifier
                }
            )
            .height(height)
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
