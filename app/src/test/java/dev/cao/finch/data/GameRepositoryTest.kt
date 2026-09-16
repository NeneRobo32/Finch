package dev.cao.finch.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** 多平台 CSV 编解码、平台归属与合并判断的纯逻辑单测 */
class GameRepositoryTest {

    @Test
    fun `CSV 解码_未知平台名忽略_空安全`() {
        assertEquals(listOf(Platform.PC, Platform.SWITCH), GameRepository.csvToPlatforms("PC,SWITCH,JUNK"))
        assertEquals(listOf(Platform.PS), GameRepository.csvToPlatforms("PS"))
        assertEquals(emptyList<Platform>(), GameRepository.csvToPlatforms(null))
        assertEquals(emptyList<Platform>(), GameRepository.csvToPlatforms(""))
    }

    @Test
    fun `CSV 编码按展示序输出`() {
        // 入参乱序，输出仍按 PC > Switch > PS
        assertEquals("PC,SWITCH", GameRepository.platformsToCsv(setOf(Platform.SWITCH, Platform.PC)))
        assertEquals("PS", GameRepository.platformsToCsv(setOf(Platform.PS)))
        assertEquals("", GameRepository.platformsToCsv(setOf(Platform.Multi)))
    }

    @Test
    fun `CSV 往返一致`() {
        val platforms = setOf(Platform.PC, Platform.PS)
        assertEquals(platforms, GameRepository.csvToPlatforms(GameRepository.platformsToCsv(platforms)).toSet())
    }

    @Test
    fun `标签拼接与Multi兜底`() {
        assertEquals("PC", GameRepository.labelFor(setOf(Platform.PC)))
        assertEquals("PC+Switch", GameRepository.labelFor(setOf(Platform.PC, Platform.SWITCH)))
        assertEquals("Multi", GameRepository.labelFor(emptySet()))
    }

    @Test
    fun `主平台按展示序优先`() {
        assertEquals(Platform.PC, GameRepository.mainPlatform(setOf(Platform.PC, Platform.SWITCH), Platform.PS))
        assertEquals(Platform.SWITCH, GameRepository.mainPlatform(setOf(Platform.SWITCH, Platform.PS), Platform.PC))
        // 只有 Multi → 回退 fallback
        assertEquals(Platform.PC, GameRepository.mainPlatform(setOf(Platform.Multi), Platform.PC))
    }

    @Test
    fun `platformSet_空CSV回退主平台字段`() {
        val game = Game(name = "塞尔达", platform = Platform.SWITCH)
        assertEquals(setOf(Platform.SWITCH), game.platformSet())
        val multi = Game(name = "多平台", platform = Platform.PC, platformsCsv = "PC,PS")
        assertEquals(setOf(Platform.PC, Platform.PS), multi.platformSet())
    }

    @Test
    fun `supportsPlatform_Multi兜底命中所有真实平台`() {
        val pcOnly = Game(name = "PC游戏", platform = Platform.PC, platformsCsv = "PC")
        val multi = Game(name = "多平台", platform = Platform.Multi)
        assertEquals(true, GameRepository.supportsPlatform(pcOnly, Platform.PC))
        assertEquals(false, GameRepository.supportsPlatform(pcOnly, Platform.SWITCH))
        assertEquals(true, GameRepository.supportsPlatform(multi, Platform.SWITCH))
    }
}
