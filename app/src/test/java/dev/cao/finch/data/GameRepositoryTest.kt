package dev.cao.finch.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** 多平台 CSV 编解码、平台归属与合并判断的纯逻辑单测 */
class GameRepositoryTest {

    /**
     * v0.15.9 改名口径（纯逻辑版，与 SyncEngine.runSwitch/runSteam 内联条件一致）：
     *  - Switch：同 switchAppId + 标题不同（忽略大小写）+ 新标题非空 → 改名（语言回填场景）
     *  - Steam：已有 steamAppId + 官方名不同（忽略大小写）+ 新名非空 → 改名（官方改名认领）
     */
    private fun shouldRenameSwitch(storedAppId: String?, storedName: String, incomingTitle: String): Boolean =
        !storedAppId.isNullOrBlank() &&
            !storedName.equals(incomingTitle, ignoreCase = true) &&
            incomingTitle.isNotBlank()

    private fun shouldRenameSteam(storedSteamId: Long?, storedName: String, steamName: String): Boolean =
        storedSteamId != null &&
            !storedName.equals(steamName, ignoreCase = true) &&
            steamName.isNotBlank()

    @Test
    fun `改名口径_Switch中英切换命中_Steam官方改名命中`() {
        // 中文老库名 + 英文新 title → 回填
        assertEquals(true, shouldRenameSwitch("app1", "异度神剑2", "Xenoblade Chronicles 2"))
        // 完全一致（含大小写差异）→ 不动，避免无意义写库
        assertEquals(false, shouldRenameSwitch("app1", "Hades", "hades"))
        // 无 appId（同名匹配的老行）→ 不走改名分支
        assertEquals(false, shouldRenameSwitch(null, "旧名", "New Name"))
        // 空 title → 不改（防脏数据清空名字）
        assertEquals(false, shouldRenameSwitch("app1", "旧名", ""))
        // Steam：官方改名认领
        assertEquals(true, shouldRenameSteam(100L, "旧译名", "Official Name"))
        assertEquals(false, shouldRenameSteam(null, "旧名", "New Name"))
        assertEquals(false, shouldRenameSteam(100L, "Same", "same"))
    }


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
