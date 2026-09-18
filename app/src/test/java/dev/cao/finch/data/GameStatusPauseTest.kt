package dev.cao.finch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** v0.13：游戏状态解析 + 暂停时长口径的纯逻辑单测 */
class GameStatusPauseTest {

    @Test
    fun `status_未知回退在玩_完成态判定`() {
        assertEquals(GameStatus.PLAYING, GameStatus.fromNameOrDefault(null))
        assertEquals(GameStatus.PLAYING, GameStatus.fromNameOrDefault("JUNK"))
        assertEquals(GameStatus.COMPLETED, GameStatus.fromNameOrDefault("COMPLETED"))
        assertTrue(GameStatus.COMPLETED.isCompleted())
        assertTrue(GameStatus.MASTERED.isCompleted())
        assertFalse(GameStatus.PLAYING.isCompleted())
        assertFalse(GameStatus.WANT.isCompleted())
        assertFalse(GameStatus.PAUSED.isCompleted())
    }

    @Test
    fun `statusResolved_空status按completed回填`() {
        val done = Game(name = "x", platform = Platform.PC, completed = true)
        assertEquals(GameStatus.COMPLETED, done.statusResolved())
        val playing = Game(name = "y", platform = Platform.PC)
        assertEquals(GameStatus.PLAYING, playing.statusResolved())
        val want = Game(name = "z", platform = Platform.PC, status = GameStatus.WANT)
        assertEquals(GameStatus.WANT, want.statusResolved())
    }

    @Test
    fun `effectiveMillis_扣掉暂停累计`() {
        val start = LocalDateTime.of(2026, 9, 18, 20, 0)
        val end = start.plusHours(2) // 120 分钟
        val s = PlaySession(gameId = 1, startTime = start, endTime = end, pauseAccumMs = 10 * 60_000)
        assertEquals(110 * 60_000L, s.effectiveMillis())
    }

    @Test
    fun `effectiveMillis_暂停累计超长钳零`() {
        val start = LocalDateTime.of(2026, 9, 18, 20, 0)
        val end = start.plusMinutes(5)
        val s = PlaySession(gameId = 1, startTime = start, endTime = end, pauseAccumMs = 60 * 60_000)
        assertEquals(0L, s.effectiveMillis())
    }

    @Test
    fun `effectiveMillis_无暂停等于起止差`() {
        val start = LocalDateTime.of(2026, 9, 18, 20, 0)
        val end = start.plusMinutes(42)
        val s = PlaySession(gameId = 1, startTime = start, endTime = end)
        assertEquals(42 * 60_000L, s.effectiveMillis())
    }

    @Test
    fun `isPaused_仅计时中且有暂停起点`() {
        val start = LocalDateTime.of(2026, 9, 18, 20, 0)
        assertTrue(PlaySession(gameId = 1, startTime = start, pauseStartedAt = start.plusMinutes(1)).isPaused())
        assertFalse(PlaySession(gameId = 1, startTime = start).isPaused())
        assertFalse(
            PlaySession(gameId = 1, startTime = start, endTime = start.plusMinutes(5), pauseStartedAt = start.plusMinutes(1)).isPaused()
        )
    }
}
