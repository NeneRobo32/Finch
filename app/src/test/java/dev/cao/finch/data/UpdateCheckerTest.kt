package dev.cao.finch.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** UpdateChecker 版本比较的纯函数单测（v0.16 检查更新比对口径），JVM 直接可跑无需 Android API。 */
class UpdateCheckerTest {

    @Test
    fun `逐段数字比较_不是字符串比较`() {
        assertTrue(UpdateChecker.isNewerVersion("0.16.0", "0.15.20"))
        assertTrue(UpdateChecker.isNewerVersion("0.15.21", "0.15.20"))
        assertFalse(UpdateChecker.isNewerVersion("0.15.9", "0.15.20")) // "9" > "20" 的字符串陷阱
        assertFalse(UpdateChecker.isNewerVersion("0.15.20", "0.16.0"))
    }

    @Test
    fun `前导v忽略_同版不算新`() {
        assertFalse(UpdateChecker.isNewerVersion("v0.15.20", "0.15.20"))
        assertFalse(UpdateChecker.isNewerVersion("0.15.20", "v0.15.20"))
    }

    @Test
    fun `段数不齐按零补`() {
        assertTrue(UpdateChecker.isNewerVersion("0.16", "0.15.20"))
        assertFalse(UpdateChecker.isNewerVersion("0.15", "0.15.0"))
        assertFalse(UpdateChecker.isNewerVersion("0.15.0", "0.15"))
    }
}
