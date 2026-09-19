package dev.cao.finch.data

import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Steam 商店接口（okhttp 直连）：搜索 + 即将推出列表 */
object SteamStoreClient {

    data class Result(val appid: Long, val name: String, val coverUrl: String?)

    data class ComingSoonGame(
        val appid: Long,
        val name: String,
        val coverUrl: String?,
        val releaseText: String?,
        val releaseDate: LocalDate?,
    )

    private val client by lazy { BangumiClient.client } // 复用共享直连客户端

    private fun get(url: String, vararg fallbackUrls: String): String {
        val urls = listOf(url) + fallbackUrls
        var lastErr: Exception? = null
        for (u in urls) {
            repeat(3) { attempt ->
                try {
                    val req = Request.Builder().url(u)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) finch/0.5")
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                        return resp.body?.string() ?: throw IOException("空响应")
                    }
                } catch (e: Exception) {
                    lastErr = e
                    if (attempt < 2) Thread.sleep(800L * (attempt + 1))
                }
            }
        }
        throw lastErr ?: IOException("未知网络错误")
    }

    fun search(keyword: String): List<Result> {
        val url = "https://store.steampowered.com/api/storesearch/?term=${java.net.URLEncoder.encode(keyword, "UTF-8")}&l=schinese&cc=CN"
        val json = get(url)
        val items = JSONObject(json).optJSONArray("items") ?: return emptyList()
        val out = mutableListOf<Result>()
        for (i in 0 until items.length()) {
            val o = items.getJSONObject(i)
            out += Result(
                appid = o.optLong("id"),
                name = o.optString("name"),
                coverUrl = o.optString("tiny_image").takeIf { it.isNotBlank() },
            )
        }
        return out
    }

    /** 商店「即将推出」+「新上架」（featuredcategories 的 coming_soon + new_releases 合并去重） */    fun fetchComingSoon(): List<ComingSoonGame> {
        // 主 URL 失败自动试备用（featured/comingsoon 编辑精选——量少但稳定）
        val json = get(
            "https://store.steampowered.com/api/featuredcategories/?l=schinese&cc=CN",
            "https://store.steampowered.com/api/featured/comingsoon?l=schinese&cc=CN",
        )
        val root = JSONObject(json)
        val out = LinkedHashMap<Long, ComingSoonGame>()
        for (key in listOf("coming_soon", "new_releases")) {
            val cat = root.optJSONObject(key) ?: continue
            val items = cat.optJSONArray("items") ?: continue
            for (i in 0 until items.length()) {
                val o = items.getJSONObject(i)
                val id = o.optLong("id")
                val rd = o.optJSONObject("release_date")
                val text = rd?.optString("date")?.takeIf { it.isNotBlank() }
                out[id] = ComingSoonGame(
                    appid = id,
                    name = o.optString("name"),
                    coverUrl = o.optString("header_image").takeIf { it.isNotBlank() },
                    releaseText = text,
                    releaseDate = text?.let(::parseReleaseText),
                )
            }
        }
        return out.values.sortedWith(compareBy({ it.releaseDate ?: LocalDate.MAX }, { it.name }))
    }

    /** 解析 Steam 日期文案："2026年11月19日" / "2026年11月" / "2026年" / "19 Nov, 2026" */
    fun parseReleaseText(text: String): LocalDate? {
        val cnFull = Regex("(\\d{4})年(\\d{1,2})月(\\d{1,2})日").find(text)
        if (cnFull != null) {
            val (y, m, d) = cnFull.destructured
            return runCatching { LocalDate.of(y.toInt(), m.toInt(), d.toInt()) }.getOrNull()
        }
        val cnMonth = Regex("(\\d{4})年(\\d{1,2})月").find(text)
        if (cnMonth != null) {
            val (y, m) = cnMonth.destructured
            return runCatching { LocalDate.of(y.toInt(), m.toInt(), 1) }.getOrNull()
        }
        val cnYear = Regex("(\\d{4})年").find(text)
        if (cnYear != null) {
            return runCatching { LocalDate.of(cnYear.groupValues[1].toInt(), 1, 1) }.getOrNull()
        }
        for (fmt in listOf("d MMM, yyyy", "MMM d, yyyy", "d MMM yyyy")) {
            runCatching {
                return LocalDate.parse(text.trim(), DateTimeFormatter.ofPattern(fmt, Locale.ENGLISH))
            }
        }
        return null
    }
}
