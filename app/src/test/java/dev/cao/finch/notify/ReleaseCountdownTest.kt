package dev.cao.finch.notify

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** v0.15：发售倒计时文案 + 关注去重键的纯逻辑单测 */
class ReleaseCountdownTest {

    @Test
    fun `倒计时_今天明天N天后`() {
        val today = LocalDate.of(2026, 9, 19)
        assertEquals("今天发售", ReleaseCheckWorker.countdownText(today, LocalDate.of(2026, 9, 19), 3))
        assertEquals("明天发售", ReleaseCheckWorker.countdownText(today, LocalDate.of(2026, 9, 20), 3))
        assertEquals("3 天后", ReleaseCheckWorker.countdownText(today, LocalDate.of(2026, 9, 22), 3))
    }

    @Test
    fun `倒计时_超出窗口返回null`() {
        val today = LocalDate.of(2026, 9, 19)
        // 4 天后超出 notifyDays=3
        assertNull(ReleaseCheckWorker.countdownText(today, LocalDate.of(2026, 9, 23), 3))
        // 31 天前视为过期
        assertNull(ReleaseCheckWorker.countdownText(today, LocalDate.of(2026, 8, 18), 3))
    }

    @Test
    fun `倒计时_已发售不久显示天数`() {
        val today = LocalDate.of(2026, 9, 19)
        assertEquals("已发售 5 天", ReleaseCheckWorker.countdownText(today, LocalDate.of(2026, 9, 14), 3))
    }

    @Test
    fun `倒计时_notifyDays为0只响今天`() {
        val today = LocalDate.of(2026, 9, 19)
        assertEquals("今天发售", ReleaseCheckWorker.countdownText(today, today, 0))
        assertNull(ReleaseCheckWorker.countdownText(today, today.plusDays(1), 0))
    }

    @Test
    fun `去重键_bangumi优先于名字`() {
        // 键规则：bangumi:<id> / steam:<appid> / name:<归一化>——同键只存一条
        fun keyOf(bangumiId: Long?, steamAppId: Long?, name: String): String = when {
            bangumiId != null -> "bangumi:$bangumiId"
            steamAppId != null -> "steam:$steamAppId"
            else -> "name:" + name.lowercase().replace(" ", "")
        }
        assertEquals("bangumi:123", keyOf(123, null, "Xenoblade 2"))
        assertEquals("steam:440", keyOf(null, 440, "Xenoblade 2"))
        assertEquals("name:xenoblade2", keyOf(null, null, "Xenoblade 2"))
        assertEquals("name:xenoblade2", keyOf(null, null, "xenoblade  2"))
    }

    @Test
    fun `进度百分比_钳制0到100`() {
        fun frac(playedMin: Long, hltbMin: Long): Float =
            (playedMin.toFloat() / hltbMin).coerceIn(0f, 1f)
        assertEquals(0.5f, frac(20, 40))
        assertEquals(1.0f, frac(100, 40)) // 超了钳 100%
        assertEquals(0.0f, frac(0, 40))
    }
}
