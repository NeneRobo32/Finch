package dev.cao.finch.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.cao.finch.FinchApp
import dev.cao.finch.data.EshopClient
import dev.cao.finch.data.Game
import dev.cao.finch.data.GameRepository
import dev.cao.finch.data.GamerskyClient
import dev.cao.finch.data.Platform
import dev.cao.finch.data.PsnStoreClient
import dev.cao.finch.data.ReleaseFollow
import dev.cao.finch.data.networkErrorKind
import dev.cao.finch.notify.ReleaseCheckWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * 发售日程页：PSN 港服（预购 + 折扣 + 价格）/ 游民星空 / eShop 三源。
 *
 * 关注（☆）的条目会连同**价格快照**一起写进 release_follows（v0.29 起），
 * 供「愿望单」页离线查看价格/折扣/发售日；每次拉港服数据时顺手刷新关注表里的价。
 */
class UpcomingViewModel(app: Application) : AndroidViewModel(app) {
    private val db = (app as FinchApp).database
    private val gameDao = db.gameDao()
    private val followDao = db.releaseFollowDao()
    private val cacheSp = app.getSharedPreferences("finch_upcoming", android.content.Context.MODE_PRIVATE)

    /** 已关注的 key 集合（日程行显示 ★ 状态用） */
    val followKeys: StateFlow<Set<String>> = followDao.observeKeys()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /** 愿望单：关注的游戏（带价格快照），按发售日/加入时间倒序 */
    val follows: StateFlow<List<ReleaseFollow>> = followDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 愿望单页「正在同步价格」标记（只看游民/eShop 时刷新愿望单价格也会亮） */
    private val _wishlistRefreshing = MutableStateFlow(false)
    val wishlistRefreshing: StateFlow<Boolean> = _wishlistRefreshing

    /** 关注去重键：bangumi / steam 优先，否则归一化名字（口径统一在 ReleaseCheckWorker.followKey，单测直测生产口径） */
    fun followKeyOf(entry: UpcomingEntry): String =
        ReleaseCheckWorker.followKey(entry.bangumiId, entry.steamAppId, entry.name)

    /** 切换关注（关注默认提前 3 天提醒；取消直接删）。关注瞬间把当时的价格一起存下来 */
    fun toggleFollow(entry: UpcomingEntry) {
        viewModelScope.launch {
            val key = followKeyOf(entry)
            if (followDao.byKey(key) != null) {
                followDao.deleteByKey(key)
            } else {
                followDao.insert(
                    ReleaseFollow(
                        key = key,
                        name = entry.name,
                        coverUrl = entry.coverUrl,
                        dateIso = entry.date?.toString(),
                        source = entry.source,
                        notifyDays = 3,
                        // v0.29：愿望单页要能看到价格，所以关注时就把港服价存成快照
                        priceText = entry.priceText,
                        basePriceText = entry.basePriceText,
                        discountText = entry.discountText,
                        saleEndIso = entry.saleEndsAt?.toString(),
                        productId = entry.productId,
                    )
                )
                // 顺手排一次后台检查（应用在前台时立刻有一条兜底判断，不等明天）
                runCatching {
                    ReleaseCheckWorker.schedule(getApplication())
                }
            }
        }
    }

    /** 愿望单页取消关注：按 key 直接删（愿望单条目只有 ReleaseFollow，没有 UpcomingEntry） */
    fun toggleFollowByKey(key: String) {
        viewModelScope.launch {
            followDao.deleteByKey(key)
        }
    }

    data class UpcomingEntry(
        val name: String,
        val nameCn: String?,
        val coverUrl: String?,
        val date: LocalDate?,
        val releaseText: String?,
        val platforms: Set<Platform>,
        val bangumiId: Long? = null,
        val steamAppId: Long? = null,
        val source: String, // "psn" / "gamersky" / "eshop"
        val expectation: Int = 0,  // 游民期待值/评分（热度信号）
        val added: Boolean = false,
        // PSN 港服价格（其他源全为 null）：现价 / 原价（仅打折时非空，UI 划线）/ 折扣文案 "-70%"
        val priceText: String? = null,
        val basePriceText: String? = null,
        val discountText: String? = null,
        /** 该条目来自「正在打折」栏目（而非预购），日程页分组用 */
        val deal: Boolean = false,
        /** 本档折扣统一截止日（仅 deal 条目有，来源为 PSN campaign endTime） */
        val saleEndsAt: LocalDate? = null,
        /** 港服商品 ID：拼商店链接、愿望单价格同步都用它 */
        val productId: String? = null,
    )

