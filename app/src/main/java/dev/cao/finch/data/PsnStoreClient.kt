package dev.cao.finch.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import android.util.Log
import okhttp3.Dispatcher
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 港服 PSN 商店（web.np.playstation.com GraphQL persisted query）——日程页「PSN 港服」源。
 *
 * 接口形状来自 MIT 许可的 mrt1m/playstation-store-api（只照抄查询形状与 operation 名，
 * 不引入其 PHP 代码，响应 DTO 全部自建）。三个必须点（本地实测得出，源码里查不到）：
 * 1) `apollo-require-preflight: true` —— 少了它整条链 400（CSRF 拦截）；
 * 2) `x-psn-store-locale-override: zh-hans-hk` —— 切港服的唯一手段，不带会落 GBP 区；
 * 3) sha256Hash 是服务端白名单，不可自算（自造 hash 返回 "Query … not whitelisted"）。
 *
 * 取数路径（不依赖按名搜索，因为港服搜索接口需要 token、旧 chihiro 搜索对中文名命中率极低）：
 * - 预购前沿：PS5 游戏类目按 productReleaseDate 降序第一页（实测前 250 条里正好是未来新作，
 *   第二页起退化成已发售老游戏），筛 skus[].type == "PREORDER"；
 * - 正在打折：All Deals 类目按 sales30 降序第一页，筛 price.discountText 非空。
 * 目录条目不返回发售日/折扣截止时间，故对预购条目并发补 metGetProductById（14KB/次），
 * 折扣截止时间只抽样几次拿 campaign 的统一 endTime（实测同档折扣 endTime 完全一致）。
 */
object PsnStoreClient {

    // ---- 接口常量 ----

    private const val BASE = "https://web.np.playstation.com/api/graphql/v1/"
    private const val LOCALE = "zh-hans-hk"

    /** PS5 游戏类目（港服实测 totalCount ≈ 7489） */
    private const val CAT_PS5_GAMES = "4cbf39e2-5749-4970-ba81-93a489e4570c"

    /** 全部折扣类目（localizedName = cat.gma.AllDeals，实测 235 条 / 55 条带 discountText） */
    private const val CAT_SALES = "803cee19-e5a1-4d59-a463-0b6b2701bf7c"

    /** persisted query 白名单 hash（服务端下发，不可自算；失效表现为整条链 400） */
    private val HASH = mapOf(
        "categoryGridRetrieve" to "4ce7d410a4db2c8b635a48c1dcec375906ff63b19dadd87e073f8fd0c0481d35",
        "metGetProductById" to "a128042177bd93dd831164103d53b73ef790d56f51dae647064cb8f9d9fc9d1a",
        "metGetConceptByProductIdQuery" to "0a4c9f3693b3604df1c8341fdc3e481f42eeecf961a996baaa65e65a657a6433",
    )

    /** 商店发售日/截止时间都以港时为准（"…T16:00:00Z" 即港时次日 00:00） */
    private val HK: ZoneId = ZoneId.of("Asia/Hong_Kong")

    /** 预购条目补发售日的并发度与条数上限（实测单次 1.2s / 14KB） */
    private const val ENRICH_PARALLEL = 8
    private const val MAX_PREORDER = 40

    /** 抽样几条折扣商品取统一 endTime 的次数 */
    private const val END_SAMPLE = 3

    /**
     * 专用 OkHttpClient：只把**每域名并发**从默认 5 抬到 8。
     * 补发售日一次要打 30+ 个同域名请求，默认 5 并发会把总耗时拖到 3 倍以上；
     * 其余超时口径沿用 HttpClients.shared（connect 5s / read 10s / call 30s）。
     */
    private val client: OkHttpClient by lazy {
        HttpClients.shared.newBuilder()
            .dispatcher(Dispatcher().apply { maxRequests = 16; maxRequestsPerHost = ENRICH_PARALLEL })
            .build()
    }

    // ---- DTO ----

