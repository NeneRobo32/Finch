package dev.cao.finch.data

/**
 * 内存「名字 → 候选行」索引（审查 P1）。
 *
 * 背景：同步流程对每条远端游戏都调一次 `GameDao.allByName`，而 games 表没有可用索引
 * （`@Index("name")` 生成的是 BINARY 排序，查询用的是 `COLLATE NOCASE`，索引用不上；
 * Room 也不支持注解表达 collation），等于每款游戏全表扫一遍。Steam/PSN 库动辄几百条，
 * 一次同步就是几百次全表扫。这里在事务开始前读一次 `gameDao.all()`，之后全部走内存。
 *
 * 口径：先用 SQLite `COLLATE NOCASE` 等价的 ASCII 折叠键查（与旧查询结果完全一致），
 * 查不到再退到 [matchKey] 归一键（剥 ™/®、折叠空白、Unicode 小写）——PSN 官方名普遍带商标
 * 符号，旧代码在 `runPSN` 里单独挂了一份这样的兜底，这里统一收进索引。
 */
internal class GameNameIndex(initial: List<Game> = emptyList()) {

    /** 键 = [sqliteNocaseKey]，与旧 SQL 的 `= :name COLLATE NOCASE` 同口径 */
    private val exact = HashMap<String, MutableList<Game>>()

    /** 键 = [matchKey]，商标符号/空白差异的兜底 */
    private val normalized = HashMap<String, MutableList<Game>>()

    /** id → 当前登记的行，用来 O(1) 摘除（改名后要按旧名字摘） */
    private val byId = HashMap<Long, Game>()

    init {
        initial.forEach { add(it) }
    }

    /** 索引里登记一行（新建游戏后必须调用，否则同批次同名会重复建档） */
    fun add(game: Game) {
        // 同一 id 重复登记时就地替换，避免索引里挂两份
        if (byId.containsKey(game.id)) remove(game.id)
        byId[game.id] = game
        exact.getOrPut(sqliteNocaseKey(game.name)) { mutableListOf() }.add(game)
        normalized.getOrPut(matchKey(game.name)) { mutableListOf() }.add(game)
    }

    /** 行内容变了（改名/补 appId）后刷新索引 */
    fun replace(game: Game) {
        remove(game.id)
        add(game)
    }

    /** 按 id 摘掉一行 */
    fun remove(gameId: Long) {
        val old = byId.remove(gameId) ?: return
        exact.removeFrom(sqliteNocaseKey(old.name), gameId)
        normalized.removeFrom(matchKey(old.name), gameId)
    }

    /**
     * 等价于旧 `allByName(name)`：同名候选，按 id 升序（与 SQL 的 `ORDER BY id` 一致）。
     * 精确口径查不到时才用归一化口径兜底。
     */
    fun candidates(name: String): List<Game> {
        val hit = exact[sqliteNocaseKey(name)]
        if (!hit.isNullOrEmpty()) return hit.sortedBy { it.id }
        return normalized[matchKey(name)]?.sortedBy { it.id }.orEmpty()
    }

    /** 库内是否已有这一款（[dev.cao.finch.ui.UpcomingViewModel] 的「已在库」标记用） */
    fun contains(name: String): Boolean = candidates(name).isNotEmpty()
}

/**
 * SQLite `COLLATE NOCASE` 只折叠 ASCII 的 A-Z，这里照着实现，
 * 保证索引查到的候选与改造前的 `allByName` 完全一致。
 */
private fun sqliteNocaseKey(name: String): String {
    val sb = StringBuilder(name.length)
    for (c in name) sb.append(if (c in 'A'..'Z') c + ('a' - 'A') else c)
    return sb.toString()
}

/** 从某个键的桶里摘掉一行；桶空了就删键，避免同步久了留下一堆空桶 */
private fun HashMap<String, MutableList<Game>>.removeFrom(key: String, gameId: Long) {
    val bucket = this[key] ?: return
    bucket.removeAll { it.id == gameId }
    if (bucket.isEmpty()) remove(key)
}
