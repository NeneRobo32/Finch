package dev.cao.finch.data

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

    private val client by lazy { BangumiClient.client } // 复用直连客户端

    private fun get(url: String): String {
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) finch/0.6")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            return resp.body?.string() ?: throw IOException("空响应")
        }
    }

    /** 发售后 60 天内的日本 eShop 游戏（含已发售，Switch 优先） */
    fun fetchRecent(): List<Item> {
        val now = LocalDate.now()
        return fetchRange(now.minusDays(60), now.plusDays(60))
    }

    /** 按发售日范围拉日本 eShop 游戏（Switch 优先；sort=suggest 热门在前，量可控） */
    fun fetchRange(from: LocalDate, to: LocalDate): List<Item> {
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
}