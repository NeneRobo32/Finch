package dev.cao.finch.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * HTML/文本正则抓取的单测（Gamersky 发售表解析、发售日期文本解析）。
 * 调用生产 parse 入口，固定 HTML 样本，不复刻口径。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HtmlParseTest {

    // ---- GamerskyKuClient：游戏库搜索/词条解析（v0.15.18 中文名反查）----

    private val kuSearchHtml = """
        <ul class="ImgY search-game-grid">
                <li>
                    <a href="https://ku.gamersky.com/2026/trails-in-the-sky-2nd-chapter/" target="_blank">
                        <div class="img">
                            <img src="https://imgs.gamersky.com/x.jpg" title="空之轨迹 the 2nd" alt="空之轨迹 the 2nd" />
                        </div>
                    </a>
                </li>
                <li>
                    <a href="https://ku.gamersky.com/2025/trails-in-the-sky-the-1st/" target="_blank">
                        <div class="img">
                            <img src="https://imgs.gamersky.com/y.jpg" title="空之轨迹 the 1st" alt="空之轨迹 the 1st" />
                        </div>
                    </a>
                </li>
        </ul>
    """.trimIndent()

    @Test
    fun `parseSearchResults_词条链接与中文名配对_无结果空表`() {
        val cands = GamerskyKuClient.parseSearchResults(kuSearchHtml)
        assertEquals(2, cands.size)
        assertEquals("空之轨迹 the 2nd", cands[0].title)
        assertEquals("https://ku.gamersky.com/2026/trails-in-the-sky-2nd-chapter/", cands[0].url)
        assertEquals("空之轨迹 the 1st", cands[1].title)
        assertTrue(GamerskyKuClient.parseSearchResults("<html>啥也没有</html>").isEmpty())
    }

    @Test
    fun `parseEntry_metaKeywords取英文名_首个Steam链接是本体`() {
        val html = """
            <meta name="keywords" content="空之轨迹 the 2nd,Trails in the Sky 2nd Chapter,空之轨迹 the 2nd下载,空之轨迹 the 2nd配置" />
            <a href="https://store.steampowered.com/app/4225980/?utm_source=gamersky.com">前往商店</a>
            <a href="https://store.steampowered.com/app/5009480/">DLC</a>
        """.trimIndent()
        val e = GamerskyKuClient.parseEntry(html)!!
        // 「XX下载/XX配置」中文噪声项被 isHltbSearchable 口径过滤，取到正式英文名
        assertEquals("Trails in the Sky 2nd Chapter", e.englishName)
        assertEquals(4225980L, e.steamAppId) // 首个 = 本体，不是 DLC 的 5009480
    }

    @Test
    fun `parseEntry_任天堂独占无Steam链接_仍取英文名`() {
        // NS2 独占（如马力欧卡丁车世界）没有 Steam 版，英文名一路照样能送 HLTB 按名搜
        val html = """<meta name="keywords" content="马里奥赛车世界,Mario Kart World,马里奥赛车世界下载" />"""
        val e = GamerskyKuClient.parseEntry(html)!!
        assertEquals("Mario Kart World", e.englishName)
        assertNull(e.steamAppId)
        assertNull(GamerskyKuClient.parseEntry("<html>三无页面</html>"))
    }

    private val sampleHtml = """
        <ul>
        <li class="lx1">
          <div class="img"><img src="//img.gamersky.com/cover1.jpg" /></div>
          <div class="tit"><a href="/game/1">测试游戏<b>特别版</b></a></div>
          <div class="dt">发行日期：<span>2027-01-15</span></div>
          <div class="pub">制作发行：<a>某某公司</a></div>
          <div class="gn">游戏类型：<a>角色扮演</a></div>
          <div class="num">66</div>
        </li>
        <li class="lx2">
          <div class="tit"><a href="/game/2">另一款游戏</a></div>
          <div class="dt">发行日期：2026年12月</div>
          <div class="rt" data-RatingTotal="88"></div>
        </li>
        </ul>
    """.trimIndent()

    @Test
    fun `parse_发售表HTML_条目字段完整抓取`() {
        val items = GamerskyClient.parse(sampleHtml)
        assertEquals(2, items.size)

        val a = items[0]
        assertEquals("测试游戏特别版", a.name) // 标签剥离
        assertEquals("https://img.gamersky.com/cover1.jpg", a.coverUrl) // 协议相对 URL 补 https:
        assertEquals(LocalDate.of(2027, 1, 15), a.releaseDate)
        assertEquals("某某公司", a.publisher)
        assertEquals("角色扮演", a.genre)
        assertEquals(66, a.expectation) // 当月页走 <div class="num">

        val b = items[1]
        assertEquals("另一款游戏", b.name)
        assertEquals(LocalDate.of(2026, 12, 1), b.releaseDate) // 年月粒度落 1 日
        assertEquals(88, b.expectation) // 完整日历页走 data-RatingTotal
    }

    @Test
    fun `parse_缺tit块跳过_无块返回空`() {
        val html = """<li class="lx1"><div class="num">1</div></li>"""
        assertTrue(GamerskyClient.parse(html).isEmpty())
        assertTrue(GamerskyClient.parse("<html>no list</html>").isEmpty())
    }

    @Test
    fun `parseRelease_日期文本各粒度`() {
        assertEquals(LocalDate.of(2027, 1, 15), GamerskyClient.parseRelease("2027-01-15"))
        assertEquals(LocalDate.of(2026, 12, 1), GamerskyClient.parseRelease("2026年12月"))
        assertEquals(LocalDate.of(2026, 1, 1), GamerskyClient.parseRelease("2026年"))
        assertEquals(LocalDate.of(2027, 1, 1), GamerskyClient.parseRelease("2027年Q1"))
        assertNull(GamerskyClient.parseRelease("待定"))
    }
}
