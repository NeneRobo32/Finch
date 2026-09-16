package dev.cao.finch.data

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Steam Web API 最小客户端（IPlayerService/GetOwnedGames）。
 * 国内直连 api.steampowered.com 可能不通，支持自定义反代地址。
 */
object SteamClient {

    init {
        // 安卓平台 HttpURLConnection(okhttp) 的 keep-alive 复用在不稳定代理下
        // 容易 "unexpected end of stream"，禁用复用换稳定性
        System.setProperty("http.keepAlive", "false")
    }

    data class SteamGame(val appid: Long, val name: String, val playtimeMinutes: Long, val lastPlayedEpoch: Long? = null)

    fun fetchOwnedGames(baseUrl: String, apiKey: String, steamId: String): List<SteamGame> {
        var lastError: IOException? = null
        repeat(3) { attempt ->
            try {
                return fetchOnce(baseUrl, apiKey, steamId)
            } catch (e: IOException) {
                lastError = e
                if (attempt < 2) {
                    try { Thread.sleep(1000L * (attempt + 1)) } catch (_: InterruptedException) {}
                }
            }
        }
        throw lastError!!
    }

    private fun fetchOnce(baseUrl: String, apiKey: String, steamId: String): List<SteamGame> {
        val url = "${baseUrl.trimEnd('/')}/IPlayerService/GetOwnedGames/v1/" +
            "?key=$apiKey&steamid=$steamId&include_appinfo=1&include_played_free_games=1"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("Connection", "close") // 禁止复用，代理下更稳
        try {
            val code = conn.responseCode
            if (code != 200) throw IOException("Steam API HTTP $code（检查 Key/SteamID，或网络是否可达）")
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                .bufferedReader().readText()
            val resp = JSONObject(body).optJSONObject("response")
                ?: throw IOException("Steam 返回格式异常")
            val arr = resp.optJSONArray("games") ?: return emptyList()
            val result = ArrayList<SteamGame>(arr.length())
            for (i in 0 until arr.length()) {
                val g = arr.getJSONObject(i)
                result.add(
                    SteamGame(
                        appid = g.optLong("appid", 0),
                        name = g.optString("name", "App ${g.optLong("appid")}"),
                        playtimeMinutes = g.optLong("playtime_forever", 0),
                        lastPlayedEpoch = if (g.has("rtime_last_played")) g.optLong("rtime_last_played") else null,
                    )
                )
            }
            return result.filter { it.playtimeMinutes > 0 }.sortedByDescending { it.playtimeMinutes }
        } finally {
            conn.disconnect()
        }
    }
}
