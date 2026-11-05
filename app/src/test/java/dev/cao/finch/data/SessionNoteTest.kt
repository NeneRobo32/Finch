package dev.cao.finch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 会话备注策略（v0.19.1，DB 21）：
 * 备注是一段自由文本，null = 没写；规范化负责去空白与截断。
 * v0.26.1 标签功能删除后，原 SessionTagTest 只保留这一半。
 */
class SessionNoteTest {

    @Test
    fun `空白输入视为没写`() {
        assertNull(SessionNote.normalize(null))
        assertNull(SessionNote.normalize(""))
        assertNull(SessionNote.normalize("   "))
        assertNull(SessionNote.normalize("\n\t "))
    }

    @Test
    fun `去首尾空白后原样保留内部内容`() {
        assertEquals("打到第三章", SessionNote.normalize("  打到第三章 "))
        assertEquals("第三章 BOSS 卡了两小时", SessionNote.normalize("第三章 BOSS 卡了两小时"))
    }

    @Test
    fun `超长备注截断到上限`() {
        val long = "一".repeat(SessionNote.MAX_LENGTH + 20)
        assertEquals(SessionNote.MAX_LENGTH, SessionNote.normalize(long)!!.length)
    }
}
