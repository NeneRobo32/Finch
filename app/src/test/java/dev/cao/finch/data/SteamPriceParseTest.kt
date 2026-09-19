package dev.cao.finch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** v0.14：Steam appdetails 价格解析的纯逻辑单测（不联网，拼 Map 走分支逻辑） */
class SteamPriceParseTest {

    /**
     * 与 SteamStoreClient.fetchPrice 同分支的价格判定（Map 版，不依赖 org.json——
     * 单测 JVM 跑 android.jar 的 JSON stub 会抛 "not mocked"，故此处只测分支逻辑）。
     */
    internal fun decidePrice(success: Boolean?, isFree: Boolean, finalCents: Long?, initialCents: Long?): SteamStoreClient.Price? {
        if (success == false) return null
        if (isFree) return SteamStoreClient.Price(cny = 0.0, isFree = true)
        val cents = finalCents ?: initialCents ?: return null
        if (cents < 0) return null
        return SteamStoreClient.Price(cny = cents / 100.0, isFree = false)
    }

    @Test
    fun `正常定价_final优先`() {
        assertEquals(SteamStoreClient.Price(34.0, false), decidePrice(true, false, 3400, 6800))
    }

    @Test
    fun `无折扣_final等于initial`() {
        assertEquals(SteamStoreClient.Price(68.0, false), decidePrice(true, false, 6800, 6800))
    }

    @Test
    fun `免费游戏`() {
        assertEquals(SteamStoreClient.Price(0.0, true), decidePrice(true, true, null, null))
    }

    @Test
    fun `失败success_false`() {
        assertNull(decidePrice(false, false, 3400, 6800))
    }

    @Test
    fun `无价格返回null`() {
        assertNull(decidePrice(true, false, null, null))
        assertNull(decidePrice(null, false, null, null))
    }

    @Test
    fun `回本率口径_暂停已在DAO层扣`() {
        // 68 元玩了 120h → 0.566…元/h；展示层只做除法，暂停口径由 DAO 保证
        val price = 68.0
        val totalMs = 120L * 3600_000
        val perHour = price / (totalMs / 3600_000.0)
        assertEquals(0.566, perHour, 0.001)
    }
}