    data class Item(
        val productId: String,
        val npTitleId: String,
        val name: String,          // 已清洗（去 ™ 与「(中英韩文版)」尾巴）
        val coverUrl: String?,
        val platforms: Set<Platform>,
        val priceText: String?,    // 现价 "HK$388.00"（无折扣时=原价）
        val basePriceText: String?, // 原价，仅打折时非空（UI 划线用）
        val discountText: String?, // "-70%"，仅打折时非空
        val releaseDate: LocalDate?, // 预购条目的发售日（港时）
        val isPreorder: Boolean,
        /** 商店把「制品版游戏」标为 FULL_GAME；豪华版是 PREMIUM_EDITION、套装是 GAME_BUNDLE */
        val fullGame: Boolean = false,
    )

    data class Feed(
        val preorders: List<Item>,
        val deals: List<Item>,
        /** 本档折扣的统一截止日（抽样取最早值；拿不到时为 null，UI 不显示倒计时） */
        val saleEndsAt: LocalDate?,
    ) {
        val isEmpty: Boolean get() = preorders.isEmpty() && deals.isEmpty()
    }

    // ---- 对外入口 ----

    /** 拉港服「预购新作 + 正在打折」两份清单 */
    suspend fun fetchFeed(): Feed = coroutineScope {
        val t0 = System.currentTimeMillis()
        // 两个目录请求互不依赖，并发发出（各 3~4s，串行会白白多等一倍）
        val ps5Deferred = async { fetchGrid(CAT_PS5_GAMES, "productReleaseDate") }
        val salesDeferred = async { fetchGrid(CAT_SALES, "sales30") }
        val ps5 = ps5Deferred.await()
        val sales = salesDeferred.await()
        val tGrid = System.currentTimeMillis()

        val preorders = pickPreorders(ps5)
        val deals = pickDeals(sales)

        // 补发售日（33 个左右）与抽样折扣截止日也是并发的，两条线一起跑
        val datedDeferred = async {
            enrichDates(preorders.take(MAX_PREORDER))
                .sortedWith(
                    compareBy(
                        { it.releaseDate == null },
                        { it.releaseDate ?: LocalDate.MAX },
                        { it.name.lowercase() },
                    )
                )
        }
        val endDeferred = async { sampleSaleEnd(deals) }

        val dated = datedDeferred.await()
        val end = endDeferred.await()
        Log.d(
            "PsnStore",
            "目录 ${tGrid - t0}ms（ps5=${ps5.size} sales=${sales.size}）" +
                " 补日期 ${System.currentTimeMillis() - tGrid}ms（预购=${dated.size} 折扣=${deals.size} 缺日期=${dated.count { it.releaseDate == null }}）",
        )

        Feed(preorders = dated, deals = deals, saleEndsAt = end)
    }

    // ---- 网络 ----

