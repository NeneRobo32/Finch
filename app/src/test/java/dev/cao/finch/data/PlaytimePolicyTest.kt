package dev.cao.finch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * v0.18.3：单款游戏「累计时长」单一显示口径的纯逻辑单测。
 * 有云平台官方累计时一律以官方为准（PS→PSN、PC→Steam），本地会话只作历史明细。
 */
class PlaytimePolicyTest {

    @Test
    fun `ps游戏_以psn同步时间为准_不论本地会话多少`() {
        val ps = Game(name = "Bloodborne", platform = Platform.PS, psnPlaytimeMin = 210 * 60)
        // 本地会话远小于 PSN（首次导入前的时长没进会话）
        assertEquals(210 * 60L, ps.effectivePlaytimeMin(42 * 60))
        // 本地计时反而更多，也统一按 PSN
        assertEquals(210 * 60L, ps.effectivePlaytimeMin(300 * 60))
        assertEquals("PSN", ps.cloudPlaytimeSource())
    }

    @Test
    fun `steam游戏_以steam库累计为准`() {
        val pc = Game(name = "Hades", platform = Platform.PC, steamPlaytimeMin = 90 * 60)
        assertEquals(90 * 60L, pc.effectivePlaytimeMin(10 * 60))
        assertEquals("Steam", pc.cloudPlaytimeSource())
    }

    @Test
    fun `双云时长_PSN优先`() {
        val both = Game(
            name = "跨平台游戏",
            platform = Platform.PC,
            platformsCsv = "PC,PS",
            steamPlaytimeMin = 30 * 60,
            psnPlaytimeMin = 50 * 60,
        )
        assertEquals(50 * 60L, both.effectivePlaytimeMin(1 * 60))
        assertEquals("PSN", both.cloudPlaytimeSource())
    }

    @Test
    fun `无云数据_回退本地会话累计`() {
        val local = Game(name = "自制游戏", platform = Platform.PC)
        assertEquals(12 * 60L, local.effectivePlaytimeMin(12 * 60))
        assertNull(local.cloudPlaytimeSource())
    }

    @Test
    fun `云时长为零_视为未同步_回退本地会话`() {
        val zero = Game(name = "刚入库", platform = Platform.PS, psnPlaytimeMin = 0)
        assertEquals(5 * 60L, zero.effectivePlaytimeMin(5 * 60))
        assertNull(zero.cloudPlaytimeSource())
    }
}