    sealed class LoadState {
        object Idle : LoadState()
        object Loading : LoadState()
        data class Ready(
            val entries: List<UpcomingEntry>,   // 展示用（已按"大作"过滤）
            val allEntries: List<UpcomingEntry>, // 全量（搜索用，不过滤）
        ) : LoadState()
        data class Failed(val message: String, val hadCache: Boolean) : LoadState()
    }

    private val _state = MutableStateFlow<LoadState>(LoadState.Idle)
    val state: StateFlow<LoadState> = _state

    /** 数据源：港服放最前并作为默认（有价格的那个源最常用） */
    enum class Source(val label: String) {
        PSN("PSN 港服"), GAMERSKY("游民星空"), ESHOP("eShop"),
    }

    private val _source = MutableStateFlow(Source.PSN)
    val source: StateFlow<Source> = _source

    private var loaded: List<UpcomingEntry> = emptyList()

    init {
        refresh()
    }

    private fun friendMsg(e: Exception): String = when (e.networkErrorKind()) {
        dev.cao.finch.data.NetworkErrorKind.TIMEOUT -> "连接超时"
        dev.cao.finch.data.NetworkErrorKind.DNS -> "域名解析失败"
        dev.cao.finch.data.NetworkErrorKind.SSL -> "SSL中断/响应不完整"
        dev.cao.finch.data.NetworkErrorKind.CONNECT,
        dev.cao.finch.data.NetworkErrorKind.IO -> "网络错误：" + (e.message ?: e.javaClass.simpleName)
        dev.cao.finch.data.NetworkErrorKind.OTHER -> (e.message ?: e.javaClass.simpleName)
    }