    private suspend fun gql(op: String, variables: JSONObject): JSONObject {
        val hash = HASH[op] ?: throw IOException("未知 operation：$op")
        val extensions = JSONObject().put(
            "persistedQuery",
            JSONObject().put("version", 1).put("sha256Hash", hash),
        )
        val url = (BASE + "op").toHttpUrl().newBuilder()
            .addQueryParameter("operationName", op)
            .addQueryParameter("variables", variables.toString())
            .addQueryParameter("extensions", extensions.toString())
            .build()
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) finch/0.6")
            .header("x-psn-store-locale-override", LOCALE)
            .header("apollo-require-preflight", "true")
            .build()
        return runInterruptible {
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string()
                if (!resp.isSuccessful) {
                    // 400 两种典型：hash 被下架（not whitelisted）/ 参数不合法 —— 都直接失败不重试
                    throw HttpStatusException(resp.code, "HTTP ${resp.code} ${body?.take(160).orEmpty()}")
                }
                if (body.isNullOrBlank()) throw IOException("空响应")
                JSONObject(body)
            }
        }
    }

    private suspend fun fetchGrid(catId: String, sortName: String): List<JSONObject> {
        val vars = JSONObject()
            .put("id", catId)
            .put("pageArgs", JSONObject().put("size", 250).put("offset", 0))
            .put("sortBy", JSONObject().put("name", sortName).put("isAscending", false))
            .put("filterBy", JSONArray())   // 服务端不支持任何 filterBy，传值必 400
            .put("facetOptions", JSONArray())
        val resp = gql("categoryGridRetrieve", vars)
        if (resp.has("errors")) {
            throw IOException("PSN 目录错误：" + resp.optJSONArray("errors")?.optJSONObject(0)?.optString("message"))
        }
        val products = resp.optJSONObject("data")
            ?.optJSONObject("categoryGridRetrieve")
            ?.optJSONArray("products") ?: return emptyList()
        return (0 until products.length()).mapNotNull { products.optJSONObject(it) }
    }

    /** 目录条目 → Item（不含发售日，预购条目由 [enrichDates] 补） */
    private fun parseGridItem(o: JSONObject): Item? {
        val productId = o.strOrNull("id") ?: return null
        val name = cleanPsnName(o.strOrNull("name").orEmpty())
        if (name.isBlank()) return null
        val price = o.optJSONObject("price")
        val discount = price?.strOrNull("discountText")
        val base = price?.strOrNull("basePrice")
        val now = price?.strOrNull("discountedPrice") ?: base
        val skus = o.optJSONArray("skus")
        val isPreorder = (0 until (skus?.length() ?: 0))
            .any { skus?.optJSONObject(it)?.optString("type") == "PREORDER" }
        val plats = o.optJSONArray("platforms")
        val hasPs = (0 until (plats?.length() ?: 0)).any { plats?.optString(it)?.startsWith("PS") == true }
        return Item(
            productId = productId,
            npTitleId = o.strOrNull("npTitleId") ?: productId,
            name = name,
            coverUrl = coverUrlOf(o.optJSONArray("media")),
            platforms = if (hasPs) setOf(Platform.PS) else emptySet(),
            priceText = now,
            // 折扣时原价与现价不同才划线；未打折时 basePriceText 留空
            basePriceText = if (discount != null && base != null && base != now) base else null,
            discountText = discount,
            releaseDate = null,
            isPreorder = isPreorder,
            fullGame = o.strOrNull("storeDisplayClassification") == "FULL_GAME",
        )
    }

    /** 封面优先级：GAMEHUB_COVER_ART > PORTRAIT_BANNER > FOUR_BY_THREE_BANNER */
    private fun coverUrlOf(media: JSONArray?): String? {
        if (media == null) return null
        val wanted = listOf("GAMEHUB_COVER_ART", "PORTRAIT_BANNER", "FOUR_BY_THREE_BANNER")
        for (role in wanted) {
            for (i in 0 until media.length()) {
                val m = media.optJSONObject(i) ?: continue
                if (m.optString("role") == role) {
                    m.strOrNull("url")?.let { return it }
                }
            }
        }
        return null
    }

    // ---- 清单挑选（纯逻辑，单测直测） ----

    /**
     * 预购前沿：只留 PREORDER，同 npTitleId 只留一档。
     *
     * 优先「制品版游戏」（FULL_GAME，即本体），而不是最简单按价格取最低 ——
     * 实测「God of War Laufey™ 数字豪华版升级」是 HK$50 的豪华版升级包，价格最低但
     * 它不是游戏本体，展示成 HK$50 会严重误导。只有该标题没有本体时，才退回最便宜的档。
     */
    internal fun pickPreorders(rows: List<JSONObject>): List<Item> =
        rows.mapNotNull { parseGridItem(it) }
            .filter { it.isPreorder }
            .filter { !isAddOn(it.name) }
            .groupBy { it.npTitleId }
            .map { (_, group) ->
                group.firstOrNull { it.fullGame }
                    ?: group.minByOrNull { priceCents(it.priceText) ?: Int.MAX_VALUE }!!
            }
            .sortedWith(compareBy({ it.releaseDate == null }, { it.name.lowercase() }))

    /**
     * 正在打折：只留带 discountText 的条目。
     *
     * 去重分两步，缺一步都会露重复：
     * 1) 按 npTitleId —— 同一标题的多个版本（标准版/豪华版/套装）只留折扣力度最大的一档；
     * 2) 再按清洗后的名字 —— PS4 与 PS5 版是**两套 npTitleId**，同一个游戏在
     *    「全部折扣」里会出现两次（如 Sniper Ghost Warrior Contracts 2 的 PS4 与 PS5 版），
     *    只按 npTitleId 去重去不掉。
     */
    internal fun pickDeals(rows: List<JSONObject>): List<Item> {
        val best = { group: List<Item> ->
            group.maxWith(
                compareBy({ discountPercent(it.discountText) }, { -(priceCents(it.priceText) ?: Int.MAX_VALUE) })
            )
        }
        return rows.mapNotNull { parseGridItem(it) }
            .filter { it.discountText != null }
            // 「全部折扣」类目里混着两类不是折扣的东西，必须剔掉：
            // 1) PS+ 会员库内免费：打折文案 -100%、现价是「包含」这种文字，没有数字价格；
            // 2) 追加内容（DLC / 素材包）：名字以「(追加内容)」结尾，不是可玩的正篇。
            .filter { priceCents(it.priceText) != null }
            .filter { !isAddOn(it.name) }
            .groupBy { it.npTitleId }
            .map { (_, group) -> best(group) }
            .groupBy { it.name.lowercase() }
            .map { (_, group) -> best(group) }
            .sortedWith(compareBy({ -discountPercent(it.discountText) }, { priceCents(it.priceText) ?: Int.MAX_VALUE }))
    }

    /** 追加内容（DLC/素材包）：港服名尾为「(追加内容)」，英文为 "(Add-On)" */
    private fun isAddOn(name: String): Boolean {
        val n = name.trim()
        return n.endsWith("(追加内容)") || n.endsWith("（追加内容）") ||
            n.endsWith("(追加內容)") || n.endsWith("(Add-On)") || n.endsWith("(Add-on)")
    }
    // 说明：PS+ 专属价（serviceBranding 含 PS_PLUS）不在此过滤 —— 目录 price 已按普通价给出，
    // 订阅价另在 mobilectas 里，本客户端只认目录价，避免把「要开会员才有的价」当普通折扣展示。

    /** 补预购条目的发售日：并发查 metGetProductById（只取 releaseDate，响应 14KB） */
    private suspend fun enrichDates(items: List<Item>): List<Item> = coroutineScope {
        if (items.isEmpty()) return@coroutineScope emptyList()
        val sem = Semaphore(ENRICH_PARALLEL)
        items.map { item ->
            async {
                sem.withPermit {
                    val d = runCatching { fetchReleaseDate(item.productId) }.getOrNull()
                    item.copy(releaseDate = d)
                }
            }
        }.awaitAll()
    }

    private suspend fun fetchReleaseDate(productId: String): LocalDate? {
        val resp = gql("metGetProductById", JSONObject().put("productId", productId))
        val raw = resp.optJSONObject("data")
            ?.optJSONObject("productRetrieve")
            ?.strOrNull("releaseDate")
            .orEmpty()
        return parseReleaseDate(raw)
    }

    /**
     * 抽样几个折扣商品取 endTime，取最早值作为本档折扣的统一截止日。
     * 实测同档折扣（不同 campaignId：PROMO-PROD-00019863 / 00020004 / 00020000）endTime 完全一致
     * （都是 1795186740000），所以不必逐条查；取最早值是为了宁可显示早一天也不夸大优惠期。
     * 只认 mobilectas[type == "ADD_TO_CART"] 的 price（UPSELL_PS_PLUS_DISCOUNT 是订阅价）。
     */
    private suspend fun sampleSaleEnd(deals: List<Item>): LocalDate? = coroutineScope {
        val picks = deals.take(END_SAMPLE)
        if (picks.isEmpty()) return@coroutineScope null
        val sem = Semaphore(ENRICH_PARALLEL)
        val ends = picks.map { item ->
            async {
                sem.withPermit {
                    runCatching { fetchCampaignEnd(item.productId) }.getOrNull()
                }
            }
        }.awaitAll().filterNotNull()
        ends.minOrNull()
    }

    private suspend fun fetchCampaignEnd(productId: String): LocalDate? {
        val resp = gql("metGetConceptByProductIdQuery", JSONObject().put("productId", productId))
        val concept = resp.optJSONObject("data")?.optJSONObject("productRetrieve")?.optJSONObject("concept")
            ?: return null
        val products = concept.optJSONObject("selectableProducts")?.optJSONArray("purchasableProducts")
            ?: return null
        for (i in 0 until products.length()) {
            val ctas = products.optJSONObject(i)?.optJSONArray("mobilectas") ?: continue
            for (j in 0 until ctas.length()) {
                val cta = ctas.optJSONObject(j) ?: continue
                if (cta.optString("type") != "ADD_TO_CART") continue
                val price = cta.optJSONObject("price") ?: continue
                if (price.strOrNull("discountText") == null) continue
                parseEndTime(price.strOrNull("endTime"))?.let { return it }
            }
        }
        return null
    }

    // ---- 纯函数（单测直测生产口径） ----

    /** "HK$1,388.00" → 138800（分）；解析不出返回 null */
    internal fun priceCents(text: String?): Int? =
        text?.filter { it.isDigit() }?.takeIf { it.isNotBlank() }?.toIntOrNull()

    /** "-70%" → 70；解析不出返回 0（排序时视为无折扣） */
    internal fun discountPercent(text: String?): Int =
        text?.filter { it.isDigit() }?.toIntOrNull() ?: 0

    /**
     * 商店名清洗：剥「(中英韩文版)」「(日语, 韩语, 简体中文, 英语)」这类语言尾巴与 ™® 标记。
     * 只剥命名尾巴，不动正名里的括号（如「女神异闻录4 黄金版」保持原样）。
     *
     * 不用固定字符类去框语言名 —— 实测「简体中文」里的「体」不在「中英日韩简繁文字语」之内，
     * 一框就整段匹配失败、尾巴留了下来。改成按分隔符切 token、逐段判定是不是语言名。
     */
    internal fun cleanPsnName(raw: String): String {
        var s = raw.trim()
        TAIL_GROUP.findAll(s).lastOrNull()?.let { m ->
            val inner = m.groupValues[1]
            if (inner.split(',', '，', '、', '/').all { isLangToken(it) }) {
                s = s.removeRange(m.range).trim()
            }
        }
        s = s.trim().trimEnd('™', '®', '©', '·', '—', '–', '-', '：', ':')
        return s.trim().ifBlank { raw.trim() }
    }

    /** 名尾括号（含全角），group(1) 是括号内文字 */
    private val TAIL_GROUP = Regex("""[（(]\s*([^（()）]{1,40}?)\s*[)）]\s*$""")

    /**
     * 是否是「语言/版本」这种纯附加信息 token：
     * 日语、韩语、英语、简体中文、繁體中文、中英韩文版、English、Japanese ……
     * 要求非空且不含数字，避免把「(2024)」「(第二部)」这类有信息量的括号当噪音剥掉；
     * 英文词必须命中语言白名单，否则「(Digital Deluxe)」会被误删；
     * 中文以「版」结尾的必须同时含语言字（中/英/日/韩/簡/繁/语/語/文），
     * 否则「(豪华版)」「(数字豪华版)」这种版本信息会被当成语言尾巴吃掉。
     */
    private fun isLangToken(token: String): Boolean {
        val t = token.trim()
        if (t.isEmpty() || t.any { it.isDigit() }) return false
        if (t.endsWith("语") || t.endsWith("語") || t.endsWith("文")) return true
        if (t.endsWith("版")) return t.any { it in "中英日韩韓簡繁语語文" }
        return t.lowercase() in EN_LANGS
    }

    private val EN_LANGS = setOf(
        "english", "japanese", "korean", "chinese", "simplified chinese", "traditional chinese",
        "french", "german", "spanish", "italian", "portuguese", "russian", "thai", "vietnamese",
        "arabic", "polish", "turkish", "dutch",
    )

    /** PSN 的 ISO 时间串 → 港时日期（"2027-05-19T16:00:00Z" 即港时 2027-05-20） */
    internal fun parseReleaseDate(iso: String?): LocalDate? {
        if (iso.isNullOrBlank()) return null
        return runCatching {
            Instant.parse(iso).atZone(HK).toLocalDate()
        }.getOrNull() ?: runCatching {
            LocalDate.parse(iso.take(10), DateTimeFormatter.ISO_LOCAL_DATE)
        }.getOrNull()
    }

    /** 折扣结束毫秒时间戳（字符串）→ 港时日期 */
    internal fun parseEndTime(millis: String?): LocalDate? {
        val v = millis?.takeIf { it.isNotBlank() }?.toLongOrNull() ?: return null
        if (v <= 0L) return null
        return runCatching { Instant.ofEpochMilli(v).atZone(HK).toLocalDate() }.getOrNull()
    }

    // ---- 离线缓存（raw JSON，与客户端解耦） ----

    fun toJson(feed: Feed): String {
        val root = JSONObject()
        root.put("preorders", JSONArray().also { arr -> feed.preorders.forEach { arr.put(itemToJson(it)) } })
        root.put("deals", JSONArray().also { arr -> feed.deals.forEach { arr.put(itemToJson(it)) } })
        feed.saleEndsAt?.let { root.put("saleEndsAt", it.toString()) }
        return root.toString()
    }

    fun fromJson(text: String): Feed? = runCatching {
        val root = JSONObject(text)
        Feed(
            preorders = itemList(root.optJSONArray("preorders")),
            deals = itemList(root.optJSONArray("deals")),
            saleEndsAt = root.strOrNull("saleEndsAt")?.let { LocalDate.parse(it) },
        )
    }.getOrNull()

    private fun itemToJson(i: Item): JSONObject = JSONObject()
        .put("productId", i.productId)
        .put("npTitleId", i.npTitleId)
        .put("name", i.name)
        .put("coverUrl", i.coverUrl)
        .put("ps", i.platforms.any { it == Platform.PS })
        .put("priceText", i.priceText)
        .put("basePriceText", i.basePriceText)
        .put("discountText", i.discountText)
        .put("releaseDate", i.releaseDate?.toString())
        .put("isPreorder", i.isPreorder)
        .put("fullGame", i.fullGame)

    private fun itemList(arr: JSONArray?): List<Item> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { idx ->
            val o = arr.optJSONObject(idx) ?: return@mapNotNull null
            val id = o.strOrNull("productId") ?: return@mapNotNull null
            Item(
                productId = id,
                npTitleId = o.strOrNull("npTitleId") ?: id,
                name = o.strOrNull("name").orEmpty(),
                coverUrl = o.strOrNull("coverUrl"),
                platforms = if (o.optBoolean("ps")) setOf(Platform.PS) else emptySet(),
                priceText = o.strOrNull("priceText"),
                basePriceText = o.strOrNull("basePriceText"),
                discountText = o.strOrNull("discountText"),
                releaseDate = o.strOrNull("releaseDate")?.let { LocalDate.parse(it) },
                isPreorder = o.optBoolean("isPreorder"),
                fullGame = o.optBoolean("fullGame"),
            )
        }
    }

    /**
     * 取字符串字段，把「缺失 / JSONObject.NULL / 空串」统一成 null。
     *
     * 注意不能用 `optString(key).ifBlank { null }`：PSN 响应里大量字段显式给 null，
     * 而 optString 会把 JSONObject.NULL 转成字面量 "null"，于是「未打折」被误判成
     * 「折扣文案 = null」（曾让所有原价商品都进折扣清单）。
     */
    private fun JSONObject.strOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() && it != "null" }
    }
}
