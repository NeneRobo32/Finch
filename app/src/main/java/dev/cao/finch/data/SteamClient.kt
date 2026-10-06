package dev.cao.finch.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.runInterruptible
import org.json.JSONObject
import java.io.IOException

/**
 * Steam Web API 最小客户端（IPlayerService/GetOwnedGames）。
 * 国内直连 api.steampowered.com 可能不通，支持自定义反代地址。
 * 走共享 okhttp 客户端（HttpClients.shared），不稳定代理下同样可靠。
 */
object SteamClient {

    data class SteamGame(val appid: Long, val name: String, val playtimeMinutes: Long, val lastPlayedEpoch: Long? = null)

    /** 重试 3 次（仅网络类/5xx/429）；退避用可取消的 delay，execute 走 runInterruptible 可被协程取消 */
    suspend fun fetchOwnedGames(baseUrl: String, apiKey: String, steamId: String): List<SteamGame> {
        var lastError: Exception? = null
        repeat(3) { attempt ->
            try {
                return fetchOnce(baseUrl, apiKey, steamId)
            } catch (e: Exception) {
                lastError = e
                if (!isRetryable(e)) throw e // 4xx（无效 Key 等）直接抛
                if (attempt < 2) delay(1000L * (attempt + 1))
            }
        }
        throw lastError ?: IOException("Steam 未知网络错误")
    }

    private suspend fun fetchOnce(baseUrl: String, apiKey: String, steamId: String): List<SteamGame> {
        val url = "${baseUrl.trimEnd('/')}/IPlayerService/GetOwnedGames/v1/" +
            "?key=$apiKey&steamid=$steamId&include_appinfo=1&include_played_free_games=1"
        val req = okhttp3.Request.Builder()
            .url(url)
            .header("User-Agent", "finch-app/0.10.8")
            .build()
        val resp = runInterruptible { HttpClients.shared.newCall(req).execute() }
        resp.use {
            if (!it.isSuccessful) {
                throw HttpStatusException(it.code, "Steam API HTTP ${it.code}（检查 Key/SteamID，或网络是否可达）")
            }
            val body = it.body?.string() ?: throw IOException("Steam 返回为空")
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
