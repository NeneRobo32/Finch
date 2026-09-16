package dev.cao.finch.data

import org.json.JSONObject
import java.io.IOException

/**
 * Steam Web API 最小客户端（IPlayerService/GetOwnedGames）。
 * 国内直连 api.steampowered.com 可能不通，支持自定义反代地址。
 * 走共享 okhttp 直连客户端（BangumiClient.client），不稳定代理下同样可靠。
 */
object SteamClient {

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
        val req = okhttp3.Request.Builder()
            .url(url)
            .header("User-Agent", "finch-app/0.10.8")
            .build()
        BangumiClient.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("Steam API HTTP ${resp.code}（检查 Key/SteamID，或网络是否可达）")
            }
            val body = resp.body?.string() ?: throw IOException("Steam 返回为空")
            val respJson = JSONObject(body).optJSONObject("response")
                ?: throw IOException("Steam 返回格式异常")
            val arr = respJson.optJSONArray("games") ?: return emptyList()
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
        }
    }
}
