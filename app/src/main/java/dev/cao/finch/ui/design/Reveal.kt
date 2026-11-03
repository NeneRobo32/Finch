package dev.cao.finch.ui.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * 进场动画：淡入 + 上浮 + 轻微放大，按 [order] 依次错开。
 *
 * 「每次出现都播」：状态用 remember（不是 rememberSaveable）记账，所以
 * · LazyColumn 里滚出屏幕被回收的卡片，滑回来时会重新播一次；
 * · 切换 tab 回来后也会重新播。
 * 这正是想要的「下滑页面，新出现的卡片自己浮现」。
 *
 * 幅度是刻意做大的（上浮 [ENTER_OFFSET]dp + 从 [ENTER_SCALE] 放大到 1）：
 * 幅度小了在手机上几乎看不出来，等于没加。用 bouncy 弹簧收尾，落下时有一点回弹，
 * 卡片像被「放」到页面上而不是淡出来。
 *
 * [order] 只用来给「同屏一起出现的一批卡片」错开先后（设置页那种整页
 * 同时铺开的场景）。列表滚动时传 0 就行——从屏幕外滑进来的卡片本来就
 * 是一张张依次出现的，再叠延迟反而像卡了。
 *
 * 动画全部落在 graphicsLayer 上，不触发重新测量，滚动时不掉布局。
 */
fun Modifier.finchEnter(
    order: Int = 0,
    enabled: Boolean = true,
    /** 每张卡之间的错开延迟（毫秒） */
    step: Long = 60L,
): Modifier = composed {
    if (!enabled) return@composed this

    var shown by remember { mutableStateOf(false) }
    // translationY 的单位是像素，所以要按当前密度换算，不能写死 dp 数值
    val enterOffsetPx = with(LocalDensity.current) { ENTER_OFFSET_Dp.dp.toPx() }
    // 从透明态起步：进入组合后立刻置位，由 animateFloatAsState 补间到终态。
    // 延迟写在协程里而不是动画 spec 里，这样被打断时也不会停在半透明。
    LaunchedEffect(Unit) {
        val index = order.coerceIn(0, MAX_STAGGER)
        if (index > 0) delay(index * step)
        shown = true
    }

    val enterSpec = FinchMotion.bouncy<Float>()
    // 注意：局部名不能叫 alpha/scaleX 之类，否则在 graphicsLayer 的作用域里
    // 会被同名的接收者成员遮蔽（隐式接收者优先级高于外层局部变量）
    val alphaAnim by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = enterSpec,
        label = "finchEnterAlpha",
    )
    val offsetAnim by animateFloatAsState(
        targetValue = if (shown) 0f else enterOffsetPx,
        animationSpec = enterSpec,
        label = "finchEnterOffset",
    )
    val scaleAnim by animateFloatAsState(
        targetValue = if (shown) 1f else ENTER_SCALE,
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

/** 起点在下方多少 dp 处 */
private const val ENTER_OFFSET_Dp = 42f

/** 起点缩放：0.88 = 从「小一圈」放到原尺寸，比 0.95 明显得多 */
private const val ENTER_SCALE = 0.88f

/** 错开上限：再靠后的卡片也一起进场，不让长列表等太久 */
private const val MAX_STAGGER = 10
