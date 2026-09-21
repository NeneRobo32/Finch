package dev.cao.finch.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * HLTB 中转 API 客户端（Crashdummy/HowLongToBeatApi，Codeberg 开源 + Azure 免费层 demo）。
 *
 * 为什么走中转：HLTB 站内搜索 API（POST /api/search/site）被 WAF 按 IP/指纹拦，
 * 手机直连 init 就 403；详情页 GET /game/<id> 虽通，但名→id 翻译无官方入口。
 * 中转服务在服务端扛 WAF + 带缓存，手机端只发普通 HTTPS GET/POST。
 *
 * 端点（Scalar 文档见 https://hltbapi1.azurewebsites.net/scalar）：
 *  - GET  {base}/steam/{appid}  → Steam 游戏直查（最准，一次命中）
 *  - POST {base}/hltb/search     → 按名搜（body {"searchTerm","matchType":1,"platform":""}）
 *  - GET  {base}/hltb/{id}       → 按 HLTB id 查（手动贴 id 的兜底）
 * 返回 GameEntry：hltbId/title/imageUrl/steamAppId/gogAppId/
 *   mainStory/mainStoryWithExtras/completionist（小时 Double）/lastUpdatedAt。
 *
 * 隐私：只发游戏名/appid（HTTPS 加密），不发游玩记录/密钥/设备标识；
 * 失败静默回手动填。详见 README「游戏资料库」段。
 */
object HltbProxyClient {

    data class Times(val mainMin: Long?, val extraMin: Long?, val completeMin: Long?) {
        fun any(): Boolean = mainMin != null || extraMin != null || completeMin != null
    }

    /** 双 base 容灾：主挂了自动试备 */
    private val bases = listOf(
        "https://hltbapi1.azurewebsites.net",
        "https://hltbapi.codepotatoes.de",
    )
    private val client by lazy { BangumiClient.client } // 复用共享直连客户端
    private const val UA = "finch/0.15 (Android; game time tracker)"

    /** Steam 游戏直查（最准）：appid → 三围（分钟）；无条目/失败抛 IOException */
    fun fetchBySteam(appid: Long): Times {
        if (appid <= 0) throw IOException("Steam appid 非法")
        val entry = getOnBases("/steam/$appid") ?: throw IOException("中转无此 Steam 游戏")
        return entry.toTimes() ?: throw IOException("中转条目无时长数据")
    }

    /** 按 HLTB id 查（手动贴 id 的兜底） */
    fun fetchByHltbId(hltbId: Long): Times {
        if (hltbId <= 0) throw IOException("HLTB id 非法")
        val entry = getOnBases("/hltb/$hltbId") ?: throw IOException("中转无此 HLTB 条目")
        return entry.toTimes() ?: throw IOException("中转条目无时长数据")
    }

