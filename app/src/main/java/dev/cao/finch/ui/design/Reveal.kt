package dev.cao.finch.ui.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/**
 * 进场动画：淡入 + 上浮 + 轻微放大，按 [order] 依次错开。
 *
 * 只在第一次出现时播一次：错开延迟用 rememberSaveable 记账，列表回收重建、
 * 切 tab 回来都不会重播，避免「一滚动就闪一片」。
 * 关键点：延迟中途被移出组合（快速滚动）时协程会被取消，所以「已经等过了」和
 * 「已经播完了」要分开记——重进时如果只等到一半，就直接跳到 shown，绝不停在透明态。
 *
 * [order] 建议直接用列表下标，超过 [MAX_STAGGER] 后统一收尾，不让长列表等太久。
 * 动画全部落在 graphicsLayer 上，不触发重新布局，滚动时不掉帧。
 */
fun Modifier.finchEnter(
    order: Int = 0,
    enabled: Boolean = true,
    /** 每张卡之间的错开延迟（毫秒） */
    step: Long = 45L,
): Modifier = composed {
    if (!enabled) return@composed this

    // 错开的延迟只等一次；shown 等动画真正开始后才置位，防止白屏
    var staggered by rememberSaveable { mutableStateOf(false) }
    var shown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!staggered) {
            staggered = true
            val index = order.coerceIn(0, MAX_STAGGER)
            if (index > 0) delay(index * step)
        }
        shown = true
    }

    val enterSpec = FinchMotion.enter<Float>()
    // 注意：局部名不能叫 alpha/scaleX 之类，否则在 graphicsLayer 的作用域里
    // 会被同名的接收者成员遮蔽（隐式接收者优先级高于外层局部变量）
    val alphaAnim by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = enterSpec,
        label = "finchEnterAlpha",
    )
    val offsetAnim by animateFloatAsState(
        targetValue = if (shown) 0f else 26f,
        animationSpec = enterSpec,
        label = "finchEnterOffset",
    )
    val scaleAnim by animateFloatAsState(
        targetValue = if (shown) 1f else 0.94f,
        animationSpec = enterSpec,
        label = "finchEnterScale",
    )

    this.graphicsLayer {
        alpha = alphaAnim
        translationY = offsetAnim
        scaleX = scaleAnim
        scaleY = scaleAnim
    }
}

/** 错开上限：再靠后的卡片也一起进场，不让长列表等太久 */
private const val MAX_STAGGER = 10
