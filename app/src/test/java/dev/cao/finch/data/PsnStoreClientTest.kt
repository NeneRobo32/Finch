package dev.cao.finch.data

import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 港服 PSN 商店客户端（PsnStoreClient）的纯函数单测。
 *
 * 这里全部走生产入口（internal），不复刻口径 —— 覆盖：
 * 价格解析、折扣力度、商品名清洗、发售日/截止日时区换算、SKU 去重挑选、离线缓存往返。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PsnStoreClientTest {

    // ---- 价格与折扣 ----

    @Test
    fun `价格文本转分_千分位与小数都能解析`() {
        assertEquals(138800, PsnStoreClient.priceCents("HK$1,388.00"))
        assertEquals(38800, PsnStoreClient.priceCents("HK$388.00"))
        assertEquals(2660, PsnStoreClient.priceCents("HK$26.60"))
        assertNull(PsnStoreClient.priceCents(null))
        assertNull(PsnStoreClient.priceCents("包含"))
        assertNull(PsnStoreClient.priceCents(""))
    }

    @Test
    fun `折扣文案转百分比_认不出按零处理`() {
        assertEquals(70, PsnStoreClient.discountPercent("-70%"))
        assertEquals(30, PsnStoreClient.discountPercent("-30%"))
        assertEquals(0, PsnStoreClient.discountPercent(null))
        assertEquals(0, PsnStoreClient.discountPercent(""))
    }

    // ---- 商品名清洗 ----

    @Test
    fun `商品名剥语言尾巴与商标符`() {
        assertEquals("God of War Laufey", PsnStoreClient.cleanPsnName("God of War Laufey™ (中英韩文版)"))
        assertEquals("God of War Laufey", PsnStoreClient.cleanPsnName("God of War Laufey™"))
        assertEquals("艾尔登法环", PsnStoreClient.cleanPsnName("艾尔登法环 (英语)"))
        assertEquals("女神异闻录4 黄金版", PsnStoreClient.cleanPsnName("女神异闻录4 黄金版"))
    }

    @Test
    fun `商品名剥真实港服多语言长尾巴`() {
        // 实测原样：简体中文里的「体」不在旧字符类里，以前整段匹配失败、尾巴留了下来
        assertEquals(
            "Warhammer Survivors",
            PsnStoreClient.cleanPsnName("Warhammer Survivors (日语, 韩语, 简体中文, 英语)"),
        )
        assertEquals(
            "Sniper Elite 4",
            PsnStoreClient.cleanPsnName("Sniper Elite 4 (日语, 简体中文, 繁体中文, 英语)"),
        )
        assertEquals("进击的巨人３", PsnStoreClient.cleanPsnName("进击的巨人３ (韩语, 简体中文, 英语)"))
    }

    @Test
    fun `商品名保留豪华版等版本信息`() {
        // 「(数字豪华版)」是版本而不是语言，不能被当噪音剥掉
        assertEquals(
            "新作 数字豪华版",
            PsnStoreClient.cleanPsnName("新作 数字豪华版 (中英韩文版)"),
        )
        assertEquals("某作 豪华版", PsnStoreClient.cleanPsnName("某作 豪华版"))
        assertEquals("某作 (Digital Deluxe)", PsnStoreClient.cleanPsnName("某作 (Digital Deluxe)"))
    }

    @Test
    fun `商品名清洗后可空则回退原名`() {
        // 极端情况：(英语) 整个名字都被吃掉时不能返回空串
        assertEquals("(英语)", PsnStoreClient.cleanPsnName("(英语)"))
    }

    // ---- 时间换算（港时口径） ----

    @Test
    fun `发售日按港时换算_UTC十六点即港时次日`() {
        // PSN 用 "…T16:00:00Z" 表示港时次日 00:00
        assertEquals(LocalDate.of(2027, 5, 20), PsnStoreClient.parseReleaseDate("2027-05-19T16:00:00Z"))
        assertEquals(LocalDate.of(2020, 11, 19), PsnStoreClient.parseReleaseDate("2020-11-19T00:00:00Z"))
        assertNull(PsnStoreClient.parseReleaseDate(null))
        assertNull(PsnStoreClient.parseReleaseDate(""))
        assertNull(PsnStoreClient.parseReleaseDate("COMING_SOON"))
    }

    @Test
    fun `折扣截止时间戳按港时换算`() {
        // 2026-11-20 14:59 UTC → 港时 2026-11-20 22:59
        assertEquals(LocalDate.of(2026, 11, 20), PsnStoreClient.parseEndTime("1795186740000"))
        assertNull(PsnStoreClient.parseEndTime(null))
        assertNull(PsnStoreClient.parseEndTime(""))
        assertNull(PsnStoreClient.parseEndTime("0"))
    }

    // ---- 目录条目挑选 ----

    /** 造一条目录 products[] 形状的条目（字段名与真响应一致） */
    private fun gridRow(
        id: String,
        npTitleId: String,
        name: String,
        base: String,
        discounted: String? = null,
        discountText: String? = null,
        preorder: Boolean = false,
        platforms: List<String> = listOf("PS5"),
        cls: String = "FULL_GAME",
    ): JSONObject {
        val price = JSONObject()
            .put("__typename", "SkuPrice")
            .put("basePrice", base)
            .put("discountedPrice", discounted ?: base)
            .put("discountText", discountText ?: JSONObject.NULL)
            .put("isFree", false)
            .put("isTiedToSubscription", false)
            .put("serviceBranding", JSONArray().put("NONE"))
        return JSONObject()
            .put("__typename", "Product")
            .put("id", id)
            .put("name", name)
            .put("npTitleId", npTitleId)
            .put("platforms", JSONArray().also { a -> platforms.forEach { a.put(it) } })
            .put("price", price)
            .put("storeDisplayClassification", cls)
            .put(
                "skus",
                JSONArray().put(JSONObject().put("type", if (preorder) "PREORDER" else "STANDARD")),
            )
            .put(
                "media",
                JSONArray().put(
                    JSONObject().put("role", "GAMEHUB_COVER_ART").put("url", "https://img/$npTitleId.jpg"),
                ),
            )
    }

    @Test
    fun `预购挑选_同标题留最便宜一档`() {
        val rows = listOf(
            gridRow("UP1", "PPSA0001_00", "新作 (中英韩文版)", "HK$568.00", preorder = true),
            gridRow("UP2", "PPSA0001_00", "新作 数字豪华版 (中英韩文版)", "HK$788.00", preorder = true),
            gridRow("UP3", "PPSA0002_00", "另一作 (中英韩文版)", "HK$388.00", preorder = true),
            gridRow("UP4", "PPSA0003_00", "已发售老游戏 (中英韩文版)", "HK$148.00", preorder = false),
        )
        val picked = PsnStoreClient.pickPreorders(rows)
        assertEquals(2, picked.size)                       // 非预购被滤掉
        assertTrue(picked.all { it.isPreorder })
        val first = picked.first { it.npTitleId == "PPSA0001_00" }
        assertEquals("UP1", first.productId)               // 留便宜的标准版
        assertEquals("HK$568.00", first.priceText)
        assertNull(first.basePriceText)                    // 预购不打折：不划线
        assertEquals("https://img/PPSA0001_00.jpg", first.coverUrl)
        assertEquals(setOf(Platform.PS), first.platforms)
    }

    @Test
    fun `预购挑选_有本体时不吃便宜的升级包`() {
        // 实测形态：「数字豪华版升级」HK$50 是升级 DLC，不是本体，展示成 HK$50 会误导
        val rows = listOf(
            gridRow("UP1", "PPSA19249_00", "God of War Laufey™ (中英韩文版)", "HK$388.00", preorder = true, cls = "FULL_GAME"),
            gridRow("UP2", "PPSA19249_00", "God of War Laufey™ 数字豪华版 (中英韩文版)", "HK$438.00", preorder = true, cls = "PREMIUM_EDITION"),
            gridRow("UP3", "PPSA19249_00", "God of War Laufey™ 数字豪华版升级 (中英韩文版)", "HK$50.00", preorder = true, cls = "GAME_BUNDLE"),
        )
        val picked = PsnStoreClient.pickPreorders(rows)
        assertEquals(1, picked.size)
        assertEquals("UP1", picked.first().productId)
        assertEquals("HK$388.00", picked.first().priceText)
        assertTrue(picked.first().fullGame)
    }

    @Test
    fun `预购挑选_没有本体时退回最便宜一档`() {
        // 全是套装/豪华版（如 FF7 三部曲）时不能丢，取最便宜那档
        val rows = listOf(
            gridRow("UP1", "PPSA37340_00", "REVELATION (中韩文版)", "HK$528.00", preorder = true, cls = "GAME_BUNDLE"),
            gridRow("UP2", "PPSA37340_00", "Trilogy Edition (中韩文版)", "HK$628.00", preorder = true, cls = "GAME_BUNDLE"),
        )
        val picked = PsnStoreClient.pickPreorders(rows)
        assertEquals(1, picked.size)
        assertEquals("UP1", picked.first().productId)
    }

    @Test
    fun `预购挑选_清洗名字后同名仍按npTitleId分组`() {
        val rows = listOf(
            gridRow("UP1", "PPSA0001_00", "Alpha™ (中英韩文版)", "HK$468.00", preorder = true),
            gridRow("UP2", "PPSA0001_00", "Alpha™ (英语)", "HK$468.00", preorder = true),
        )
        val picked = PsnStoreClient.pickPreorders(rows)
        assertEquals(1, picked.size)
        assertEquals("Alpha", picked.first().name)
    }

    @Test
    fun `折扣挑选_同标题留力度最大一档`() {
        val rows = listOf(
            gridRow("UP1", "PPSA0001_00", "打折作 标准版 (中英韩文版)", "HK$468.00", "HK$140.40", "-70%"),
            gridRow("UP2", "PPSA0001_00", "打折作 豪华版 (中英韩文版)", "HK$788.00", "HK$236.40", "-70%"),
            gridRow("UP3", "PPSA0002_00", "小折扣作 (中英韩文版)", "HK$238.00", "HK$166.60", "-30%"),
            gridRow("UP4", "PPSA0003_00", "原价作 (中英韩文版)", "HK$398.00"),
        )
        val picked = PsnStoreClient.pickDeals(rows)
        assertEquals(2, picked.size)                       // 无 discountText 的被滤掉
        val big = picked.first { it.npTitleId == "PPSA0001_00" }
        assertEquals("UP1", big.productId)                 // 同力度取更便宜的
        assertEquals("-70%", big.discountText)
        assertEquals("HK$140.40", big.priceText)
        assertEquals("HK$468.00", big.basePriceText)       // 划线原价
    }

    @Test
    fun `折扣挑选_PS4与PS5同款只留一条`() {
        // 实测：PS4 与 PS5 版是两套 npTitleId，同一个游戏会在「全部折扣」里出现两次
        val rows = listOf(
            gridRow("UP1", "CUSA0001_00", "Sniper Ghost Warrior Contracts 2 (韩语, 简体中文, 繁体中文, 英语)", "HK$308.00", "HK$30.80", "-90%", platforms = listOf("PS4")),
            gridRow("UP2", "PPSA0001_00", "Sniper Ghost Warrior Contracts 2 (韩语, 简体中文, 繁体中文, 英语)", "HK$308.00", "HK$30.80", "-90%", platforms = listOf("PS5")),
        )
        val picked = PsnStoreClient.pickDeals(rows)
        assertEquals(1, picked.size)
        assertEquals("Sniper Ghost Warrior Contracts 2", picked.first().name)
    }

    @Test
    fun `折扣挑选_剔掉PSPlus免费与追加内容`() {
        // 实测「全部折扣」里混进来的两类脏数据
        val rows = listOf(
            // PS+ 会员库内免费：-100% 且现价是「包含」
            gridRow("UP1", "PPSA0001_00", "Cities: Skylines (中英韩文版)", "HK$308.00", "包含", "-100%"),
            // 追加内容（DLC/打包）
            gridRow("UP2", "PPSA0002_00", "Lords of the Fallen - Complete Edition DLC Bundle (2014) (追加内容)", "HK$108.00", "HK$16.20", "-85%"),
            // 正常打折
            gridRow("UP3", "PPSA0003_00", "Sniper Elite 4 (中英韩文版)", "HK$468.00", "HK$46.80", "-90%"),
        )
        val picked = PsnStoreClient.pickDeals(rows)
        assertEquals(1, picked.size)
        assertEquals("Sniper Elite 4", picked.first().name)
    }

    @Test
    fun `折扣挑选_原价等于现价时不划线`() {
        val rows = listOf(
            gridRow("UP1", "PPSA0001_00", "假折扣 (中英韩文版)", "HK$398.00", "HK$398.00", "-30%"),
        )
        val picked = PsnStoreClient.pickDeals(rows)
        assertEquals(1, picked.size)
        assertNull(picked.first().basePriceText)
    }

    // ---- 离线缓存 ----

    @Test
    fun `缓存往返_预购与折扣条目字段不丢`() {
        val feed = PsnStoreClient.Feed(
            preorders = listOf(
                PsnStoreClient.Item(
                    productId = "UP1",
                    npTitleId = "PPSA0001_00",
                    name = "Alpha",
                    coverUrl = "https://img/1.jpg",
                    platforms = setOf(Platform.PS),
                    priceText = "HK$568.00",
                    basePriceText = null,
                    discountText = null,
                    releaseDate = LocalDate.of(2027, 5, 20),
                    isPreorder = true,
                    fullGame = true,
                ),
            ),
            deals = listOf(
                PsnStoreClient.Item(
                    productId = "UP9",
                    npTitleId = "PPSA0009_00",
                    name = "Beta",
                    coverUrl = null,
                    platforms = setOf(Platform.PS),
                    priceText = "HK$140.40",
                    basePriceText = "HK$468.00",
                    discountText = "-70%",
                    releaseDate = null,
                    isPreorder = false,
                ),
            ),
            saleEndsAt = LocalDate.of(2026, 11, 20),
        )
        val back = PsnStoreClient.fromJson(PsnStoreClient.toJson(feed))
        assertEquals(feed, back)
    }

    @Test
    fun `缓存解析_坏数据返回null而不抛`() {
        assertNull(PsnStoreClient.fromJson("not json"))
        val empty = PsnStoreClient.fromJson("{}")
        assertTrue(empty != null && empty.isEmpty && empty.saleEndsAt == null)
    }
}