    /**
     * 按名搜：返回候选（相似度排序，含三围）。
     * 数字强制匹配：名里带数字的必须对上（如 2 代不认 1 代），全不过滤则回退全量。
     */
    fun search(query: String): List<Hit> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        var lastErr: Exception? = null
        for (base in bases) {
            try {
                val body = org.json.JSONObject()
                    .put("searchTerm", q)
                    .put("matchType", 1)
                    .put("platform", "")
                    .toString()
                    .toRequestBody("application/json".toMediaType())
                val req = Request.Builder().url("$base/hltb/search")
                    .header("User-Agent", UA)
                    .post(body)
                    .build()
                val json = client.newCall(req).execute().use { resp ->
                    if (resp.code == 404) return@use null // 该 base 无匹配，换下一个
                    if (!resp.isSuccessful) throw IOException("中转 HTTP ${resp.code}")
                    resp.body?.string()
                } ?: continue
                val hits = parseSearchResult(json, q)
                if (hits.isNotEmpty()) return hits
                // 该 base 空结果：继续试下一个 base（缓存覆盖不同）
            } catch (e: Exception) {
                lastErr = e
            }
        }
        if (lastErr != null && lastErr !is IOException) throw lastErr
        return emptyList()
    }

    /** 搜索取最佳（相似度 ≥0.4 才算命中，否则 null） */
    fun searchBest(query: String): Hit? {
        val hits = try {
            search(query)
        } catch (_: Exception) {
            return null
        }
        if (hits.isEmpty()) return null
        val numbers = Regex("\\d+").findAll(query).map { it.value }.toSet()
        val filtered = if (numbers.isNotEmpty()) {
            hits.filter { h ->
                val hay = "${h.title}"
                numbers.all { n -> Regex("\\b$n\\b").containsMatchIn(hay) }
            }.ifEmpty { hits }
        } else hits
        return filtered.maxByOrNull { it.similarity }?.takeIf { it.similarity >= 0.4 }
    }

    data class Hit(
        val hltbId: Long,
        val title: String,
        val times: Times,
        val similarity: Double,
    )

    internal data class Entry(
        val hltbId: Long,
        val title: String,
        val mainStory: Double?,
        val mainExtra: Double?,
        val completionist: Double?,
    ) {
        fun toTimes(): Times? {
            val t = Times(
                mainMin = mainStory?.times(60)?.toLong()?.takeIf { it > 0 },
                extraMin = mainExtra?.times(60)?.toLong()?.takeIf { it > 0 },
                completeMin = completionist?.times(60)?.toLong()?.takeIf { it > 0 },
            )
            return if (t.any()) t else null
        }
    }

    /** 纯 JSON 行解析（无 android 依赖，可单测）：缺 hltbId 返回 null */
    internal fun parseEntryFields(hltbId: Long, title: String, main: Double?, extra: Double?, complete: Double?): Entry? {
        if (hltbId <= 0) return null
        fun clean(d: Double?): Double? = d?.takeIf { !it.isNaN() }
        return Entry(hltbId, title, clean(main), clean(extra), clean(complete))
    }

    private fun getOnBases(path: String): Entry? {
        var lastErr: Exception? = null
        for (base in bases) {
            try {
                val req = Request.Builder().url("$base$path")
                    .header("User-Agent", UA)
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.code == 404) return@use null // 无条目：换 base 再试
                    if (!resp.isSuccessful) throw IOException("中转 HTTP ${resp.code}")
                    val body = resp.body?.string() ?: throw IOException("空响应")
                    return parseEntry(body)
                }
            } catch (e: Exception) {
                lastErr = e
            }
        }
        if (lastErr != null) throw lastErr
        return null
    }

    internal fun parseEntry(json: String): Entry? {
        val o = try {
            org.json.JSONObject(json)
        } catch (_: Exception) {
            return null
        }
        return parseEntryFields(
            hltbId = o.optLong("hltbId"),
            title = o.optString("title"),
            main = o.optDouble("mainStory").takeIf { !it.isNaN() },
            extra = o.optDouble("mainStoryWithExtras").takeIf { !it.isNaN() },
            complete = o.optDouble("completionist").takeIf { !it.isNaN() },
        )
    }

    internal fun parseSearchResult(json: String, query: String): List<Hit> {
        val rawArr: List<String> = try {
            val arr = org.json.JSONArray(json)
            List(arr.length()) { i -> arr.optJSONObject(i)?.toString() ?: "" }
        } catch (_: Exception) {
            // 可能包一层 {data:[...]}，兼容一下
            try {
                val arr = org.json.JSONObject(json).optJSONArray("data") ?: return emptyList()
                List(arr.length()) { i -> arr.optJSONObject(i)?.toString() ?: "" }
            } catch (_: Exception) {
                return emptyList()
            }
        }
        return mapHits(rawArr.mapNotNull { parseEntry(it) }, query)
    }

    /** Entry 列表 → Hit 列表（纯逻辑，可单测）：过滤低相似 + 无三围 */
    internal fun mapHits(entries: List<Entry>, query: String): List<Hit> {
        val numbers = Regex("\\d+").findAll(query).map { it.value }.toSet()
        val out = mutableListOf<Hit>()
        for (entry in entries) {
            if (entry.title.isBlank()) continue
            val sim = similarity(query, entry.title, numbers)
            if (sim < 0.4) continue
            val times = entry.toTimes() ?: continue // 无三围的候选不要
            out += Hit(entry.hltbId, entry.title, times, sim)
        }
        return out.sortedByDescending { it.similarity }
    }

    /** 相似度：Gestalt 比率（大小写不敏感）+ 数字强制（名里数字必须对上，否则 -0.1） */
    internal fun similarity(a: String, b: String, numbers: Set<String>): Double {
        if (a.isBlank() || b.isBlank()) return 0.0
        var sim = gestaltRatio(a.lowercase(), b.lowercase())
        if (numbers.isNotEmpty()) {
            val words = b.lowercase().replace(Regex("([^\\s\\w]|_)+"), "")
                .split(Regex("\\s+")).toSet()
            if (numbers.none { it in words }) sim -= 0.1
        }
        return sim.coerceIn(0.0, 1.0)
    }

    /** Gestalt 比率（difflib.SequenceMatcher 核心：最长公共块递归） */
    internal fun gestaltRatio(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        val matches = matchingBlocks(a, b)
        return 2.0 * matches / (a.length + b.length)
    }

    private fun matchingBlocks(a: String, b: String): Int {
        val m = longestMatch(a, 0, a.length, b, 0, b.length) ?: return 0
        if (m.third == 0) return 0
        return m.third +
            matchingBlocks(a.substring(0, m.first), b.substring(0, m.second)) +
            matchingBlocks(a.substring(m.first + m.third), b.substring(m.second + m.third))
    }

    private data class Triple(val first: Int, val second: Int, val third: Int)

    private fun longestMatch(a: String, aLo: Int, aHi: Int, b: String, bLo: Int, bHi: Int): Triple? {
        var bestI = aLo
        var bestJ = bLo
        var bestK = 0
        val bIndex = mutableMapOf<Char, MutableList<Int>>()
        for (j in bLo until bHi) bIndex.getOrPut(b[j]) { mutableListOf() } += j
        var i = aLo
        while (i < aHi) {
            val js = bIndex[a[i]] ?: run { i++; continue }
            for (j in js) {
                if (j < bLo) continue
                var k = 1
                while (i + k < aHi && j + k < bHi && a[i + k] == b[j + k]) k++
                if (k > bestK) {
                    bestI = i
                    bestJ = j
                    bestK = k
                }
            }
            i++
        }
        return Triple(bestI, bestJ, bestK)
    }
}
