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
        val e = entry(1, "x", Double.NaN, 1.0, 1.0)
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
        // 生产口径：data/NameVariants.kt 的 stripPlatformTails（buildNameVariants 的剥尾巴一环，逐级剥）
        assertEquals("异度神剑2", stripPlatformTails("异度神剑2 Nintendo Switch 2 Edition"))
        assertEquals("Elden Ring", stripPlatformTails("Elden Ring"))
        assertEquals("Hades", stripPlatformTails("Hades Deluxe Edition"))
    }

    @Test
    fun `eShop英文段_括号前拉丁_纯日文丢弃`() {
        // 生产口径：EshopClient.latinSegment（internal 纯函数，不联网，直接调用）
        assertEquals("Xenoblade2", EshopClient.latinSegment("Xenoblade2 (ゼノブレイド2) Nintendo Switch 2 Edition"))
        assertEquals("Hollow Knight", EshopClient.latinSegment("Hollow Knight（ホロウナイト） Switch 2 Edition"))
        assertEquals("Hades", EshopClient.latinSegment("Hades"))
        assertNull(EshopClient.latinSegment("ゼルダの伝説"))
        assertNull(EshopClient.latinSegment("异度神剑2"))
        assertNull(EshopClient.latinSegment(""))
    }

    @Test
    fun `通关联动_参考钳到已玩`() {
        // 生产口径：data/GameProgress.kt 的 snapRefForCompleted（FinchViewModel.snapProgressToCompleted 调用）：
        // 已玩 < 参考 → 参考钳到已玩；否则不动；无参考不动
        assertEquals(300L, snapRefForCompleted(300, 3600)) // 玩5h通关，参考钳到5h→100%
        assertEquals(3600L, snapRefForCompleted(5000, 3600)) // 玩超了不动
        assertNull(snapRefForCompleted(100, null))
    }

    @Test
    fun `Nlib_TitleID校验_16位十六进制`() {
        assertTrue(SwitchTitleClient.isTitleId("0100E95004038000"))
        assertTrue(SwitchTitleClient.isTitleId("01007EF00011E000"))
        assertTrue(SwitchTitleClient.isTitleId("  0100e95004038000  ")) // 大小写+空白容忍
        assertEquals(false, SwitchTitleClient.isTitleId(null))
        assertEquals(false, SwitchTitleClient.isTitleId(""))
        assertEquals(false, SwitchTitleClient.isTitleId("app123")) // 短 id
        assertEquals(false, SwitchTitleClient.isTitleId("0100E9500403800G")) // 非十六进制
        assertEquals(false, SwitchTitleClient.isTitleId("0100E950040380000")) // 17 位
    }

    @Test
    fun `Nlib_响应解析_取name_缺字段null`() {
        // pickName 纯逻辑（parseName 碰 org.json，JVM stub 跑不了，只测 pickName）
        assertEquals("Xenoblade Chronicles 2", SwitchTitleClient.pickName("Xenoblade Chronicles 2"))
        assertNull(SwitchTitleClient.pickName(""))
        assertNull(SwitchTitleClient.pickName(null))
    }

    @Test
    fun `Nlib_非法id不发请求_直接null`() {
        // fetchEnglishName 非法格式直接返回 null（纯逻辑路径，不联网）
        assertNull(SwitchTitleClient.fetchEnglishName(null))
        assertNull(SwitchTitleClient.fetchEnglishName(""))
        assertNull(SwitchTitleClient.fetchEnglishName("app123"))
    }

    // ---- 英文名来源口径（v0.15.15：HLTB 只认英文名，中文/日文库名换英文线索）----

    @Test
    fun `TitleID变体归一_带后缀取前16位_非TitleID形态null`() {
        // 生产口径：SwitchTitleClient.normalizeTitleId（Moon 的 applicationId 有带后缀变体）
        assertEquals("0100E95004038000", SwitchTitleClient.normalizeTitleId("0100E95004038000"))
        assertEquals("0100E95004038000", SwitchTitleClient.normalizeTitleId("  0100e95004038000  ")) // 大小写+空白容忍
        assertEquals("0100E95004038000", SwitchTitleClient.normalizeTitleId("0100E95004038000_002")) // 带后缀变体
        assertNull(SwitchTitleClient.normalizeTitleId("app123")) // 短 id
        assertNull(SwitchTitleClient.normalizeTitleId("70010000012345")) // 纯数字 nsuId
        assertNull(SwitchTitleClient.normalizeTitleId("0100E9500403800G")) // 15 位十六进制 + 非法字符
        assertNull(SwitchTitleClient.normalizeTitleId(null))
    }

    @Test
    fun `商标符号剥离_Nlib官方名送搜前`() {
        // 生产口径：NameVariants.kt 的 stripMarks
        assertEquals(
            "The Legend of Zelda: Breath of the Wild",
            stripMarks("The Legend of Zelda™: Breath of the Wild"),
        )
        assertEquals("Game", stripMarks("Game®©"))
        assertEquals("少女歌劇 レヴュースタァライト", stripMarks("少女☆歌劇 レヴュースタァライト")) // 装饰符号剥掉
        assertEquals("Hollow Knight", stripMarks("Hollow  Knight")) // 顺带压缩连续空白
        assertEquals("plain", stripMarks("plain"))
    }

    @Test
    fun `拉丁判定_无拉丁名字不可直搜HLTB`() {
        // 生产口径：NameVariants.kt 的 hasLatin（HLTB 能否按名搜的真正标准）。
        // 回归用例：v0.15.15 的「纯 CJK」判定会把带 ☆ 等符号的日文名误判为可直搜——
        // 原名进了查询词、Steam/Bangumi/机翻三级全被跳过（用户实测「试了日文中文，就是没有英文」）
        assertTrue(hasLatin("Hollow Knight"))
        assertTrue(hasLatin("怪物猎人Rise")) // 混拉丁可直搜
        assertTrue(!hasLatin("塞尔达传说"))
        assertTrue(!hasLatin("ゼルダの伝説"))
        assertTrue(!hasLatin("몬스터 헌터"))
        assertTrue(!hasLatin("异度神剑3")) // 数字不是拉丁字母，HLTB 搜不了
        assertTrue(!hasLatin("少女☆歌劇 レヴュースタァライト")) // 符号混排仍是不可直搜
        assertTrue(!hasLatin(""))
    }

    @Test
    fun `假名谚文判定_决定机翻方向`() {
        // 生产口径：NameVariants.kt 的 hasKana/hasHangul + TitleTranslateClient.sourceLang
        assertTrue(hasKana("ゼルダの伝説 ティアーズ"))
        assertTrue(!hasKana("塞尔达传说"))
        assertTrue(!hasKana("Zelda"))
        assertTrue(hasHangul("몬스터 헌터"))
        assertTrue(!hasHangul("怪物猎人"))
        // 机翻方向：日文名 ja→en（音译还原率高）、韩文名 ko→en、其余 zh-CN→en；混排名按最具体先判
        assertEquals("ja", TitleTranslateClient.sourceLang("ゼルダの伝説 ティアーズ"))
        assertEquals("ja", TitleTranslateClient.sourceLang("少女☆歌劇 レヴュースタァライト"))
        assertEquals("ko", TitleTranslateClient.sourceLang("몬스터 헌터"))
        assertEquals("zh-CN", TitleTranslateClient.sourceLang("怪物猎人 崛起"))
    }

    @Test
    fun `Steam中文反查_相似最高者胜出_全角数字归一`() {
        // 生产口径：SteamStoreClient.pickAppId（中文名命中 Steam 中文索引 → appid 直查 HLTB）
        val items = listOf(
            SteamStoreClient.Result(1687950L, "女神异闻录5皇家版", null),
            SteamStoreClient.Result(2254740L, "女神异闻录５ 战略版", null),
        )
        assertEquals(1687950L, SteamStoreClient.pickAppId("女神异闻录5 皇家版", items)?.appid)
        assertEquals(2254740L, SteamStoreClient.pickAppId("女神异闻录５ 战略版", items)?.appid) // 全角数字归一后命中
    }

    @Test
    fun `Steam中文反查_数字强制与低相似不认_宁漏勿错`() {
        val items = listOf(
            SteamStoreClient.Result(1687950L, "女神异闻录5皇家版", null),
            SteamStoreClient.Result(367520L, "空洞骑士", null),
        )
        assertNull(SteamStoreClient.pickAppId("女神异闻录3", items)) // 3 代不认 5 代：appid 直查命中即写库
        assertNull(SteamStoreClient.pickAppId("怪物猎人 崛起", items)) // 低相似不认
        assertNull(SteamStoreClient.pickAppId("", items))
    }
}
