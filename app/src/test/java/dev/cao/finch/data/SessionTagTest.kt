package dev.cao.finch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 会话标签策略（v0.19，DB 20）：
 * 标签是一个短字符串，null = 未分类；规范化负责去空白与截断。
 */
class SessionTagTest {

    @Test
    fun `预设标签是四档且不含未分类`() {
        assertEquals(listOf("主线", "支线", "联机", "多周目"), SessionTags.PRESETS)
    }

    @Test
    fun `空白输入视为未分类`() {
        assertNull(SessionTags.normalize(null))
        assertNull(SessionTags.normalize(""))
        assertNull(SessionTags.normalize("   "))
    }

    @Test
    fun `去首尾空白后原样保留`() {
        assertEquals("主线", SessionTags.normalize("  主线 "))
        assertEquals("二周目速通", SessionTags.normalize("二周目速通"))
    }

    @Test
    fun `超长标签截断到上限`() {
        val long = "一二三四五六七八九十"
        assertEquals(SessionTags.MAX_LENGTH, SessionTags.normalize(long)!!.length)
        assertEquals("一二三四五六七八", SessionTags.normalize(long))
    }

    @Test
    fun `预设标签本身不会被规范化改动`() {
        SessionTags.PRESETS.forEach { assertEquals(it, SessionTags.normalize(it)) }
    }
}
