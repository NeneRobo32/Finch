package dev.cao.finch.ui.design

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Finch 设计变量（2026-10 UI 重构）。
 *
 * 目标：全 App 只认这一份间距 / 动效 / 圆角口径，屏幕里不再出现零散的 dp 与 spring 参数。
 * 圆角统一在 [dev.cao.finch.ui.theme.FinchShapes]（连续曲率），这里只放间距与动效。
 */
object FinchSpace {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp

    /** 屏幕左右留白（所有 tab 页统一） */
    val screen = 18.dp

    /** 悬浮底栏 + 导航栏占位：所有可滚动页面底部统一让位，最后一张卡不会被压住 */
    val bottomBarClearance = 132.dp
}

/**
 * 动效口径（Material 3 Expressive 思路）：
 * - press：按下即收紧，抬起 200ms 内回弹，不抢注意力；
 * - enter：列表/卡片入场，轻微过冲；
 * - page：页面级转场，几乎不过冲，避免晕；
 * - settle：数值/尺寸变化，快速到位；
 * - bouncy：强调时刻（计时中、通关彩带前的 hero）才用。
 */
object FinchMotion {
    fun <T> press(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.78f, stiffness = 900f)

    fun <T> enter(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.88f, stiffness = 480f, visibilityThreshold = null)

    fun <T> page(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.9f, stiffness = 360f, visibilityThreshold = null)

    fun <T> settle(): FiniteAnimationSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    fun <T> bouncy(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.6f, stiffness = 260f, visibilityThreshold = null)

    /** IntOffset 页面转场用（spring 的 visibilityThreshold 对 IntOffset 无意义，置空） */
    fun pageOffset(): FiniteAnimationSpec<androidx.compose.ui.unit.IntOffset> =
        spring(dampingRatio = 0.9f, stiffness = 360f, visibilityThreshold = null)
}

/**
 * 统一点击反馈：光泽涟漪 + 按压缩放（0.97）。
 * 与 [androidx.compose.foundation.clickable] 用法一致，长按可选。
 */
fun Modifier.finchClickable(
    enabled: Boolean = true,
    role: Role? = null,
    onClickLabel: String? = null,
    onLongClickLabel: String? = null,
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
): Modifier = composed {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = FinchMotion.press(),
        label = "finchPress",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(
            if (onLongClick != null) {
                Modifier.combinedClickable(
                    enabled = enabled,
                    onClickLabel = onClickLabel,
                    role = role,
                    onLongClickLabel = onLongClickLabel,
                    onLongClick = onLongClick,
                    onClick = onClick,
                    interactionSource = source,
                    indication = ripple(),
                )
            } else {
                Modifier.combinedClickable(
                    enabled = enabled,
                    onClickLabel = onClickLabel,
                    role = role,
                    onClick = onClick,
                    interactionSource = source,
                    indication = ripple(),
                )
            }
        )
}
