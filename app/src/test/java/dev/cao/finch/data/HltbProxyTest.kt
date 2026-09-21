package dev.cao.finch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.15.6：HLTB 中转映射 + 相似度的纯逻辑单测（不联网，不碰 org.json——
 * JVM 单测跑 android.jar stub 会抛 not mocked，所以只测 mapHits/parseEntryFields 纯映射）。
 */
class HltbProxyTest {

    private fun entry(id: Long, title: String, main: Double?, extra: Double?, complete: Double?) =
        HltbProxyClient.parseEntryFields(id, title, main, extra, complete)!!

    @Test
    fun `中转条目映射_三围小时转分钟`() {
        val e = entry(68151, "Elden Ring", 60.09, 101.31, 136.19)
        assertEquals(68151L, e.hltbId)
        val t = e.toTimes()!!
        assertEquals(3605L, t.mainMin) // 60.09*60
        assertEquals(6078L, t.extraMin)
        assertEquals(8171L, t.completeMin)
        assertTrue(t.any())
    }

    @Test
    fun `中转条目映射_缺id返回null_NaN被清`() {
        assertNull(HltbProxyClient.parseEntryFields(0, "x", 1.0, 1.0, 1.0))
        val e = entry(1, "x", Double.NaN, 1.0, 1.0)!!
        assertNull(e.toTimes()?.mainMin)
        assertEquals(60L, e.toTimes()?.extraMin)
    }

    @Test
    fun `中转搜索映射_过滤低相似_无三围不要`() {
        val entries = listOf(
            entry(68151, "Elden Ring", 60.0, 100.0, 130.0),
            entry(1, "Ring Fit Adventure", 20.0, 25.0, 30.0),
            entry(2, "No Times Game", null, null, null),
        )
        val hits = HltbProxyClient.mapHits(entries, "Elden Ring")
        assertTrue(hits.isNotEmpty())
        assertEquals(68151L, hits.first().hltbId) // 最像的排第一
        assertTrue(hits.none { it.hltbId == 2L }) // 无三围的不要
    }

    @Test
    fun `中转搜索映射_数字强制`() {
        val entries = listOf(
            entry(10, "Nioh", 34.0, null, null),
            entry(11, "Nioh 2", 44.0, null, null),
        )
        val hits = HltbProxyClient.mapHits(entries, "Nioh 2")
        assertEquals(11L, hits.first().hltbId)
    }

    @Test
    fun `相似度_Gestalt基本盘`() {
        assertEquals(1.0, HltbProxyClient.gestaltRatio("abc", "abc"), 0.001)
        assertEquals(0.0, HltbProxyClient.gestaltRatio("", "abc"), 0.001)
        assertTrue(HltbProxyClient.gestaltRatio("elden ring", "elden ring") > 0.9)
        assertTrue(HltbProxyClient.similarity("Elden Ring", "Elden Ring", emptySet()) > 0.9)
        // 数字对不上扣分
        val withNum = HltbProxyClient.similarity("Nioh 2", "Nioh", setOf("2"))
        val exact = HltbProxyClient.similarity("Nioh 2", "Nioh 2", setOf("2"))
        assertTrue(exact > withNum)
    }

    @Test
    fun `HLTB直抓id抠取_保留兼容`() {
        // 手动贴 id 仍走 HltbClient.parseGameId（直抓详情页兜底在服务端，中转无条目时客户端可再直抓，暂保留）
        assertEquals(68151L, HltbClient.parseGameId("https://howlongtobeat.com/game/68151"))
        assertEquals(7231L, HltbClient.parseGameId("7231"))
    }

    @Test
    fun `库名变体_去平台后缀尾巴`() {
        // 变体逻辑在 VM（需 gameDao，单测只验正则口径）：尾巴词逐级剥
        fun strip(name: String): String {
            var cur = name.trim()
            val tails = listOf(
                "Nintendo Switch 2 Edition", "Nintendo Switch Edition", "Switch Edition",
                "PS5 Edition", "Definitive Edition", "Deluxe Edition",
            )
            var changed = true
            while (changed) {
                changed = false
                for (t in tails) {
                    if (cur.endsWith(t, ignoreCase = true) && cur.length - t.length >= 3) {
                        cur = cur.dropLast(t.length).trim().trimEnd('-', ':', '·')
                        changed = true
                        break
                    }
                }
            }
            return cur
        }
        assertEquals("异度神剑2", strip("异度神剑2 Nintendo Switch 2 Edition"))
        assertEquals("Elden Ring", strip("Elden Ring"))
        assertEquals("Hades", strip("Hades Deluxe Edition"))
    }

    @Test
    fun `eShop英文段_括号前英文`() {
        // EshopClient.searchTitles 取括号前英文段：纯逻辑冒烟（真网络由联调覆盖）
        fun enOf(title: String): String =
            title.split("（", "(").firstOrNull()?.trim().orEmpty()
        assertEquals("Xenoblade2", enOf("Xenoblade2 (ゼノブレイド2) Nintendo Switch 2 Edition"))
        assertEquals("Hollow Knight", enOf("Hollow Knight（ホロウナイト） Switch 2 Edition"))
        assertEquals("Hades", enOf("Hades"))
    }

    @Test
    fun `通关联动_参考钳到已玩`() {
        // snapRefForCompleted 口径：已玩 < 参考 → 参考钳到已玩；否则不动；无参考不动
        fun snap(playedMin: Long, refMin: Long?): Long? {
            if (refMin == null || refMin <= 0) return refMin
            return if (playedMin < refMin) playedMin.coerceAtLeast(1L) else refMin
        }
        assertEquals(300L, snap(300, 3600)) // 玩5h通关，参考钳到5h→100%
        assertEquals(3600L, snap(5000, 3600)) // 玩超了不动
        assertNull(snap(100, null))
    }
}
