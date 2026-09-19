package dev.cao.finch.data

import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * IGDB（api.igdb.com，Twitch 旗下游戏资料库）客户端 —— okhttp 直连，无新依赖。
 *
 * 接入点（Apicalypse 纯文本 POST body）：
 *  1) 按名搜索：POST /v4/games，`search "名"; fields ...; limit; where version_parent = null;`
 *     → name / cover.image_id / platforms.name / first_release_date / id
 *  2) 封面拼 URL：https://images.igdb.com/igdb/image/upload/t_cover_big/{image_id}.jpg
 *  3) 平台归一化：name 含 PC/Windows/Steam/Mac/Linux→PC；Switch/NS→SWITCH；
 *     PlayStation/PS→PS；其余（Xbox/手机/街机…）→ null（过滤）
 *
 * 认证（Twitch App token，用户自填 Client ID/Secret，见 SettingsStore）：
 *  POST https://id.twitch.tv/oauth2/token?client_id=..&client_secret=..&grant_type=client_credentials
 *  → access_token + expires_in（秒）。调用方缓存 + 提前 1 天刷新（见 IgdbAuth）。
 */
object IgdbClient {

    data class Result(
        val igdbId: Long,
        val name: String,
        val coverUrl: String?,
        val platforms: Set<Platform>,
        val firstReleaseUnix: Long?,
    )

    private const val API = "https://api.igdb.com/v4"
    private val client by lazy { BangumiClient.client } // 复用共享直连客户端

    data class RawGame(
        val igdbId: Long,
        val name: String,
        val coverImageId: String?,
        val platformNames: List<String>,
        val firstReleaseUnix: Long?,
    )

    fun search(query: String, clientId: String, token: String, limit: Int = 12): List<Result> {
        val body = buildString {
            append("search \"${query.replace("\"", "")}\"; ")
            append("fields name,cover.image_id,platforms.name,first_release_date; ")
            append("limit ${limit.coerceIn(1, 50)}; ")
            append("where version_parent = null;")
        }.toRequestBody("text/plain".toMediaType())
        val req = Request.Builder().url("$API/games")
            .header("Client-ID", clientId)
            .header("Authorization", "Bearer $token")
            .header("User-Agent", "finch-app/0.15 (Android; game time tracker)")
            .post(body)
            .build()
        val json = client.newCall(req).execute().use { resp ->
            if (resp.code == 401 || resp.code == 403) throw IgdbAuthException("IGDB 授权失败（Client ID/Secret 不对或 token 过期）")
            if (!resp.isSuccessful) throw IOException("IGDB HTTP ${resp.code}")
            resp.body?.string() ?: throw IOException("空响应")
        }
        return mapResults(parseRaw(JSONArray(json)))
    }

    /** JSON → 中间结构（唯一碰 org.json 的地方，方便单测绕过 stub） */
    internal fun parseRaw(arr: JSONArray): List<RawGame> {
        val out = mutableListOf<RawGame>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optLong("id")
            val name = o.optString("name")
            if (id <= 0 || name.isBlank()) continue
            val coverId = o.optJSONObject("cover")?.optString("image_id")?.takeIf { it.isNotBlank() }
            val plats = mutableListOf<String>()
            val parr = o.optJSONArray("platforms")
            if (parr != null) {
                for (j in 0 until parr.length()) {
                    parr.optJSONObject(j)?.optString("name")?.takeIf { it.isNotBlank() }?.let { plats += it }
                }
            }
            out += RawGame(id, name, coverId, plats, o.optLong("first_release_date").takeIf { it > 0 })
        }
        return out
    }

    /** 中间结构 → 展示结构（纯逻辑，可单测） */
    internal fun mapResults(raw: List<RawGame>): List<Result> = raw.map { r ->
        Result(
            igdbId = r.igdbId,
            name = r.name,
            coverUrl = r.coverImageId?.let { "https://images.igdb.com/igdb/image/upload/t_cover_big/$it.jpg" },
            platforms = r.platformNames.mapNotNull(::mapPlatformName).toSet(),
            firstReleaseUnix = r.firstReleaseUnix,
        )
    }

    internal fun parseGames(arr: JSONArray): List<Result> = mapResults(parseRaw(arr))

    /** IGDB 平台名 → Finch 平台；Xbox/手机/街机等返回 null（搜索链里直接丢弃） */
    internal fun mapPlatformName(name: String): Platform? = when {
        name.isBlank() -> null
        name.contains("Xbox", true) -> null
        name.contains("Switch", true) -> Platform.SWITCH
        // "Nintendo Switch" 含 Switch 已命中；NS 简写兜底（避免误伤含 ns 的词，加边界）
        Regex("\\bNS\\b").containsMatchIn(name) -> Platform.SWITCH
        name.contains("PlayStation", true) || name.startsWith("PS", true) -> Platform.PS
        name.contains("PC", true) || name.contains("Windows", true) ||
            name.contains("Steam", true) || name.contains("Mac", true) ||
            name.contains("Linux", true) -> Platform.PC
        else -> null
    }
}

/** IGDB 授权失败（401/403）：调用方清 token 并提示重填 */
class IgdbAuthException(message: String) : IOException(message)

/**
 * Twitch App token 获取+缓存（纯逻辑，方便单测）：
 * SettingsStore 存 clientId/clientSecret/token/expiresAt；过期前 1 天提前刷新。
 */
object IgdbAuth {
    data class Token(val accessToken: String, val expiresAtMillis: Long)

    fun needRefresh(expiresAtMillis: Long, nowMillis: Long = System.currentTimeMillis()): Boolean =
        expiresAtMillis <= 0 || nowMillis + 24 * 3600_000L >= expiresAtMillis

    fun requestToken(clientId: String, clientSecret: String): Token {
        val url = "https://id.twitch.tv/oauth2/token" +
            "?client_id=${java.net.URLEncoder.encode(clientId.trim(), "UTF-8")}" +
            "&client_secret=${java.net.URLEncoder.encode(clientSecret.trim(), "UTF-8")}" +
            "&grant_type=client_credentials"
        val req = Request.Builder().url(url)
            .post(FormBody.Builder().build())
            .header("User-Agent", "finch-app/0.15 (Android; game time tracker)")
            .build()
        // token 接口走独立短超时客户端（BangumiClient.client 是长复用，不混用）
        val json = BangumiClient.client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (resp.code == 400 || resp.code == 401 || resp.code == 403) {
                throw IgdbAuthException("Twitch 授权失败（Client ID/Secret 不对）")
            }
            if (!resp.isSuccessful) throw IOException("Twitch token HTTP ${resp.code}")
            body
        }
        val o = JSONObject(json)
        val token = o.optString("access_token")
        if (token.isBlank()) throw IOException("Twitch token 响应缺 access_token")
        val expiresInSec = o.optLong("expires_in", 3600L).coerceAtLeast(60L)
        return Token(token, System.currentTimeMillis() + expiresInSec * 1000)
    }
}
