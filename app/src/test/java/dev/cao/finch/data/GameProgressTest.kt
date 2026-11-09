package dev.cao.finch.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 通关进度的派生量（v0.27）：
 * [progressFraction] 会把比例钳到 1，所以「超出多少」「还差多少」必须单独算，
 * 这里锁住三个函数在同一组输入下的互斥关系。
 */
class GameProgressTest {

    @Test
    fun `没到主线参考_还差当作差_超出为0`() {
        assertEquals(0L, mainOverrunMin(playedMin = 300, hltbMin = 1200))
        assertEquals(900L, mainRemainingMin(playedMin = 300, hltbMin = 1200))
    }

    @Test
    fun `超过主线参考_超出当作差_还差为0`() {
        assertEquals(240L, mainOverrunMin(playedMin = 1440, hltbMin = 1200))
        assertEquals(0L, mainRemainingMin(playedMin = 1440, hltbMin = 1200))
    }

    @Test
    fun `正好打平_两边都是0`() {
        assertEquals(0L, mainOverrunMin(playedMin = 1200, hltbMin = 1200))
        assertEquals(0L, mainRemainingMin(playedMin = 1200, hltbMin = 1200))
    }

    @Test
    fun `超出多少与进度条封顶无关`() {
        // 进度条永远是 1.0，但「超了多少」还得能读出来——这是它单独存在的原因
        assertEquals(1f, progressFraction(playedMin = 2400, hltbMin = 1200))
        assertEquals(1200L, mainOverrunMin(playedMin = 2400, hltbMin = 1200))
    }

    @Test
    fun `脏数据负数不会算成负数`() {
        assertEquals(0L, mainOverrunMin(playedMin = 0, hltbMin = 600))
        assertEquals(0L, mainRemainingMin(playedMin = 0, hltbMin = -1))
    }
}
