package dev.cao.finch.data

import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/**
 * TheGamesDB (thegamesdb.net)：主机游戏（Switch/PS）资料+封面强，免费 Key 注册即得。
 * 平台 ID → 名称 用官方 /v1/Platforms 动态拉取并缓存（SharedPreferences，30天）。
 * 走共用 okhttp 直连客户端（BangumiClient.client）。
 */
object TheGamesDbClient {

    data class Result(val name: String, val coverUrl: String?, val platforms: List<String>)

    private const val BASE = "https://api.thegamesdb.net/v1"
    private val client by lazy { BangumiClient.client }

    private fun get(url: String): String {
        val req = Request.Builder().url(url).header("User-Agent", "finch-app/0.10.8").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("TGDB HTTP ${resp.code}")
            return resp.body?.string() ?: throw IOException("空响应")
        }
    }

    /** 拉取平台 ID→名称 表（30 天缓存，存 SharedPreferences） */
    fun fetchPlatformNames(apiKey: String, cache: android.content.SharedPreferences): Map<Int, String> {
        val cached = cache.getString("tgdb_platforms", null)
        val cachedAt = cache.getLong("tgdb_platforms_at", 0)
        if (cached != null && System.currentTimeMillis() - cachedAt < 30L * 24 * 3600 * 1000) {
            return parsePlatformMap(cached)
        }
        val json = get("$BASE/Platforms?apikey=$apiKey&fields=name")
        val map = parsePlatformMap(json)
        cache.edit()
            .putString("tgdb_platforms", json)
            .putLong("tgdb_platforms_at", System.currentTimeMillis())
            .apply()
        return map
    }

    private fun parsePlatformMap(json: String): Map<Int, String> {
        val data = JSONObject(json).optJSONObject("data") ?: return emptyMap()
        val out = mutableMapOf<Int, String>()
        for (key in data.keys()) {
            val o = data.optJSONObject(key) ?: continue
            val id = o.optInt("id")
            val name = o.optString("name")
            if (id > 0 && name.isNotBlank()) out[id] = name
        }
        return out
    }

    fun search(apiKey: String, query: String, platformNames: Map<Int, String>): List<Result> {
        val url = "$BASE/Games/BySearch?apikey=$apiKey&name=${URLEncoder.encode(query, "UTF-8")}&include=boxart"
        val json = get(url)
        val root = JSONObject(json)
        val games = root.optJSONObject("data")?.optJSONArray("games") ?: return emptyList()

        // boxart: include.boxart.base_url.{original/medium/small...} + data[gameId] = [{type:front, filename}]
        val include = root.optJSONObject("include")?.optJSONObject("boxart")
        val baseUrls = include?.optJSONObject("base_url")
        val smallBase = baseUrls?.optString("small") ?: ""
        val medBase = baseUrls?.optString("medium") ?: ""
        val boxartData = include?.optJSONObject("data")
        fun frontUrl(gameId: Int): String? {
            val arr = boxartData?.optJSONArray(gameId.toString()) ?: return null
            for (i in 0 until arr.length()) {
                val b = arr.getJSONObject(i)
                if (b.optString("type") == "front") {
                    val f = b.optString("filename")
                    return (if (smallBase.isNotBlank() && smallBase.startsWith("https")) smallBase else medBase) + f
                }
            }
            return null
        }

        val out = mutableListOf<Result>()
        for (i in 0 until games.length()) {
            val g = games.getJSONObject(i)
            val platNames = mutableListOf<String>()
            val parr = g.optJSONArray("platform")
            if (parr != null) {
                for (j in 0 until parr.length()) {
                    platformNames[parr.getInt(j)]?.let { platNames += it }
                }
            }
            out += Result(
                name = g.optString("game_title"),
                coverUrl = frontUrl(g.optInt("id")),
                platforms = platNames,
            )
        }
        return out
    }
}
