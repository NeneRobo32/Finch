package dev.cao.finch.data

import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import kotlinx.coroutines.runInterruptible
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

    private val client get() = HttpClients.shared // 共享客户端（connect 5s / read 10s / call 30s）

    /**
     * 网络 GET（多 URL 容灾；G.1 取消安全口径）：suspend + runInterruptible——协程取消即中断阻塞调用；
     * 退避用 delay()（取消即抛）；4xx 不重试，仅 5xx/429/IO 重试。
     */
    private suspend fun get(url: String, vararg fallbackUrls: String): String {
        val urls = listOf(url) + fallbackUrls
        var lastErr: Exception? = null
        for (u in urls) {
            repeat(3) { attempt ->
                try {
                    val req = Request.Builder().url(u)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) finch/0.5")
                        .build()
                    return runInterruptible {
                        client.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) throw HttpStatusException(resp.code, "HTTP ${resp.code}")
                            resp.body?.string() ?: throw IOException("空响应")
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e // 取消不是失败，照常上抛
                    lastErr = e
                    if (!isRetryable(e)) throw e // 4xx 等不重试
                    if (attempt < 2) kotlinx.coroutines.delay(800L * (attempt + 1))
                }
            }
        }
        throw lastErr ?: IOException("未知网络错误")
    }

    suspend fun search(keyword: String): List<Result> {
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

    /**
     * 中文名 → Steam 候选匹配口径（纯逻辑，可单测）：Steam 中文索引能命中中文库名，
     * 取相似度最高的候选（门槛 0.55 + 数字强制）。
     * 命中后可拿 appid 直查 HLTB 中转 /steam/<appid>，绕开 HLTB 的英文名匹配。
     * 数字强制比按名搜（-0.1 惩罚）更严、直接否决：appid 直查命中即写库，
     * 「女神异闻录3」绝不能写成 5 代的三围。罗马数字/汉字数字名（XI/八）会因此漏配，
     * 由原名/机翻等下级来源兜底——宁漏勿错。
     */
    internal fun pickAppId(query: String, items: List<Result>): Result? {
        val q = query.trim()
        if (q.isBlank()) return null
        val qNums = digitTokens(q)
        return items
            .map { it to HltbProxyClient.similarity(q, it.name, emptySet()) }
            .filter { (hit, sim) -> sim >= 0.55 && qNums.all { it in digitTokens(hit.name) } }
            .maxByOrNull { it.second }
            ?.first
    }

    /** 数字词口径（纯逻辑，可单测）：全角数字归一成半角后抠连续数字段（「女神异闻录５」→ {"5"}） */
    internal fun digitTokens(s: String): Set<String> {
        val half = s.map { c -> if (c in '０'..'９') (c.code - 0xFF10 + 0x30).toChar() else c }
            .joinToString("")
        return Regex("\\d+").findAll(half).map { it.value }.toSet()
    }

    /** 商店「即将推出」+「新上架」（featuredcategories 的 coming_soon + new_releases 合并去重） */
    suspend fun fetchComingSoon(): List<ComingSoonGame> {
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
