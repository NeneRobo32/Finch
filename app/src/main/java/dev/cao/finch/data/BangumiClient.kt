package dev.cao.finch.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Bangumi (bgm.tv) 客户端 —— okhttp 直连；client 同时供 Eshop / Gamersky / SteamStore / IGDB / HLTB 复用 */
object BangumiClient {

    data class Result(val name: String, val nameCn: String?, val coverUrl: String?, val platforms: List<String>)

    data class CalendarEntry(
        val name: String,
        val nameCn: String?,
        val coverUrl: String?,
        val date: String?,          // "2026-11-20"，可能 null
        val platforms: List<String>,
        val bangumiId: Long,
    )

    private const val UA = "finch-app/0.10.8 (Android; game time tracker)"

    /** 直连（不用 DoH——手机上 DoH 的 IP 反而连不通） */
    internal val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    private fun get(url: String, method: String = "GET", body: String? = null): String {
        var lastErr: Exception? = null
        repeat(2) { attempt ->
            try {
                val b = okhttp3.Request.Builder().url(url).header("User-Agent", UA)
                if (body != null) {
                    b.post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                }
                client.newCall(b.build()).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    return resp.body?.string() ?: throw IOException("空响应")
                }
            } catch (e: Exception) {
                lastErr = e
                if (attempt < 1) Thread.sleep(800L * (attempt + 1))
            }
        }
        throw lastErr ?: IOException("未知网络错误")
    }

    private fun mapCommon(o: JSONObject): Result? {
        val images = o.optJSONObject("images")
        val cover = images?.optString("common")?.takeIf { it.isNotBlank() }
            ?: images?.optString("large")?.takeIf { it.isNotBlank() }
        val platArray = o.optJSONArray("platform")
        val plats = mutableListOf<String>()
        if (platArray != null) {
            for (j in 0 until platArray.length()) {
                val p = platArray.opt(j)
                plats += when (p) {
                    is String -> p
                    is JSONObject -> p.optString("name_cn")
                    else -> ""
                }
            }
        }
        return Result(
            name = o.optString("name"),
            nameCn = o.optString("name_cn").takeIf { it.isNotBlank() },
            coverUrl = cover,
            platforms = plats.filter { it.isNotBlank() },
        )
    }

    fun search(keyword: String): List<Result> {
        val body = JSONObject().put("keyword", keyword).put("limit", 20)
        val json = get("https://api.bgm.tv/v0/search/games?limit=20", "POST", body.toString())
        val data = JSONObject(json).optJSONArray("data") ?: return emptyList()
        val out = mutableListOf<Result>()
        for (i in 0 until data.length()) {
            mapCommon(data.getJSONObject(i))?.let { out += it }
        }
        return out
    }

    /** 每日发售日历，返回原始 JSON 供离线缓存；base 可传镜像域名 */
    fun fetchCalendarRaw(base: String = "https://api.bgm.tv"): String = get("$base/v0/calendar")

    /** 解析日历 JSON → 即将发售的游戏（按日期升序）。防御式：兼容裸数组和 {data:[...]} 两种包裹 */
    fun parseCalendar(json: String): List<CalendarEntry> {
        val weeks: org.json.JSONArray = try {
            val root = JSONObject(json)
            root.optJSONArray("data") ?: org.json.JSONArray(json)
        } catch (_: Exception) {
            org.json.JSONArray(json)
        }
        val out = mutableListOf<CalendarEntry>()
        for (i in 0 until weeks.length()) {
            val week = weeks.optJSONObject(i) ?: continue
            val items = week.optJSONArray("items") ?: continue
            for (j in 0 until items.length()) {
                val o = items.getJSONObject(j)
                if (o.optInt("type") != 4) continue // 只要游戏
                val r = mapCommon(o) ?: continue
                out += CalendarEntry(
                    name = r.name,
                    nameCn = r.nameCn,
                    coverUrl = r.coverUrl,
                    date = o.optString("date").takeIf { it.isNotBlank() },
                    platforms = r.platforms,
                    bangumiId = o.optLong("id"),
                )
            }
        }
        return out.sortedWith(compareBy({ it.date ?: "9999" }, { it.name }))
    }
}
