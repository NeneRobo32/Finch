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
