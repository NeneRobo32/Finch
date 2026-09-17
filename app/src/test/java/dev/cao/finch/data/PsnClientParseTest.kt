package dev.cao.finch.data

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** PSN 协议字段解析单测：playDuration（ISO8601 PT 变体，最大单位小时）与 ISO 时间戳 */
class PsnClientParseTest {

    @Test
    fun `时长解析_完整格式`() {
        // PT243H18M48S = 243*3600+18*60+48 = 875928s → 14598 分钟（余 48s 向下取整）
        assertEquals(14598L, PsnClient.parsePlayDurationToMinutes("PT243H18M48S"))
    }

    @Test
    fun `时长解析_各种缺省变体`() {
        assertEquals(21L, PsnClient.parsePlayDurationToMinutes("PT21M18S"))
        assertEquals(1080L, PsnClient.parsePlayDurationToMinutes("PT18H"))
        assertEquals(1080L, PsnClient.parsePlayDurationToMinutes("PT18H20S"))
        assertEquals(261L, PsnClient.parsePlayDurationToMinutes("PT4H21M"))
        assertEquals(0L, PsnClient.parsePlayDurationToMinutes("PT"))
    }

    @Test
    fun `时长解析_异常输入返回0`() {
        assertEquals(0L, PsnClient.parsePlayDurationToMinutes(null))
        assertEquals(0L, PsnClient.parsePlayDurationToMinutes(""))
        assertEquals(0L, PsnClient.parsePlayDurationToMinutes("垃圾输入"))
    }

    @Test
    fun `时间戳解析_标准UTC与带毫秒`() {
        val expected = Instant.parse("2026-09-13T22:00:00Z").toEpochMilli()
        assertEquals(expected, PsnClient.parseIsoToMillis("2026-09-13T22:00:00Z"))
        assertEquals(expected, PsnClient.parseIsoToMillis("2026-09-13T22:00:00.000Z"))
    }

    @Test
    fun `时间戳解析_异常输入返回null`() {
        assertNull(PsnClient.parseIsoToMillis(null))
        assertNull(PsnClient.parseIsoToMillis(""))
        assertNull(PsnClient.parseIsoToMillis("垃圾输入"))
    }
}
