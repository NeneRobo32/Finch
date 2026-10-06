package dev.cao.finch.data

/**
 * 通关进度联动的纯口径（自 FinchViewModel / GameDetailScreen 平移，行为零变化）。
 * 抽成生产纯函数供 VM、UI 与单测共用，防止测试复刻与生产口径漂移。
 */

/**
 * 通关联动封顶：已玩 < 参考 → 参考钳到已玩（至少 1 分钟）；否则不动；无参考不动。
 * 勾通关瞬间进度 100% 的「done 即现」逻辑用此口径（FinchViewModel.snapProgressToCompleted 调用）。
 */
internal fun snapRefForCompleted(playedMin: Long, refMin: Long?): Long? {
    if (refMin == null || refMin <= 0) return refMin
    return if (playedMin < refMin) playedMin.coerceAtLeast(1L) else refMin
}

/**
 * HLTB 通关进度百分比：已玩 / 主线参考，钳制 0..1（GameDetailScreen 进度条用）。
 * 调用方保证 hltbMin > 0（进度卡只在有参考时展示）。
 */
internal fun progressFraction(playedMin: Long, hltbMin: Long): Float =
    (playedMin.toFloat() / hltbMin).coerceIn(0f, 1f)
