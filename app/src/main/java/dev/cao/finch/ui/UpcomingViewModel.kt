package dev.cao.finch.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.cao.finch.FinchApp
import dev.cao.finch.data.BangumiClient
import dev.cao.finch.data.EshopClient
import dev.cao.finch.data.Game
import dev.cao.finch.data.GameRepository
import dev.cao.finch.data.GameSearchClient
import dev.cao.finch.data.GamerskyClient
import dev.cao.finch.data.Platform
import dev.cao.finch.data.SteamStoreClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** 发售日程页：Bangumi 日历 + Steam「即将推出」双源，质量过滤，离线缓存，一键入库 */
class UpcomingViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as FinchApp).database
    private val gameDao = db.gameDao()
    private val cacheSp = app.getSharedPreferences("finch_upcoming", android.content.Context.MODE_PRIVATE)

    data class UpcomingEntry(
        val name: String,
        val nameCn: String?,
        val coverUrl: String?,
        val date: LocalDate?,
        val releaseText: String?,
        val platforms: Set<Platform>,
        val bangumiId: Long? = null,
        val steamAppId: Long? = null,
        val source: String, // "gamersky" / "bangumi" / "steam" / "eshop"
        val expectation: Int = 0,  // 游民期待值/评分（热度信号）
        val added: Boolean = false,
    )

    sealed class LoadState {
        object Idle : LoadState()
        object Loading : LoadState()
        data class Ready(
            val entries: List<UpcomingEntry>,   // 展示用（已按"大作"过滤）
            val allEntries: List<UpcomingEntry>, // 全量（搜索用，不过滤）
            val note: String?,
        ) : LoadState()
        data class Failed(val message: String, val hadCache: Boolean) : LoadState()
    }

    private val _state = MutableStateFlow<LoadState>(LoadState.Idle)
    val state: StateFlow<LoadState> = _state

    enum class Source(val label: String) {
        GAMERSKY("游民星空"), ESHOP("eShop"), STEAM("Steam"), BANGUMI("Bangumi"),
    }

    private val _source = MutableStateFlow(Source.GAMERSKY)
    val source: StateFlow<Source> = _source

    private var loaded: List<UpcomingEntry> = emptyList()

    init {
        loadCache()
        refresh()
    }

    private fun loadCache() {
        val cached = cacheSp.getString("calendar_json", null) ?: return
        viewModelScope.launch {
            val entries = runCatching { fromBangumi(BangumiClient.parseCalendar(cached)) }.getOrDefault(emptyList())
            if (entries.isNotEmpty()) publish(entries, "缓存（Bangumi）")
        }
    }

    private fun friendMsg(e: Exception): String = when (e) {
        is java.net.SocketTimeoutException -> "连接超时"
        is java.net.UnknownHostException -> "域名解析失败"
        is javax.net.ssl.SSLException -> "SSL中断/响应不完整"
        is java.io.IOException -> "网络错误：" + (e.message ?: e.javaClass.simpleName)
        else -> (e.message ?: e.javaClass.simpleName)
    }

    fun refresh() {
        _state.value = LoadState.Loading
        // 只拉当前选中的源（不再每次全拉 4 源）
        val sel = _source.value
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                try {
                    when (sel) {
                        Source.GAMERSKY -> fromGamersky(GamerskyClient.fetchRestOfYear())
                        Source.ESHOP -> fromEshop(EshopClient.fetchRecent())
                        Source.STEAM -> fromSteam(SteamStoreClient.fetchComingSoon())
                        Source.BANGUMI -> {
                            var out = emptyList<UpcomingEntry>()
                            for (base in listOf("https://api.bgm.tv", "https://bangumi.tv")) {
                                try {
                                    val raw = BangumiClient.fetchCalendarRaw(base)
                                    cacheSp.edit().putString("calendar_json", raw).apply()
                                    out = fromBangumi(BangumiClient.parseCalendar(raw))
                                    if (out.isNotEmpty()) break
                                } catch (_: Exception) { /* 试下一个域名 */ }
                            }
                            out
                        }
                    }
                } catch (e: Exception) {
                    Log.w("Upcoming", "${sel.label} ✗ ${friendMsg(e)}")
                    emptyList()
                }
            }
            if (r.isNotEmpty()) {
                mergeAndPublish(r, null)
            } else {
                _state.value = LoadState.Failed("${sel.label} 没有返回数据（网络或源问题）", cacheSp.contains("calendar_json"))
            }
        }
    }

    /** 全量结果发布（results 已汇总所有成功源，去重+排序后展示） */
    private suspend fun mergeAndPublish(all: List<UpcomingEntry>, note: String? = null) {
        if (all.isEmpty()) return
        val merged = rankEntries(all.distinctBy { it.name.lowercase().replace(" ", "") })
        publish(merged, note ?: _state.value.let { (it as? LoadState.Ready)?.note })
    }

    /** 切换数据源：立即拉取该源（不再共用旧数据） */
    fun setSource(s: Source) {
        if (_source.value == s) return
        _source.value = s
        refresh()
    }

    /** 排序：已发售 → 本年度 → 更远期；同桶按日期升序，无日期按大作分排 → 全面 */
    private fun rankEntries(entries: List<UpcomingEntry>): List<UpcomingEntry> {
        val now = LocalDate.now()
        fun dayBucket(e: UpcomingEntry): Int = when (val d = e.date) {
            null -> 2 // 无日期 → 远期桶
            else -> when {
                d < now.minusDays(1) -> 0 // 已发售（含去年 12/31 前的）
                d.year == now.year -> 1   // 本年度剩余
                else -> 2                  // 更远期
            }
        }
        fun major(e: UpcomingEntry): Int {
            var s = MajorGameScorer.score(e.name, e.nameCn)
            if (e.source == "bangumi") s += 1
            // 游民期待值：热度信号，1万+ 顶级大作/5千+ 一线/1千+ 二线
            if (e.source == "gamersky") s += when {
                e.expectation >= 10000 -> 5
                e.expectation >= 3000 -> 4
                e.expectation >= 1000 -> 3
                else -> 2
            }
            return s
        }

        return entries.sortedWith(compareBy(
            { dayBucket(it) },                      // 0 近期 → 1 未来 → 2 远期
            { if (it.date == null) 1 else 0 },      // 有日期的优先
            { it.date ?: LocalDate.MAX },           // 日期升序
            { -major(it) },                         // 大作分降序
            { it.name.lowercase() },
        ))
    }

    /** 同名去重（Bangumi 优先） */
    private fun merge(a: List<UpcomingEntry>, b: List<UpcomingEntry>, c: List<UpcomingEntry>): List<UpcomingEntry> {
        return rankEntries((a + b + c).distinctBy { it.name.lowercase().replace(" ", "") })
    }

    private fun fromBangumi(list: List<BangumiClient.CalendarEntry>): List<UpcomingEntry> = list.asSequence()
        .map { e ->
            UpcomingEntry(
                name = e.nameCn ?: e.name,
                // 副标题显示另一个名字（中文名做标题时显示原名，反之亦然）
                nameCn = if (e.nameCn != null && e.name != e.nameCn) e.name else null,
                coverUrl = e.coverUrl,
                date = e.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                releaseText = null,
                platforms = e.platforms.mapNotNull(GameSearchClient::mapPlatformName).toSet(),
                bangumiId = e.bangumiId,
                source = "bangumi",
            )
        }
        .toList()

    /**
     * 过滤规则（只留大作，小游戏全滤掉）：
     * 1) 黑词：demo/trailer/DLC/OST/合集/收藏/设定/免费/同人/小游戏等明显非正作
     * 2) 平台：只留 PC/Switch/PS 之一；平台字段非空但全是 XBox/手机/街机等 → 滤掉；
     *    平台字段为空（未标注）→ 保留（可能还没录入平台）
     * 3) 大作信号：MajorGameScorer.score >= 3（命中知名系列/大厂）才保留，其余小作滤掉
     */
    private fun isKeep(e: UpcomingEntry): Boolean {
        // 游民星空条目已在拉取时按期待值>=300过滤（小作/边缘已丢），此处不再重复滤
        if (e.source == "gamersky") return true
        val lower = e.name.lowercase()
        if (lower.contains("demo") || lower.contains("trailer") || lower.contains("promo") || lower.contains("display")) return false
        if (lower.contains("bundle") || lower.contains("pack") || lower.contains("dlc") || lower.contains("ost")) return false
        if (lower.contains("vol.") || lower.contains("合集") || lower.contains("收藏") || lower.contains("设定") || lower.contains("画集") || lower.contains("公式书")) return false
        if (lower.contains("免费") || lower.contains("同人") || lower.contains("小游戏") || lower.contains("独立")) return false
        if (e.platforms.isEmpty()) {
            // 未标注平台：大作信号仍保留，但小作不因平台缺失漏进来
            return MajorGameScorer.score(e.name, e.nameCn) >= 3
        }
        if (e.platforms.none { it == Platform.PC || it == Platform.SWITCH || it == Platform.PS }) return false
        // 只留大作：命中知名系列/大厂（分数>=3）才保留
        return MajorGameScorer.score(e.name, e.nameCn) >= 3
    }

    private fun fromSteam(list: List<SteamStoreClient.ComingSoonGame>): List<UpcomingEntry> = list
        .map { e ->
            UpcomingEntry(
                name = e.name,
                nameCn = null,
                coverUrl = e.coverUrl,
                date = e.releaseDate,
                releaseText = e.releaseText,
                platforms = setOf(Platform.PC),
                steamAppId = e.appid,
                source = "steam",
            )
        }

    /** eShop（日区）→ 条目：平台=Switch，日期用 dsdate */
    private fun fromEshop(items: List<EshopClient.Item>): List<UpcomingEntry> = items
        .map { e ->
            UpcomingEntry(
                name = e.title,
                nameCn = null,
                coverUrl = e.coverUrl,
                date = e.releaseDate,
                releaseText = null,
                platforms = setOf(Platform.SWITCH),
                source = "eshop",
            )
        }
        // eShop 里很多是 DLC/追加角色（日文标题），滤掉：含 追加/DLC/「」/DK 的
        .filter {
            val t = it.name
            !(t.contains("追加") || t.contains("DLC") || t.contains("エキスパンション") || t.contains("パック") ||
                t.contains("- ") || t.contains("「") || t.contains("」") ||
                t.contains("キャラクター") || t.contains("衣装") || t.contains("コスチューム") ||
                t.contains("BGM") || t.contains("サウンドトラック") || t.contains("アートブック") ||
                t.contains("デジタル") || t.contains("シーズンパス") || t.contains("スタートダッシュ") ||
                t.contains("セット") || t.contains("エディション") || t.contains("特典"))
        }

    /** 游民星空 → 条目：hgc=主机(Multi/PS5/NS2)、pc=PC；期待值并入排序 */
    private fun fromGamersky(items: List<GamerskyClient.Item>): List<UpcomingEntry> = items
        .map { e ->
            UpcomingEntry(
                name = e.name,
                nameCn = e.publisher?.takeIf { it.isNotBlank() },
                coverUrl = e.coverUrl,
                date = e.releaseDate,
                releaseText = e.releaseRaw,
                platforms = emptySet(), // 解析时按页区分：pc→PC / hgc→Multi
                source = "gamersky",
                expectation = e.expectation,
            )
        }

    private suspend fun publish(entries: List<UpcomingEntry>, note: String?) {
        // 标记哪些已入库（bangumiId 或 steamAppId 命中）
        val marked = withContext(Dispatchers.IO) {
            entries.map { e ->
                val added = when {
                    e.bangumiId != null -> gameDao.byBangumiId(e.bangumiId) != null
                    e.steamAppId != null -> gameDao.bySteamAppId(e.steamAppId) != null
                    else -> false
                }
                e.copy(added = added)
            }
        }
        refreshDisplay(marked, note)
    }

    /** 按当前选中的源生成展示列表（全量保留在 allEntries；展示=该源+大作过滤，过滤后空则给该源全量） */
    private fun refreshDisplay(all: List<UpcomingEntry>, note: String?) {
        val sel = _source.value
        val bySource = all.filter { it.source == sel.name.lowercase() }
        val display = bySource.filter(::isKeep).ifEmpty { bySource }
        loaded = display
        // 上限 300 条（全年展示足够，渲染不卡；排序后取前 N）
        val capped = if (display.size > 300) display.take(300) else display
        _state.value = LoadState.Ready(capped, all, note)
    }

    /** 一键入库 */
    fun addToLibrary(entry: UpcomingEntry, extraPlatforms: Set<Platform>, onDone: (Game) -> Unit) {
        viewModelScope.launch {
            val plats = (entry.platforms + extraPlatforms).filter { it != Platform.Multi }.toSet()
                .ifEmpty { setOf(Platform.PC) }
            val id = withContext(Dispatchers.IO) {
                GameRepository.upsertGame(
                    dao = gameDao,
                    name = entry.name,
                    platforms = plats,
                    coverUrl = entry.coverUrl,
                    bangumiId = entry.bangumiId,
                    steamAppId = entry.steamAppId,
                )
            }
            val game = gameDao.byId(id) ?: Game(id = id, name = entry.name, platform = GameRepository.mainPlatform(plats, Platform.PC))
            onDone(game)
            publish(loaded, _state.value.let { (it as? LoadState.Ready)?.note })
        }
    }
}