    fun refresh() {
        _state.value = LoadState.Loading
        // 只拉当前选中的源
        val sel = _source.value
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                try {
                    when (sel) {
                        Source.PSN -> fetchPsnEntries()
                        Source.GAMERSKY -> fromGamersky(GamerskyClient.fetchRestOfYear())
                        Source.ESHOP -> fromEshop(EshopClient.fetchRecent())
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e // 取消不是失败
                    Log.w("Upcoming", "${sel.label} ✗ ${friendMsg(e)}")
                    emptyList()
                }
            }
            if (r.isNotEmpty()) {
                // 港服数据到手就顺手刷新愿望单里的价格快照
                if (sel == Source.PSN) {
                    withContext(Dispatchers.IO) { syncFollowPrices(r) }
                }
                mergeAndPublish(r)
            } else {
                _state.value = LoadState.Failed(
                    "${sel.label} 没有返回数据（网络或源问题）",
                    cacheSp.contains("psn_json"),
                )
            }
        }
    }

    /**
     * 愿望单页刷新：即使当前停在别的源，也单独拉一次港服价并同步到关注表。
     * 当前源就是港服时直接走 [refresh]（那份数据本来就要展示）。
     */
    fun refreshWishlist() {
        if (_source.value == Source.PSN) {
            refresh()
            return
        }
        if (_wishlistRefreshing.value) return
        viewModelScope.launch {
            _wishlistRefreshing.value = true
            try {
                val entries = withContext(Dispatchers.IO) {
                    runCatching { fetchPsnEntries() }.getOrNull()
                }
                if (entries != null) {
                    withContext(Dispatchers.IO) { syncFollowPrices(entries) }
                }
            } finally {
                _wishlistRefreshing.value = false
            }
        }
    }

    /** 港服取数：成功后写缓存；失败回落上次缓存（离线也能看上次的价），都没有则抛给上层 */
    private suspend fun fetchPsnEntries(): List<UpcomingEntry> {
        return try {
            val feed = PsnStoreClient.fetchFeed()
            cacheSp.edit().putString("psn_json", PsnStoreClient.toJson(feed)).apply()
            fromPsn(feed)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val cached = cacheSp.getString("psn_json", null)?.let { PsnStoreClient.fromJson(it) }
            if (cached != null && !cached.isEmpty) {
                Log.w("Upcoming", "PSN 港服取数失败，用缓存（${friendMsg(e)}）")
                fromPsn(cached)
            } else {
                throw e
            }
        }
    }

    /**
     * 把港服现价同步进关注表（愿望单页的数据源）。
     * 同 key 优先保留第一条 —— 预购条目排在折扣条目前面，发售日不会被折扣条目的空日期覆盖。
     */
    private suspend fun syncFollowPrices(entries: List<UpcomingEntry>) {
        val priced = entries.filter { it.productId != null }
        if (priced.isEmpty()) return
        val byKey = LinkedHashMap<String, UpcomingEntry>(priced.size)
        for (e in priced) byKey.putIfAbsent(followKeyOf(e), e)
        var changed = 0
        for (f in followDao.all()) {
            val e = byKey[f.key] ?: continue
            val saleEndIso = e.saleEndsAt?.toString()
            if (f.priceText == e.priceText && f.basePriceText == e.basePriceText &&
                f.discountText == e.discountText && f.saleEndIso == saleEndIso &&
                f.productId == e.productId
            ) {
                continue
            }
            followDao.updatePrice(
                key = f.key,
                priceText = e.priceText,
                basePriceText = e.basePriceText,
                discountText = e.discountText,
                saleEndIso = saleEndIso,
                productId = e.productId,
            )
            changed++
        }
        if (changed > 0) Log.d("Upcoming", "愿望单价格同步 $changed 条")
    }

    /** 全量结果发布（去重 + 排序后展示） */
    private suspend fun mergeAndPublish(all: List<UpcomingEntry>) {
        if (all.isEmpty()) return
        publish(rankEntries(all.distinctBy { it.name.lowercase().replace(" ", "") }))
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
            // 折扣条目（都在同一桶、都没有日期）按力度降序：PSN 端 pickDeals 已排好，
            // 这里若再按名字排会把 -90% 混到 -30% 后面
            { if (it.deal) -PsnStoreClient.discountPercent(it.discountText) else 0 },
            { -major(it) },                         // 大作分降序
            { it.name.lowercase() },
        ))
    }

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
        // PSN 港服：预购前沿一页仅 ~33 个标题、打折页已按 discountText 筛过，
        // 是商店自己给的「新品/在促」清单，再按大作分滤会把冷门独立游戏全砍掉，故不过滤
        if (e.source == "psn") return true
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
                // 别把发行商当成中文别名塞进 nameCn：卡片会在标题下面多出一行
                // 「SQUARE ENIX」这类厂牌，看起来像游戏名的第二半，观感很脏。
                // 游民星空自己的条目名本来就是中文，单行标题就够。
                nameCn = null,
                coverUrl = e.coverUrl,
                date = e.releaseDate,
                releaseText = e.releaseRaw,
                platforms = emptySet(), // 解析时按页区分：pc→PC / hgc→Multi
                source = "gamersky",
                expectation = e.expectation,
            )
        }

    /**
     * PSN 港服 → 条目：预购新作（带发售日、原价）+ 正在打折（带原价划线、折扣角标、截止日）。
     * 折扣条目的 date 置空 —— 它们不是「即将发售」，日程分组会落到「更远期」桶，
     * 但 deal=true 会在 UI 上单独标题，不靠日期表达。
     */
    private fun fromPsn(feed: PsnStoreClient.Feed): List<UpcomingEntry> {
        val pre = feed.preorders.map { i ->
            UpcomingEntry(
                name = i.name,
                nameCn = null,
                coverUrl = i.coverUrl,
                date = i.releaseDate,
                releaseText = if (i.releaseDate == null) "发售日待定" else null,
                platforms = i.platforms,
                source = "psn",
                priceText = i.priceText,
                basePriceText = null,       // 预购不打折：只显示售价
                discountText = null,
                saleEndsAt = null,
                productId = i.productId,
            )
        }
        val deal = feed.deals.map { i ->
            UpcomingEntry(
                name = i.name,
                nameCn = null,
                coverUrl = i.coverUrl,
                date = null,
                releaseText = null,
                platforms = i.platforms,
                source = "psn",
                priceText = i.priceText,
                basePriceText = i.basePriceText,
                discountText = i.discountText,
                deal = true,
                saleEndsAt = feed.saleEndsAt,
                productId = i.productId,
            )
        }
        return pre + deal
    }

    private suspend fun publish(entries: List<UpcomingEntry>) {
        // 标记哪些已入库：优先按平台 ID，港服条目没有 ID 就按名字（与 upsertGame 的合并口径一致）
        val marked = withContext(Dispatchers.IO) {
            entries.map { e ->
                val added = when {
                    e.bangumiId != null -> gameDao.byBangumiId(e.bangumiId) != null
                    e.steamAppId != null -> gameDao.bySteamAppId(e.steamAppId) != null
                    else -> gameDao.allByName(e.name).isNotEmpty()
                }
                e.copy(added = added)
            }
        }
        refreshDisplay(marked)
    }

    /** 按当前选中的源生成展示列表（全量保留在 allEntries；展示=该源+大作过滤，过滤后空则给该源全量） */
    private fun refreshDisplay(all: List<UpcomingEntry>) {
        val sel = _source.value
        val bySource = all.filter { it.source == sel.name.lowercase() }
        val display = bySource.filter(::isKeep).ifEmpty { bySource }
        loaded = display
        // 上限 300 条（全年展示足够，渲染不卡；排序后取前 N）
        val capped = if (display.size > 300) display.take(300) else display
        _state.value = LoadState.Ready(capped, all)
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
            publish(loaded)
        }
    }
}
