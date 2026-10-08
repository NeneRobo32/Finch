package dev.cao.finch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * org.json 解析路径的单测（Robolectric 提供真实 org.json，android.jar stub 测不到这些路径）。
 * 全部调用生产解析入口（internal/public），不复刻口径。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JsonParseTest {

    // ---- HltbProxyClient：中转条目 JSON → Entry ----

    @Test
    fun `parseEntry_完整条目_三围正确映射`() {
        val e = HltbProxyClient.parseEntry(
            """{"hltbId":68151,"title":"Hollow Knight","mainStory":8.5,"mainStoryWithExtras":25.25,"completionist":44.5}"""
        )
        assertNotNull(e)
        assertEquals(68151L, e!!.hltbId)
        assertEquals("Hollow Knight", e.title)
        assertEquals(8.5, e.mainStory!!, 0.001)
        assertEquals(25.25, e.mainExtra!!, 0.001)
        assertEquals(44.5, e.completionist!!, 0.001)
        val t = e.toTimes()
        assertNotNull(t)
        assertEquals(510L, t!!.mainMin)     // 8.5h
        assertEquals(1515L, t.extraMin)     // 25.25h
        assertEquals(2670L, t.completeMin)  // 44.5h
    }

    @Test
    fun `parseEntry_缺hltbId返回null_非法JSON返回null`() {
        assertNull(HltbProxyClient.parseEntry("""{"title":"X","mainStory":1.0}"""))
        assertNull(HltbProxyClient.parseEntry("not json"))
        assertNull(HltbProxyClient.parseEntry(""))
    }

    @Test
    fun `parseEntry_缺三围_字段与toTimes均为null不外泄NaN`() {
        val e = HltbProxyClient.parseEntry("""{"hltbId":5,"title":"X"}""")
        assertNotNull(e)
        assertNull(e!!.mainStory)
        assertNull(e.mainExtra)
        assertNull(e.completionist)
        assertNull(e.toTimes())
    }

    @Test
    fun `parseSearchResult_裸数组_按相似度过滤`() {
        val json = """
            [
              {"hltbId":1,"title":"Hollow Knight","mainStory":8.5},
              {"hltbId":2,"title":"完全不同的名字","mainStory":1.0}
            ]
        """.trimIndent()
        val hits = HltbProxyClient.parseSearchResult(json, "Hollow Knight")
        assertEquals(1, hits.size)
        assertEquals(1L, hits[0].hltbId)
        assertEquals(510L, hits[0].times.mainMin)
    }

    @Test
    fun `parseSearchResult_data包裹兼容_非法输入返回空`() {
        val wrapped = """{"data":[{"hltbId":9,"title":"Hades","mainStory":2.0}]}"""
        val hits = HltbProxyClient.parseSearchResult(wrapped, "Hades")
        assertEquals(1, hits.size)
        assertEquals(9L, hits[0].hltbId)
        assertTrue(HltbProxyClient.parseSearchResult("not json", "x").isEmpty())
    }

    // ---- BangumiClient：发售日历 JSON → CalendarEntry ----

    private fun calendarItem(
        type: Int = 4,
        id: Long = 101,
        name: String = "Game A",
        nameCn: String? = "游戏A",
        date: String? = "2027-02-01",
        images: String = """{"common":"http://c/a.png"}""",
        platform: String = """[{"name_cn":"Nintendo Switch"}]""",
    ): String = buildString {
        append("""{"type":$type,"id":$id,"name":"$name"""")
        if (nameCn != null) append(""","name_cn":"$nameCn"""")
        if (date != null) append(""","date":"$date"""")
        append(""","images":$images,"platform":$platform}""")
    }

    @Test
    fun `parseCalendar_只收游戏_type4_按日期升序_字段正确映射`() {
        val json = """
            [
              {"items":[
                ${calendarItem(id = 101, name = "Game A", nameCn = "游戏A", date = "2027-02-01")},
                {"type":2,"id":102,"name":"Book B","date":"2027-01-01"},
                ${calendarItem(id = 103, name = "Game C", nameCn = null, date = "2027-01-15", images = """{"large":"http://c/c.png"}""", platform = """["PC"]""")}
              ]}
            ]
        """.trimIndent()
        val list = BangumiClient.parseCalendar(json)
        assertEquals(2, list.size) // type=2 的书籍被过滤
        // 日期升序：01-15 在 02-01 前
        val c = list[0]
        assertEquals("Game C", c.name)
        assertEquals(null, c.nameCn)
        assertEquals("2027-01-15", c.date)
        assertEquals("http://c/c.png", c.coverUrl) // common 缺失走 large
        assertEquals(listOf("PC"), c.platforms)    // 字符串数组形态
        assertEquals(103L, c.bangumiId)
        val a = list[1]
        assertEquals("游戏A", a.nameCn)
        assertEquals("http://c/a.png", a.coverUrl)
        assertEquals(listOf("Nintendo Switch"), a.platforms) // 对象数组形态
        assertEquals(101L, a.bangumiId)
    }

    @Test
    fun `parseCalendar_data包裹兼容_缺日期排最后_垃圾输入向上抛`() {
        val wrapped = """{"data":[{"items":[${calendarItem(id = 1, name = "NoDate", date = null)}]}]}"""
        val list = BangumiClient.parseCalendar(wrapped)
        assertEquals(1, list.size)
        assertEquals(null, list[0].date)
        // 生产行为如实记录：完全非法的输入由内层 JSONArray 再抛 JSONException（调用方 runCatching 兜底），
        // parseCalendar 本身不吞——此处锁定现状，若将来改为防御式返回空可同步更新断言
        org.junit.Assert.assertThrows(org.json.JSONException::class.java) {
            BangumiClient.parseCalendar("not json")
        }
    }

    // ---- SwitchTitleClient：Nlib 响应 JSON → name ----

    @Test
    fun `parseName_正常返回name字段`() {
        assertEquals("Xenoblade 2", SwitchTitleClient.parseName("""{"name":"Xenoblade 2"}"""))
    }

    @Test
    fun `parseName_空缺字段与非法JSON返回null`() {
        assertNull(SwitchTitleClient.parseName("""{"name":""}"""))
        assertNull(SwitchTitleClient.parseName("""{}"""))
        assertNull(SwitchTitleClient.parseName("not json"))
    }

    @Test
    fun `isTitleId_16位十六进制判定`() {
        assertTrue(SwitchTitleClient.isTitleId("0100E95004038000"))
        assertTrue(SwitchTitleClient.isTitleId(" 0100e95004038000 "))
        assertTrue(SwitchTitleClient.isTitleId("0100abcdef000000"))
        assertTrue(!SwitchTitleClient.isTitleId("1234"))
        assertTrue(!SwitchTitleClient.isTitleId("0100E950040380001")) // 17 位
        assertTrue(!SwitchTitleClient.isTitleId("0100E9500403800G")) // 非十六进制
        assertTrue(!SwitchTitleClient.isTitleId(null))
        assertTrue(!SwitchTitleClient.isTitleId(""))
    }

    // ---- TitleTranslateClient：MyMemory 响应 → 英文候选（v0.15.15 机翻兜底）----

    @Test
    fun `pickTranslations_主译文优先_高匹配补位_例句噪声不收`() {
        val json = """
            {"responseData":{"translatedText":"Legend of Zelda Tears of the Kingdom"},
             "matches":[
               {"translation":"FINAL FANTASY XV ROYAL EDITION INCLUDES:","match":0.21},
               {"translation":"This is clearly a long example sentence from translation memory, not a game title at all","match":0.9},
               {"translation":"The Legend of Zelda","match":0.4},
               {"translation":"ZELDA","match":0.48}
             ]}
        """.trimIndent()
        val out = TitleTranslateClient.pickTranslations(json)
        // 主译文优先，记忆库高匹配条目按 match 降序补位（最多 3 个）
        assertEquals(
            listOf("Legend of Zelda Tears of the Kingdom", "ZELDA", "The Legend of Zelda"),
            out,
        )
        // 低匹配例句（match<0.4）与长句（>60 字符）都不收
        assertTrue(out.none { it.contains("FINAL FANTASY") || it.contains("example sentence") })
    }

    @Test
    fun `pickTranslations_非法JSON_空译文_非拉丁返回空`() {
        assertTrue(TitleTranslateClient.pickTranslations("not json").isEmpty())
        assertTrue(TitleTranslateClient.pickTranslations("""{"responseData":{"translatedText":""}}""").isEmpty())
        assertTrue(TitleTranslateClient.pickTranslations("""{"responseData":{"translatedText":"完全中文译文"}}""").isEmpty())
    }

    @Test
    fun `pickTranslations_剥商标_去重保序`() {
        val json = """
            {"responseData":{"translatedText":"Game™"},
             "matches":[{"translation":"Game","match":0.9}]}
        """.trimIndent()
        assertEquals(listOf("Game"), TitleTranslateClient.pickTranslations(json))
    }
}
