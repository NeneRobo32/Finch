package dev.cao.finch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.25 PSN 真机联调的两个修复的口径单测：
 *  ① `isPlayableCategory` —— 媒体 App 不进游戏库；
 *  ② `matchKey` —— 带 ™ 的官方名能认领到老条目。
 */
class PsnNameMatchTest {

    // ---------- ① category 过滤 ----------

    @Test
    fun `游戏类目都算可玩`() {
        assertTrue(PsnClient.isPlayableCategory("ps5_native_game"))
        assertTrue(PsnClient.isPlayableCategory("ps4_game"))
        assertTrue(PsnClient.isPlayableCategory("ps3_game"))
    }

    @Test
    fun `媒体App不算可玩`() {
        // 真机实测：账号里同时有 bilibili（web_based）与 Apple Music（native media）
        assertFalse(PsnClient.isPlayableCategory("ps5_web_based_media_app"))
        assertFalse(PsnClient.isPlayableCategory("ps5_native_media_app"))
    }

    @Test
    fun `类目缺失时按可玩处理不怕老接口漏字段`() {
        assertTrue(PsnClient.isPlayableCategory(null))
        assertTrue(PsnClient.isPlayableCategory(""))
        assertTrue(PsnClient.isPlayableCategory("   "))
    }

    // ---------- ② 名字归一认领 ----------

    @Test
    fun `商标符号不影响认领`() {
        // 真机实测的三条
        assertEquals(matchKey("Uncharted: The Nathan Drake Collection"), matchKey("Uncharted™: The Nathan Drake Collection"))
        assertEquals(matchKey("Rise of the Ronin"), matchKey("Rise of the Ronin™"))
        assertEquals(matchKey("The Last of Us Part I"), matchKey("The Last of Us™ Part I"))
        assertEquals(matchKey("ASTRO BOT"), matchKey("ASTRO BOT®"))
    }

    @Test
    fun `大小写与多余空白不影响认领`() {
        assertEquals(matchKey("CONTROL Resonant"), matchKey("control   resonant"))
        assertEquals(matchKey("Phantom Blade Zero"), matchKey("  Phantom Blade Zero  "))
    }

    @Test
    fun `不同游戏不能被并成一条`() {
        // 关键：不剥副标题、不剥版本尾巴——那是搜索口径，拿来做同一性判定会过度认领
        assertFalse(matchKey("The Last of Us Part I") == matchKey("The Last of Us Part II"))
        assertFalse(matchKey("Control") == matchKey("Control 2"))
        // 版本差异保留
        assertFalse(matchKey("Foo Remastered") == matchKey("Foo"))
    }

    @Test
    fun `装饰符号也被剥掉`() {
        assertEquals(matchKey("Zelda Breath of the Wild"), matchKey("Zelda ☆ Breath of the Wild ♪"))
    }
}
