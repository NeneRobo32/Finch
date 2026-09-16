package dev.cao.finch.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Finch 动效体系（Material 3 Expressive 风格，2026）：
 * - 微交互 100~200ms，组件过渡 200~300ms，页面级 300~500ms
 * - 弹簧：低阻尼弹跳 = 有生命感的反馈；高钢度 = 快速到位
 * - 进入缓动：快起慢落（emphasized）；离开：渐快
 * 全部基于 spring，替代生硬 tween。
 */

/** 列表项进场：自底部滑入 + 淡入（spring，带轻微自然弹跳） */
fun <T> springEnterSpec(): FiniteAnimationSpec<T> = spring<T>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

/** 页面级过渡用：轻微弹跳，感觉「活」 */
fun <T> springPageSpec(): FiniteAnimationSpec<T> = spring<T>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMedium,
)

/** 微交互（按压、点击反馈）：快而柔和，200ms 内到位 */
fun <T> springPressSpec(): FiniteAnimationSpec<T> = spring<T>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium,
)

/** 强调缓动：进入元素快起慢落（Material 3 emphasized 近似） */
fun <T> emphasizedTween(duration: Int = 400): FiniteAnimationSpec<T> =
    tween(duration, easing = androidx.compose.animation.core.FastOutSlowInEasing)

/**
 * 按压弹簧缩放 modifier：
 * 按下 → scale 0.95（tactile 反馈），抬起 → 弹性回弹 1.0。
 * 与点击区域分离：只动 graphicsLayer，不触发布局/重组。
 */
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.95f,
    springSpec: FiniteAnimationSpec<Float> = springPressSpec(),
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = springSpec,
        label = "pressScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}