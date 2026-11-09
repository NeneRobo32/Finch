package dev.cao.finch.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 「刚拿到白金」的识别器（v0.27）。
 *
 * PSN 只告诉你有几个白金，**不告诉你是刚拿的**——`trophyPlatinum` 被任何一次同步刷新，
 * 所以「新获得」只能自己比对：同步前记一份 `游戏id → 白金数`，同步后再读一遍，
 * 从 0 变成 ≥1 的才算。
 *
 * 为什么要记 [SettingsStore.platinumCelebratedGameIds]：不记的话每次同步都会再判成
 * 「刚拿到」，动画就天天弹。播过一次就永久记下这个游戏，之后只认真正的 0→1。
 *
 * 抽成独立的 object 而不是塞进 ViewModel，是因为全库有两条 PSN 同步入口
 * （主页下拉刷新的 [dev.cao.finch.ui.FinchViewModel.syncNow]，设置页的
 * [dev.cao.finch.ui.ImportViewModel.syncPSN]），两边都得能触发庆祝。
 */
object PlatinumWatch {

    /** 待庆祝的游戏；null = 没有。UI 播完动画调 [consume] 清掉 */
    private val _pending = MutableStateFlow<Game?>(null)
    val pending: StateFlow<Game?> = _pending

    /** 同步前拍快照：只关心挂了 psnTitleId 的游戏，别的游戏拿不到奖杯 */
    suspend fun snapshot(gameDao: GameDao): Map<Long, Int> =
        gameDao.all()
            .filter { it.psnTitleId != null }
            .associate { it.id to (it.trophyPlatinum ?: 0) }

    /**
     * 同步后挑选。
     *
     * 只认 0 → ≥1；[before] 里没有的（同步中途新建的）也算，因为之前根本没人见过它有奖杯。
     * 一次有多个同时达成时只取进度最高的一个——一次弹一堆谁也看不清。
     */
    suspend fun scan(gameDao: GameDao, settings: SettingsStore, before: Map<Long, Int>) {
        if (!settings.trophyCelebrationEnabled) return
        val celebrated = settings.platinumCelebratedGameIds
        _pending.value = gameDao.all()
            .filter { (it.trophyPlatinum ?: 0) > 0 }
            .filter { it.id.toString() !in celebrated }
            .filter { (before[it.id] ?: 0) <= 0 }
            .maxByOrNull { it.trophyProgress ?: 0 }
    }

    /** 动画播完：记住这个游戏，并清掉待庆祝状态 */
    fun consume(settings: SettingsStore, gameId: Long) {
        _pending.value = null
        settings.platinumCelebratedGameIds = settings.platinumCelebratedGameIds + gameId.toString()
    }

    /** 用户关掉开关：立刻撤掉还没播的 */
    fun cancel() {
        _pending.value = null
    }
}
