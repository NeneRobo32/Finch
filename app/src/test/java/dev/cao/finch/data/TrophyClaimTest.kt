package dev.cao.finch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 奖杯归认纯函数单测（v0.17）：npTitleId 等价优先、名字兜底、宁漏勿错。 */
class TrophyClaimTest {

    private fun game(
        id: Long,
        name: String,
        psnTitleId: String? = null,
        platform: Platform = Platform.PS,
    ) = Game(id = id, name = name, platform = platform, psnTitleId = psnTitleId)

    private fun trophy(npTitleId: String?, name: String) =
        PsnClient.PsnTrophyTitle(
            npTitleId = npTitleId,
            npCommunicationId = null,
            name = name,
            platinum = 0,
            gold = 0,
            silver = 0,
            bronze = 0,
            progress = 0,
            lastUpdatedEpochMillis = null,
        )

    @Test
    fun `npTitleId精确匹配优先于名字`() {
        val bound = game(1, "旧名", "PPSA13195_00")
        val sameName = game(2, "Sample")
        assertEquals(bound, claimTrophyGame(listOf(bound, sameName), trophy("PPSA13195_00", "Sample")))
    }

    @Test
    fun `npTitleId剥后缀等价`() {
        val g = game(1, "Astro", "PPSA13195") // gamelist 形态无 _NN 后缀
        assertEquals(g, claimTrophyGame(listOf(g), trophy("PPSA13195_00", "Astro's Playroom")))
    }

    @Test
    fun `无npTitleId时名字兜底_只认PS行且唯一`() {
        val ps = game(1, "Astro")
        val pc = game(2, "Astro", platform = Platform.PC)
        assertEquals(ps, claimTrophyGame(listOf(ps, pc), trophy(null, "Astro")))
    }

    @Test
    fun `同名多行不猜_宁漏勿错`() {
        val g1 = game(1, "Same")
        val g2 = game(2, "Same")
        assertNull(claimTrophyGame(listOf(g1, g2), trophy(null, "Same")))
    }

    @Test
    fun `不同标题不误认`() {
        val a = game(1, "Other", "NPWR00867_00")
        assertNull(claimTrophyGame(listOf(a), trophy("PPSA13195_00", "Sample")))
        assertNull(claimTrophyGame(listOf(a), trophy(null, "Sample")))
    }
}
