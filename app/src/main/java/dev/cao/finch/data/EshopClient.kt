package dev.cao.finch.data

import kotlinx.coroutines.runInterruptible
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 任天堂 eShop（日本官网搜索 search.json）——直连可用，Switch 主机游戏源。
 * 返回含 title/hard/dsdate(发售日)/iurl(封面)/maker。
 */
object EshopClient {

    data class Item(
        val title: String,
        val hard: String?,          // 硬件码："05_BEE" = Switch 等
        val releaseDate: LocalDate?,  // dsdate "2027-09-03 00:00:00"
        val coverUrl: String?,        // iurl 封面
        val maker: String?,
    )

    private val client get() = HttpClients.shared // 共享客户端（connect 5s / read 10s / call 30s）

    private suspend fun get(url: String): String {
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) finch/0.6")
            .build()
        return runInterruptible { client.newCall(req).execute() }.use { resp ->
            if (!resp.isSuccessful) throw HttpStatusException(resp.code, "HTTP ${resp.code}")
            resp.body?.string() ?: throw IOException("空响应")
        }
    }

    /** 发售后 60 天内的日本 eShop 游戏（含已发售，Switch 优先） */
    suspend fun fetchRecent(): List<Item> {
        val now = LocalDate.now()
        return fetchRange(now.minusDays(60), now.plusDays(60))
    }

    /** 按发售日范围拉日本 eShop 游戏（Switch 优先；sort=suggest 热门在前，量可控） */
    suspend fun fetchRange(from: LocalDate, to: LocalDate): List<Item> {
        val url = "https://search.nintendo.jp/nintendo_soft/search.json?q=&limit=100" +
            "&sort=suggest&dir=desc&f_pub_date_from=${from.isoDate()}&f_pub_date_to=${to.isoDate()}&u=9001"
        val json = get(url)
        val root = JSONObject(json)
        val result = root.optJSONObject("result") ?: return emptyList()
        val items = result.optJSONArray("items") ?: return emptyList()
        val out = mutableListOf<Item>()
        for (i in 0 until items.length()) {
            val o = items.getJSONObject(i)
            val hard = o.optString("hard").takeIf { it.isNotBlank() }
            // 只要 Switch 系（hard 含 BEE：switch / switch lite / oled）
            if (hard != null && !hard.contains("BEE")) continue
            val ds = o.optString("dsdate").takeIf { it.isNotBlank() }
            out += Item(
                title = o.optString("title"),
                hard = hard,
                releaseDate = ds?.let { parseDate(it) },
                coverUrl = o.optString("iurl").takeIf { it.isNotBlank() },
                maker = o.optString("maker").takeIf { it.isNotBlank() },
            )
        }
        return out
    }

    private fun parseDate(s: String): LocalDate? = runCatching {
        LocalDate.parse(s.trim().take(10), DateTimeFormatter.ISO_LOCAL_DATE)
    }.getOrNull()

    private fun LocalDate.isoDate(): String = format(DateTimeFormatter.ISO_LOCAL_DATE)

    /**
     * 按名搜 eShop 取英文段（HLTB 英文名联动用）：
     * 日区 search.json 的 title 常是「英文名（日文名）后缀」格式，
     * 只返回拉丁字母段（HLTB 是纯英文库，日文/中文段送过去必 404，还污染失败文案）。
     * 失败抛 IOException，调用方吞掉继续下一级。
     */
    suspend fun searchTitles(keyword: String, limit: Int = 10): List<String> {
        val lim = limit.coerceIn(1, 30)
        val url = "https://search.nintendo.jp/nintendo_soft/search.json?q=" +
            java.net.URLEncoder.encode(keyword, "UTF-8") +
            "&limit=$lim&sort=suggest&dir=desc&u=9001"
        val json = get(url)
        val items = JSONObject(json).optJSONObject("result")?.optJSONArray("items")
            ?: return emptyList()
        val out = mutableListOf<String>()
        for (i in 0 until items.length()) {
            val o = items.optJSONObject(i) ?: continue
            // 追加内容（aoc=DLC）不产英文词候选：DLC 标题的英文段会污染按名搜
            if (o.optString("sctg") == "aoc") continue
            val title = o.optString("title").orEmpty()
            if (title.isBlank()) continue
            // 「Hollow Knight（ホロウナイト） Switch 2 Edition」→ "Hollow Knight"；
            // 纯日文/中文标题（如「ゼルダの伝説」）直接丢弃，不返回
            latinSegment(title)?.takeIf { it.length >= 3 }?.let { out += it }
            if (out.size >= lim) break
        }
        return out.distinct()
    }

    /**
     * 从 eShop 标题抠拉丁英文段：
     * 括号前段含拉丁字母 → 取之并剥平台后缀；否则整标题无拉丁字母 → null（丢弃）。
     * 例："Xenoblade2 (ゼノブレイド2) Nintendo Switch 2 Edition" → "Xenoblade2"；
     *     "ゼルダの伝説" → null；"Hollow Knight（ホロウナイト）" → "Hollow Knight"。
     */
    internal fun latinSegment(title: String): String? {
        val head = title.split("（", "(", "「").firstOrNull()?.trim().orEmpty()
        // 括号前段必须含拉丁字母，否则是纯日文/中文标题
        if (!head.any { it in 'A'..'Z' || it in 'a'..'z' }) return null
        // 剥尾巴平台后缀（与 NameVariants.stripPlatformTails 同思路；此处仅 Switch 平台后缀、阈值更松）
        var cur = head
        val tails = listOf(
            "Nintendo Switch 2 Edition", "Nintendo Switch Edition", "Nintendo Switch",
            "Switch 2 Edition", "Switch Edition",
        )
        var changed = true
        while (changed) {
            changed = false
            for (t in tails) {
                if (cur.endsWith(t, ignoreCase = true) && cur.length - t.length >= 2) {
                    cur = cur.dropLast(t.length).trim().trimEnd('-', ':', '·')
                    changed = true
                    break
                }
            }
        }
        return cur.takeIf { it.length >= 3 && it.any { c -> c in 'A'..'Z' || c in 'a'..'z' } }
    }
}