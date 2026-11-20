package dev.cao.finch.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.Flow

/**
 * 像 [androidx.compose.runtime.collectAsState] 一样收 Flow，但**换源时保留上一份数据**，
 * 等新查询回来再整体替换。
 *
 * 为什么不直接用 `collectAsState(initial = ...)`：换周 / 换月 / 换年时会
 * `remember(窗口) { viewModel.xxx(窗口) }` 建一个新 Flow，新 Flow 的第一发还没回来，
 * 界面先拿到的是 `initial`（空列表 / 0），于是卡片「空一帧再填满」，看着就是抽一下；
 * 更糟的是不少卡片用 `list.isNotEmpty()` 当显示条件，空这一帧会让整张卡先消失再长回来，
 * 高度一跳比数字跳还明显。
 *
 * 这里把上一次的值留住，新值到了才覆盖；只有真的没有数据时才显示空态。
 *
 * 注意：只解决「新 Flow 首帧未到」这一种情况。如果下游还要拿**新的窗口**去摊分旧数据
 * （比如作息卡要把会话摊进某一周的桶里），光保留数据不够，得把窗口和数据绑成一份一起保留，
 * 见 `StatsScreen` 里的 `RhythmWindow`。
 */
@Composable
internal fun <T> Flow<T>.collectRetaining(initial: T): State<T> {
    // 注意 state 不能 remember(this)：换 Flow 就重建的话又回到 initial 了
    val state = remember { mutableStateOf(initial) }
    LaunchedEffect(this) { collect { state.value = it } }
    return state
}

/**
 * 一「份」作息数据：窗口与那一窗的会话绑在一起。
 *
 * 光保留会话不够——换周后窗口立刻变成新的一周，旧会话全部落在窗口外，
 * [partitionRhythm] 会把它们裁光，柱状图先塌成空白再长回来。
 * 窗口跟着会话一起换，界面才是一整份地替换。
 */
internal data class RhythmWindow(
    val from: Long,
    val to: Long,
    val spans: List<dev.cao.finch.data.SessionSpan>,
)
