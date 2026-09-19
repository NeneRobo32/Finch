package dev.cao.finch.data

import org.json.JSONArray

/**
 * 游戏在线搜索，三源自动容错：
 * 1) Bangumi（国内直连，中文名/别名、封面、平台信息）
 * 2) IGDB（Twitch 旗下资料库，主机游戏全，需用户自填 Twitch App 凭证）
 * 3) Steam 商店搜索（PC 游戏，免 key）
 * 同时搜本地库（已添加过的游戏），保证断网也能搜到历史条目。
 */
object GameSearchClient {

    data class Item(
        val name: String,
        val nameCn: String?,
        val coverUrl: String?,
        val platforms: Set<Platform>,
        val steamAppId: Long? = null,
        val igdbId: Long? = null,
        val source: String, // "bangumi" / "igdb" / "steam" / "local"
        val localId: Long? = null,
    )

    /** Bangumi 平台名 → Finch 平台 */
    private fun mapBangumiPlatform(nameCn: String): Platform? = when {
        nameCn.contains("PC") || nameCn.contains("Steam") || nameCn.contains("Windows") -> Platform.PC
        nameCn.contains("Switch") || nameCn.contains("NS") -> Platform.SWITCH
        nameCn.contains("PS") || nameCn.contains("PlayStation") -> Platform.PS
        else -> null
    }

    suspend fun searchLocal(query: String, dao: GameDao): List<Item> =
        dao.searchByName("%${query.trim()}%").map {
            Item(
                name = it.name, nameCn = null, coverUrl = it.coverUrl,
                platforms = it.platformSet(), source = "local", localId = it.id,
            )
        }

    data class OnlineResult(val items: List<Item>, val notes: List<String>)

    /** IGDB 凭证（Client ID + 有效 token）；null=未配置，搜索链跳过 IGDB */
    data class IgdbCred(val clientId: String, val token: String)

    fun searchOnline(
        query: String,
        igdb: IgdbCred? = null,
        igdbTokenRefresh: (() -> IgdbCred?)? = null,
    ): OnlineResult {
        val out = mutableListOf<Item>()
        val notes = mutableListOf<String>()
        // 源1：Bangumi
        try {
            out += BangumiClient.search(query).map { r ->
                Item(
                    name = r.nameCn ?: r.name,
                    nameCn = r.nameCn,
                    coverUrl = r.coverUrl,
                    platforms = r.platforms.mapNotNull(::mapBangumiPlatform).toSet()
                        .ifEmpty { setOf(Platform.Multi) },
                    source = "bangumi",
                )
            }
        } catch (e: Exception) {
            notes += "bangumi✗(${e.message ?: "异常"})"
        }
        // 源2：IGDB（主机游戏全，需用户自填 Twitch App 凭证）
        val cred = igdb ?: igdbTokenRefresh?.invoke()
        if (cred != null && cred.clientId.isNotBlank() && cred.token.isNotBlank()) {
            try {
                val igdbResults = IgdbClient.search(query, cred.clientId, cred.token)
                out += igdbResults.map { r ->
                    Item(
                        name = r.name,
                        nameCn = null,
                        coverUrl = r.coverUrl,
                        platforms = r.platforms.ifEmpty { setOf(Platform.Multi) },
                        igdbId = r.igdbId,
                        source = "igdb",
                    )
                }
                if (igdbResults.isEmpty()) notes += "igdb(无匹配)"
            } catch (e: IgdbAuthException) {
                notes += "igdb✗(授权失败，重填凭证)"
            } catch (e: Exception) {
                notes += "igdb✗(${e.message ?: "异常"})"
            }
        } else {
            notes += "igdb(未填凭证)"
        }
        // 源3：Steam 商店
        try {
            out += SteamStoreClient.search(query).map { r ->
                Item(
                    name = r.name, nameCn = null, coverUrl = r.coverUrl,
                    platforms = setOf(Platform.PC), steamAppId = r.appid, source = "steam",
                )
            }
        } catch (e: Exception) {
            notes += "steam✗(${e.message ?: "异常"})"
        }
        return OnlineResult(dedupe(out), notes)
    }

    /** Bangumi 平台中文名 → Finch 平台（日历页也用；IGDB 走自带映射，不经这里） */
    fun mapPlatformName(name: String): Platform? = when {
        name.contains("Switch", true) -> Platform.SWITCH
        name.contains("PlayStation", true) || name.startsWith("PS", true) -> Platform.PS
        name.contains("PC", true) || name.contains("Windows", true) || name.contains("Steam", true) || name.contains("Mac", true) || name.contains("Linux", true) -> Platform.PC
        name.contains("Xbox", true) -> null
        else -> null
    }

    fun dedupe(list: List<Item>): List<Item> {
        val seen = mutableSetOf<String>()
        return list.filter { seen.add(it.name.lowercase().replace(" ", "")) }
    }

    fun emptyJsonArray(): JSONArray = JSONArray()
}
