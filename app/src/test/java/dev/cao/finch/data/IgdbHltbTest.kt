package dev.cao.finch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.15.1：IGDB 平台映射/结果映射 + HLTB 解析的纯逻辑单测（不联网，不碰 org.json——
 * JVM 单测跑 android.jar stub 会抛 not mocked，所以 IGDB 只测 mapResults 纯映射）。
 */
class IgdbHltbTest {

    // ---- IGDB 纯映射（RawGame → Result，不过 org.json） ----

    @Test
    fun `IGDB映射_封面平台首发`() {
        val raw = listOf(
            IgdbClient.RawGame(
                igdbId = 1942,
                name = "The Witcher 3",
                coverImageId = "abc123",
                platformNames = listOf("PC", "PlayStation 4"),
                firstReleaseUnix = 1431909600,
            )
        )
        val r = IgdbClient.mapResults(raw)
        assertEquals(1, r.size)
        assertEquals(1942L, r[0].igdbId)
        assertEquals("https://images.igdb.com/igdb/image/upload/t_cover_big/abc123.jpg", r[0].coverUrl)
        assertEquals(setOf(Platform.PC, Platform.PS), r[0].platforms)
        assertEquals(1431909600L, r[0].firstReleaseUnix)
    }

    @Test
    fun `IGDB映射_Xbox被过滤_空平台保留条目`() {
        val raw = listOf(
            IgdbClient.RawGame(99, "Halo", null, listOf("Xbox One"), null)
        )
        val r = IgdbClient.mapResults(raw)
        assertEquals(1, r.size)
        assertTrue(r[0].platforms.isEmpty()) // 调用方 ifEmpty{Multi}
        assertNull(r[0].coverUrl)
    }

    @Test
    fun `IGDB平台映射_Switch_PS_PC`() {
        assertEquals(Platform.PC, IgdbClient.mapPlatformName("PC (Microsoft Windows)"))
        assertEquals(Platform.PC, IgdbClient.mapPlatformName("Steam"))
        assertEquals(Platform.SWITCH, IgdbClient.mapPlatformName("Nintendo Switch"))
        assertEquals(Platform.PS, IgdbClient.mapPlatformName("PlayStation 5"))
        assertEquals(Platform.PS, IgdbClient.mapPlatformName("PS4"))
        assertNull(IgdbClient.mapPlatformName("Xbox Series X"))
        assertNull(IgdbClient.mapPlatformName("iOS"))
        assertNull(IgdbClient.mapPlatformName(""))
    }

    @Test
    fun `IGDB鉴权_过期提前一天刷新`() {
        val now = 1_000_000_000L
        assertTrue(IgdbAuth.needRefresh(0, now))
        assertTrue(IgdbAuth.needRefresh(now + 23 * 3600_000L, now)) // 剩23h→刷
        assertEquals(false, IgdbAuth.needRefresh(now + 25 * 3600_000L, now)) // 剩25h→不刷
    }

    // ---- HLTB 解析（走 HltbClient 真方法，拼 HTML/展示值） ----

    @Test
    fun `HLTB解析_中位数优先`() {
        val html = """"game_id":68151,"comp_main_med":216000,"comp_plus_med":353190,"comp_100_med":468000"""
        val t = HltbClient.parseTimes(html)!!
        assertEquals(3600L, t.mainMin) // 216000s=60h=3600min
        assertEquals(5886L, t.extraMin) // 353190/60
        assertEquals(7800L, t.completeMin)
        assertTrue(t.any())
    }

    @Test
    fun `HLTB解析_h45兜底`() {
        val html = "<h4>Main Story</h4><h5>60 Hours</h5><h4>Completionist</h4><h5>--</h5>"
        val t = HltbClient.parseTimes(html)!!
        assertEquals(3600L, t.mainMin)
        assertNull(t.extraMin)
        assertNull(t.completeMin)
    }

    @Test
    fun `HLTB解析_两路全空返回null`() {
        assertNull(HltbClient.parseTimes("<html>no data</html>"))
    }

    @Test
    fun `HLTB展示值_半小时分钟未知`() {
        assertEquals(44.5, HltbClient.parseShownHours("44½ Hours")!!, 0.001)
        assertEquals(1.0, HltbClient.parseShownHours("50 Mins")!!, 0.001)
        assertNull(HltbClient.parseShownHours("--"))
        assertNull(HltbClient.parseShownHours("???"))
    }

    @Test
    fun `HLTBid_纯数字链接game参数`() {
        assertEquals(68151L, HltbClient.parseGameId("68151"))
        assertEquals(68151L, HltbClient.parseGameId("https://howlongtobeat.com/game/68151"))
        assertEquals(7231L, HltbClient.parseGameId("https://howlongtobeat.com/game?id=7231"))
        assertNull(HltbClient.parseGameId(""))
        assertNull(HltbClient.parseGameId("abc"))
    }
}
