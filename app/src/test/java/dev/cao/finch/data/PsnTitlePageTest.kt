package dev.cao.finch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** PSN gameList 单页 JSON 映射（PsnClient.parseTitlePage）单测：生产入口直测，零复刻。
 *  org.json 依赖 Robolectric 环境（纯 JVM 下是 android.jar stub）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PsnTitlePageTest {

    @Test
    fun `单页映射_字段与分页游标`() {
        val body = """
            {"titles":[
              {"titleId":"PPSA01234","name":"Astro Bot","imageUrl":"https://img/1.png",
               "category":"ps5","playCount":12,
               "lastPlayedDateTime":"2026-09-13T22:00:00Z","playDuration":"PT2H30M"}
            ],"nextOffset":200}
        """.trimIndent()
        val page = PsnClient.parseTitlePage(body)
        assertEquals(200, page.nextOffset)
        assertEquals(1, page.titles.size)
        val t = page.titles[0]
        assertEquals("PPSA01234", t.titleId)
        assertEquals("Astro Bot", t.name)
        assertEquals("https://img/1.png", t.imageUrl)
        assertEquals("ps5", t.category)
        assertEquals(12, t.playCount)
        assertEquals(150L, t.totalMinutes) // PT2H30M
        assertEquals(java.time.Instant.parse("2026-09-13T22:00:00Z").toEpochMilli(), t.lastPlayedEpochMillis)
    }

    @Test
    fun `缺字段条目_兜底口径`() {
        val body = """{"titles":[{"titleId":"","name":"","playCount":0,"playDuration":""}],"nextOffset":0}"""
        val t = PsnClient.parseTitlePage(body).titles[0]
        assertNull(t.titleId) // 空串视为无
        assertEquals("PS ", t.name) // 空名回退 "PS <titleId>"
        assertEquals(0L, t.totalMinutes)
        assertNull(t.lastPlayedEpochMillis)
    }

    @Test
    fun `无titles或空页_返回空列表且游标可读`() {
        assertTrue(PsnClient.parseTitlePage("""{"nextOffset":0}""").titles.isEmpty())
        val empty = PsnClient.parseTitlePage("""{"titles":[],"nextOffset":5}""")
        assertTrue(empty.titles.isEmpty())
        assertEquals(5, empty.nextOffset)
    }
}
