package dev.cao.finch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.15.5：HLTB 解析（详情直抓 + Bangumi 联动 id 解析）的纯逻辑单测（不联网） */
class HltbTest {

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
