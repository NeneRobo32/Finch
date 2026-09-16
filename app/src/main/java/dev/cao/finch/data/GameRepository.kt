package dev.cao.finch.data

/** 游戏条目的公共仓库逻辑：多平台 CSV 编解码、按名合并、平台归属判断 */
object GameRepository {

    private val displayOrder = listOf(Platform.PC, Platform.SWITCH, Platform.PS)

    fun csvToPlatforms(csv: String?): List<Platform> =
        csv?.split(',')?.mapNotNull { runCatching { Platform.valueOf(it.trim()) }.getOrNull() }
            .orEmpty()

    fun platformsToCsv(platforms: Collection<Platform>): String =
        displayOrder.filter { platforms.contains(it) }.joinToString(",") { it.name }

    /** 展示用标签：PC / Switch / PS / PC+Switch */
    fun labelFor(platforms: Collection<Platform>): String =
        displayOrder.filter { platforms.contains(it) }.joinToString("+") { shortLabel(it) }
            .ifEmpty { "Multi" }

    fun shortLabel(p: Platform): String = when (p) {
        Platform.PC -> "PC"
        Platform.SWITCH -> "Switch"
        Platform.PS -> "PS"
        Platform.Multi -> "Multi"
    }

    /** 该游戏是否包含某平台（多平台任一命中即可） */
    fun supportsPlatform(game: Game, p: Platform): Boolean {
        val set = game.platformSet()
        return p in set || (p != Platform.Multi && set == setOf(Platform.Multi))
    }

    /**
     * 按名字找可合并的游戏：
     * 1) 名字完全一致且平台有交集 → 返回它（调用方更新封面/平台）
     * 2) 否则返回 null（调用方新建）
     */
    suspend fun findMergeable(dao: GameDao, name: String, platforms: Set<Platform>): Game? {
        val candidates = dao.allByName(name.trim())
        for (c in candidates) {
            val existing = c.platformSet().filter { it != Platform.Multi }.toSet()
            val incoming = platforms.filter { it != Platform.Multi }.toSet()
            if (existing.intersect(incoming).isNotEmpty() || existing.isEmpty() || incoming.isEmpty()) {
                return c
            }
        }
        return null
    }

    /** 新建/合并一条游戏记录，返回其 id */
    suspend fun upsertGame(
        dao: GameDao,
        name: String,
        platforms: Set<Platform>,
        coverUrl: String?,
        bangumiId: Long? = null,
        steamAppId: Long? = null,
    ): Long {
        val cleanName = name.trim()
        val existing = findMergeable(dao, cleanName, platforms)
        if (existing != null) {
            val merged = (existing.platformSet() + platforms).filter { it != Platform.Multi }.toSet()
            val csv = platformsToCsv(merged)
            dao.update(
                existing.copy(
                    platformsCsv = csv.ifEmpty { null },
                    coverUrl = existing.coverUrl ?: coverUrl, // 已有封面不覆盖
                    platform = mainPlatform(merged, fallback = existing.platform),
                    bangumiId = existing.bangumiId ?: bangumiId,
                    steamAppId = existing.steamAppId ?: steamAppId,
                )
            )
            return existing.id
        }
        val main = mainPlatform(platforms, fallback = Platform.PC)
        return dao.insert(
            Game(
                name = cleanName,
                platform = main,
                platformsCsv = platformsToCsv(platforms).ifEmpty { null },
                coverUrl = coverUrl,
                bangumiId = bangumiId,
                steamAppId = steamAppId,
            )
        )
    }

    fun mainPlatform(platforms: Set<Platform>, fallback: Platform): Platform {
        val real = platforms.filter { it != Platform.Multi }
        if (real.isEmpty()) return fallback
        return displayOrder.firstOrNull { it in real } ?: real.first()
    }
}
