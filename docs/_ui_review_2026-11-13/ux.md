# Finch 体验设计审查 —— 信息架构 / 可用性 / 无障碍 / 响应式

| 项目 | 内容 |
|---|---|
| 应用 | Finch（`dev.cao.finch`） |
| 版本 | v0.29.5 |
| 审查范围 | `ui/FinchAppScaffold.kt` + 5 个 tab 页 + `WishlistPane` + `UpcomingViewModel` + `design/{Tokens,Controls,Containers,FinchBottomBar}` + `liquid/LiquidBottomTab(s)` + `notify/ReleaseCheckWorker` |
| 审查日期 | 2026-11-13 |
| 方式 | 逐文件静态阅读 + 依据 `Type.kt` 实际行高与 `Tokens.kt` 实际间距做 dp 累加 |
| 基线 | 已读 `docs/UI_REVIEW_2026-11-12.md`（上一轮 14 项），**本文只记录仍未修 / 新出现的问题**，已修项在 §0.2 列出以便对齐 |
| 约束 | 只读审查，未修改 `app/` 下任何源码 |

---

## 0. 一句话总体判断

**v0.29 这批改动确实把上一轮的密度与无障碍问题修掉了**——主页控件区从约 260dp 收到约 100dp，首屏可见卡片从 1~2 张回到 3.5~4.5 张，选中态命中区、小字强调色、分组顺序、愿望单入口也都落了地；**但「发现 → 追踪」这条路没有闭环**：愿望单只能被动查看、系统从不告知降价（`ReleaseCheckWorker` 只认发售日），日程页又被 100+ 条折扣条目撑成了一张「商店打折页」，而缓存回退的旧价格没有任何时间戳。**所以现在最该动的不是像素，是日程/愿望单的信息架构与「这条价格何时抓的」的可信度提示。**

### 0.2 上一轮（2026-11-12）已确认修复，本轮不再重复

| 上轮编号 | 修复证据 |
|---|---|
| A2 小字强调色 | `theme/AppThemes.kt:400-401` 新增 `accentText`；`design/Controls.kt:564`（`FinchBadge`）、`HomeCards.kt:308/325`、`WishlistPane.kt:226` 已改用 |
| A3 命中区 | `Controls.kt:244` `minimumInteractiveComponentSize()`（`FinchIconButton`）；`Controls.kt:605`（`FinchStars`）；`Controls.kt:312/321` Chip 上下内边距 12dp→44dp |
| B1 主页控件区 | `HomeScreen.kt:373-483` 收成「标题行(搜索/排序) + 平台分段 + 筛选图标」，状态胶囊进 `HomeFilterSheet` |
| B2 日程搜索 | `UpcomingScreen.kt:106/175` 搜索默认收起 |
| B3 分组顺序 | `UpcomingScreen.kt:263-268` 改为 待发售 → 打折 → 更远期 → 已发售 |
| B4 期数导航 | `StatsScreen.kt:294-305` 滑杆换成 `StatsPeriodSheet` |
| B5 愿望单入口 | `UpcomingScreen.kt:119-145` 挪到页头星标 + 计数角标 |
| C1 标题错位 | `WishlistPane.kt:91-96` 返回键单独一行 |
| C2 下拉刷新 | `UpcomingScreen.kt:188-192`、`HomeScreen.kt:330` |
| C4 偏移量 token | `Tokens.kt:39-48` `bottomBarClearance` / `fabOffset` / `snackbarClearance` / `fabSize` |
| D1 分组标题 | `Containers.kt:186-207` `onSurface` + SemiBold + `FinchBadge` 计数 |
| D2 删除入口 | `HomeCards.kt:341-359` 换成「更多」菜单（详情/删除），长按保留 |

---

## 1. 导航与信息架构图解

```
FinchAppScaffold                                  (FinchAppScaffold.kt:74-172)
├── ① 静态氛围背景层（玻璃采样源）
├── ② 内容层  AnimatedContent(targetState = tab)   ← 无 SaveableStateHolder（问题 P0-4）
│   ├─ [主页] HomeScreen            页头「游戏库」   任务：我有什么 / 这月玩了多少
│   │   └─ 游戏详情（内嵌 AnimatedContent + BackHandler）
│   │       └─ 白金庆祝 overlay（挂在 scaffold 层，任何 tab 都能弹）
│   ├─ [日程] UpcomingScreen        页头「发售日程」 任务：接下来买什么 ← 实际承载 3 种意图
│   │   ├─ 数据源胶囊  PSN港服(默认) / 游民星空 / eShop  ← 三者信息结构完全不同
│   │   ├─ 搜索（页头放大镜展开，只在当前源内搜）
│   │   ├─ 分组  本年度待发售 → 正在打折 → 更远期 → 本年度已发售
│   │   ├─ PlatformPickerSheet（条目无平台时）
│   │   └─ 愿望单 WishlistPane（页头★进入，BackHandler 返回）
│   │       └─ 分段 全部 / 打折中 / 未打折 → WishSummary → WishCard
│   ├─ [记录] HistoryScreen         页头「记录」     任务：这周玩了什么 / 补录
│   ├─ [总结] StatsScreen           页头「总结」     任务：我的节奏
│   │   └─ YearReportScreen（整页纵向叙事，独立返回）
│   └─ [设置] ImportScreen          页头「设置」     任务：外观 / 同步 / 备份
└── ③ 悬浮 Liquid 玻璃底栏（5 tab，64dp）
```

**每屏主任务一句话**

| 页面 | 用户来这一屏要完成的一件事 | 当前是否顺畅 |
|---|---|---|
| 主页 | 「我现在开哪个游戏玩」→ 找到并点开 | 顺畅（3.5 张卡可见） |
| 日程 | 「接下来有什么要出 / 有什么值得现在买」 | **不顺畅**：两种任务被压在一屏，折扣条目数量压倒时间轴 |
| 愿望单（子视图） | 「我关注的那几款现在什么价」 | 可读，但**只能主动来看**，缺变化量与通知 |
| 记录 | 「昨天那局多久 / 补录一条」 | 顺畅（9+ 条可见），但 300 条里无检索手段 |
| 总结 | 「这个月状态如何」 | 顺畅 |
| 设置 | 「改个外观 / 备份一次」 | 顺畅 |

**三个意图挤在「日程」里的具体结构冲突**

```
「日程」tab
  ├─ 意图 A：发售日历（期待值 / 日期 / 平台）  ← 游民星空、eShop 的主要价值
  ├─ 意图 B：当前折扣（价格 / -xx% / 截止日）  ← 只有 PSN 港服有
  └─ 意图 C：我的关注与价格追踪               ← 只有 PSN 港服有，藏在页头★后面
       ↑ B 与 C 是「购买决策」，A 是「计划」；而 tab 名「日程」只覆盖 A
       ↑ 切到「游民星空」时 B、C 的相关字段全部消失，页面不解释原因
```

---

## 2. 首屏预算测算表

**测算前提**（写死在表里，便于复核）：1080×2400px @2.625 → 屏幕高 914dp；状态栏 28dp；手势导航 24dp；悬浮底栏 64dp + 上下 10dp = 84dp，含导航栏共占 108dp。
→ **可用内容视口 H = 914 − 28 − 108 = 778dp**
尺寸来源：`Type.kt` 行高 + `Tokens.kt` 间距 + 代码里写死的 `.height()`。

### 2.1 日程页（`UpcomingScreen.kt`）

| 段 | 来源 | dp |
|---|---|---|
| `Spacer(sm)` | :114 | 8 |
| `FinchHeader`（标题 27/33 + 2 + 副标题 12/16） | :115-157 + `Type.kt:46-52,109-115` | 51 |
| `Spacer(md)` | :158 | 12 |
| 数据源胶囊行（labelLarge 20 + 12×2） | :159-172 + `Controls.kt:312/321` | 44 |
| `Spacer(sm)` | :173 | 8 |
| 搜索框（收起 = 0 / 展开 ≈ 56） | :175-183 | **0**（展开 56） |
| `Spacer(md)` | :184 | 12 |
| `LazyColumn` top padding = `xs` | :238 | 4 |
| 首个分组标题（8 + 20 + 2） | `Containers.kt:194-203` | 30 |
| item 间距 `md` | :236 | 12 |
| **固定区合计（首张卡上沿之前）** | | **181**（搜索展开 237） |
| 卡片高度（封面上限 82 + 上下 8×2，内容更高时随内容） | :355-409 | ≈ 120 |
| 卡片 + 间距 | | ≈ 132 |

→ **首屏可见卡片 = (778 − 181) ÷ 132 ≈ 4.5 张**（搜索展开时 ≈ 4.1 张）

### 2.2 主页（`HomeScreen.kt`）

| 段 | 来源 | dp |
|---|---|---|
| `contentPadding.top = sm` | :359 | 8 |
| `FinchHeader`（同日程页） | :364-369 | 51 |
| 间距 `md` | :362 | 12 |
| `MonthHeroCard`（`196.dp * fontScale.coerceIn(1,1.45)`） | `HomeCards.kt:80` | **196** |
| 间距 `md` | :362 | 12 |
| 「我的游戏 · N」+ 搜索/排序图标行（titleSmall 20，但图标按钮强制 48dp 命中区） | :377-413 + `Controls.kt:244` | 48 |
| 行内 `spacedBy(sm)` | :374 | 8 |
| 平台分段行（`FinchSegmented` 46 vs 图标 48 → 取 48） | :437-482 | 48 |
| 间距 `md` | :362 | 12 |
| **固定区合计** | | **395** |
| 游戏横卡（封面 72 + padding 12×2） | `HomeCards.kt:236-241` | ≈ 96 |

→ **首屏可见卡片 = (778 − 395) ÷ 108 ≈ 3.5 张**

### 2.3 愿望单（`WishlistPane.kt`）

| 段 | 来源 | dp |
|---|---|---|
| `Spacer(sm)` | :87 | 8 |
| 返回按钮（视觉 40，含命中区 48） | :91-96 | 48 |
| `FinchHeader` 标题 33（无副标题） | :97-107 | 33 |
| `Spacer(md)` | :108 | 12 |
| `FinchSegmented` 46 | :109-115 | 46 |
| `Spacer(md)` | :116 | 12 |
| `LazyColumn` top `xs` | :149 | 4 |
| `WishSummary`（12 上/下 padding + 15 + 2 + 22 + 8 + 15） | :187-213 | **98** |
| 间距 `md` | :147 | 12 |
| **固定区合计（首张 WishCard 之前）** | | **273** |
| WishCard | 复用 `StoreEntryCard` | ≈ 120 |

→ **首屏可见卡片 = (778 − 273) ÷ 132 ≈ 3.8 张**；`WishSummary` 独吞 98dp（首屏 12.6%）

### 2.4 总结页（`StatsScreen.kt`）

| 段 | 来源 | dp |
|---|---|---|
| top `sm` + Header 51 + `md` | :181-190 | 71 |
| 期数控制卡（segmented 46 + 8 + 翻页行 48 + 卡内 12×2） | :213-268 | 126 |
| 间距 `md` | :187 | 12 |
| `HeroTotalCard` | `StatsCards.kt` 定高 | ≈ 188 |
| **固定区合计** | | **397** |

→ 首屏仅剩 381dp 给 `MonthReportCard` 一段，符合「统计页首屏看大数字」的预期，**无问题**。

### 2.5 结论

| 页面 | 顶部固定区 | 首屏可见内容 | 结论 |
|---|---|---|---|
| 主页 | 395dp（其中 hero 196dp = 50%） | 3.5 张卡 | 达标。剩余浪费集中在 `MonthHeroCard` 196dp |
| 日程 | 181dp（搜索展开 237） | 4.5 张卡 | 达标。**密度问题已解决，问题转移到「一屏里塞了三种意图」** |
| 愿望单 | 273dp（`WishSummary` 98dp） | 3.8 张卡 | 偏紧。关注 <3 款时汇总卡是纯浪费 |
| 总结 | 397dp | 大数字 hero | 达标 |
| 记录 | 109dp | 9+ 条 | 达标（最密的一屏，合理） |

> 注意：360dp 窄屏上日程页副标题「PSN 港服价格 / 游民星空 / eShop 三源聚合」在剩余约 180dp 宽里会折成 2 行，`FinchHeader` 从 51dp 涨到 67dp，固定区实际 197dp（见 P1-16）。

---

## 3. 问题清单

严重度定义：**P0** = 影响核心任务完成或导致错误判断；**P1** = 明显摩擦/误导，多数用户会遇到；**P2** = 打磨。

### P0

| # | 标题 | 证据 | 用户会怎么踩到 | 具体改法 |
|---|---|---|---|---|
| **P0-1** | 「加愿望单 → 收到降价」闭环断在最后一步：系统只提醒发售，从不提醒降价 | `notify/ReleaseCheckWorker.kt:36-55`（唯一判据是 `days in 0..f.notifyDays`，与价格无关）；`UpcomingViewModel.kt:264-290`（`syncFollowPrices` 只覆盖 `priceText/discountText`，不比较、不通知） | 用户点了☆等降价，等一周没消息，以为 App 不会提醒，只好每天手动打开刷——**愿望单退化成书签** | ①`ReleaseFollow` 增 `prevPriceText` / `prevDiscountText` / `lastNotifiedAt`；②`syncFollowPrices` 里 `if (f.discountText != e.discountText \|\| f.priceText != e.priceText)` 记一条待发；③新增通知 channel `wishlist_price`（`IMPORTANCE_DEFAULT`），文案「「艾尔登法环」降到 HK$298（-70%），优惠至 11月20日」；④同一 key 24h 内最多 1 条 |
| **P0-2** |「源返回空」与「网络失败」共用一条失败分支，且切源会顶掉已有列表 | `UpcomingViewModel.kt:190-193`（`catch { … emptyList() }`，异常被吞成空列表）；`:198-209`（`r.isEmpty()` → `LoadState.Failed`，覆盖掉原本 `Ready` 的内容）；`UpcomingScreen.kt:199-208`（Failed 整块顶掉列表） | 切到「游民星空」赶上该源静默返回 0 条 → 整页变成「游民星空 没有返回数据（网络或源问题）」，上一屏的港服内容消失，用户分不清「没数据」还是「没网」，只能点重试反复失败 | 分流：`catch` → `Failed(reason)`（保留旧列表 + 顶部错误条）；`r.isEmpty()` → `Ready(emptyList(), emptyList())`，由 `UpcomingScreen` 显示**源专属空态**（「游民星空 今天没抓到大作，去 PSN 港服看价格」+ 一键切源 `FinchChip`）；`Ready` 分支永不破坏上一份 `loaded` |
| **P0-3** | 缓存回退没有任何「数据时间」标注，旧折扣被当成当前价展示 | `UpcomingViewModel.kt:243-258`（PSN 失败 → 静默 `fromPsn(cached)`，只有一行 `Log.w`）；`UpcomingScreen.kt:117`（副标题恒为「PSN 港服价格 / 游民星空 / eShop 三源聚合」，与新鲜度无关）；`WishlistPane.kt:206-210`（只说「离线也能看上次同步到的价」，不说上次是何时） | 地铁里打开 App，看到 `-70%` + 「优惠至 11月20日（还剩 3 天）」，实际是 3 周前的快照 → **据此下单，付了原价**。这是价格追踪类应用的信任底线 | ①`cacheSp` 写缓存时同存 `psn_cached_at`；②`fetchPsnEntries` 返回 `(entries, cachedAt: Long?)`；③`LoadState.Ready` 增 `staleAt: Long?`；④stale 时页头副标题换成「离线 · 数据为 11月2日 21:30 的快照」并加 `FinchBadge(tint = scheme.tertiary)`；⑤`WishSummary` 说明句改为「价格更新于 11月2日 21:30」 |
| **P0-4** | 切 tab 销毁整页状态：滚动位置、筛选、搜索、愿望单开关全部归零 | `FinchAppScaffold.kt:115-144`（`AnimatedContent(targetState = tab)` 里直接 `when(t)`，**无 `SaveableStateHolder`**，全仓库 `grep SaveableStateHolder` = 0 命中）；`HomeScreen.kt:165`（`rememberLazyListState()` 在 `HomeScreen` 内，随组合销毁） | 主页滚到第 12 款 → 去「记录」看一眼 → 回主页，**又回到顶部第 1 款**；设好的「Switch + 仅收藏」筛选、搜索词、排序方式一并丢失；在愿望单里切走再回来退回日程列表 | `val holder = rememberSaveableStateHolder()`，`AnimatedContent` 内包 `holder.SaveableStateProvider(t.name) { … }`；`UpcomingScreen.kt:101/106` 的 `query`、`searchOpen` 已是 `rememberSaveable` 会自动生效，`listState` 从 `remember` 改 `rememberSaveable`；`StatsScreen.kt:105-110` 的 `mode/anchorMonth` 同步改 `rememberSaveable` |

### P1

| # | 标题 | 证据 | 用户会怎么踩到 | 具体改法 |
|---|---|---|---|---|
| **P1-1** | 日程页三种意图挤一页，「正在打折」把时间轴淹没；tab 名「日程」覆盖不了另外两种 | `UpcomingScreen.kt:263-268`（分组顺序 coming→deal→later→recent）；`UpcomingViewModel.kt:431-447`（PSN 同时产出 `preorders` 与 `deals`，`deals` 条目数远多于预购）；`FinchAppScaffold.kt:61`（tab 名「日程」，CalendarMonth 图标） | 点「日程」想看「下个月有什么要出」，结果往下滑 3 屏全是 `-70%` / `-85%` 的打折券；想找刚才看到的那款折扣得先滚回去；而「愿望单」要绕到页头点一个星标 | ①页头下方加 `FinchSegmented`：**时间轴 / 折扣 / 愿望单** 三段横向同级（`WishFilter` 那套已有，扩成页面级）；②「时间轴」按月份 `stickyHeader`，`deal` 条目不再混入；③`Tab.Upcoming` 标签与图标改「发现」+ `Icons.Outlined.Explore`，图标别再用日历（日历强化了错误预期） |
| **P1-2** | 加入愿望单的唯一入口是一颗无文字标签的 ☆，夹在价格和「入库」之间 | `UpcomingScreen.kt:429-439`（`FinchIconButton(StarBorder, ghost = true)`，右列宽仅 92~108dp）；`WishlistPane.kt:127`（空态却要求用户「在日程页点卡片右边的 ☆ 关注」） | 用户看到 ★/☆ 会先猜「收藏」或「评分」，不敢点；点了变成实心但没有一句话告诉他已经加入愿望单；愿望单空态里的 ☆ 说明是**唯一**的引导文案，而它在另一个页面 | ①未关注时把裸图标换成带文字的小胶囊「☆ 想买」（`FinchButton(compact, Glass)`，宽 52dp 够放两字）；②关注成功立刻 `Snackbar("已加入愿望单，降价会提醒你 · 撤销")`；③卡片点按打开 `FinchSheet` 动作面板（入库 / 关注 / 去商店），星标只作状态指示——与 `GameRowCard` 的「更多」菜单保持同一种心智 |
| **P1-3** | 主题色实心底 + 白字对比度 3.7:1，选中态在强光下看不清 | `theme/AppThemes.kt:124-125`（`primary = #3D7FFF`、`onPrimary = White`，相对亮度 0.234 → **3.7:1**）；用在 `Controls.kt:311-312`（选中 `FinchChip` 底+字）、`Controls.kt:355-378`（`FinchSegmented` 选中块）、`FinchAppScaffold.kt:61-63`（底栏选中 tint）. 选中文字为 labelLarge **14sp SemiBold ≠ 大字**，AA 要求 4.5:1 | 户外/强光下看不清当前选了哪个数据源、哪个平台、哪个分段；色弱用户对蓝底白字的辨识更差 | ①`AppThemes.kt` 增 `val onPrimaryStrong: Color get() = if (isDark) scheme.onPrimary else lerp(scheme.primary, Color.Black, 0.22f)`（≈`#2F63C7` → 5.1:1）；②选中胶囊/分段底改 `Brush.linearGradient(primary, onPrimaryStrong)`，文字保持 `onPrimary`；③**只改 token 一处**，三处同时生效，不碰玻璃配方 |
| **P1-4** | fontScale 2.0 时两处定高把文字裁掉 | `Controls.kt:349`（`FinchSegmented` 内 `.height(46.dp)`，装 labelLarge 20sp → 2.0x 后行高 40sp + 上下 4dp = 48dp **溢出**）；`Controls.kt:191`（`FinchButton` `height = if (compact) 36.dp else 48.dp`，compact 装 20sp→40sp **裁一半**） | 系统字号调到「特大」后，主页平台分段「全平台/PC/Switch/PS」的字被上下切掉；日程卡片里「入库」「商店」按钮文字只剩上半截 | ①`.height(46.dp)` → `.heightIn(min = 46.dp)`，内层 `Box` 用 `padding(vertical = 4.dp)` 撑高；②`compact` 高度改 `heightIn(min = 36.dp)`；③在 `FinchSegmented` 里给 `Text` 加 `maxLines = 1` 即可（已有），但要靠容器放高而不是靠裁切 |
| **P1-5** | fontScale ≥1.3 时商店卡右列吃掉近一半宽度，标题被压成两行两字 | `UpcomingScreen.kt:414-416`（`rightScale` 上限 1.45 → 右列最宽 **157dp**）+ `:360`（封面 62）+ `:377/410`（间距 12+4）→ 360dp 屏扣掉屏边距 36 后，中间列只剩 **≈ 89dp** | 大字号用户看到的卡片是「封面 62 / 标题 2 行 2 字 / 右侧价格+星+入库」，标题基本读不出是什么游戏 | 在 `StoreEntryCard` 外套 `BoxWithConstraints`，`maxWidth < 380.dp \|\| fontScale > 1.3f` 时切竖版：第一行 封面(62×82) + 标题/元信息（`weight(1f)`，可 3 行）；第二行 价格 + ☆ + 入库 右对齐横排。卡片高度仍由内容决定（现有做法保持） |
| **P1-6** | 玻璃底 / flat 的图标按钮在 TalkBack 下不是「按钮」 | `Controls.kt:249-272`：`flat` 走 `InsetSurface(onClick)`、默认走 `GlassSurface(onClick)`，两者最终都是 `finchClickable(onClick)`（`Tokens.kt:84-127` 未传 `role`）；只有 `ghost` 分支走 Material `IconButton` 才有 `Role.Button` | 读屏用户听到「刷新日程 按钮？」，不知道能否点击、也不知道是按钮还是纯文本；与 ghost 分支的朗读不一致 | `finchClickable` 已支持 `role` 参数——给 `InsetSurface` / `GlassSurface` 的签名加 `role: Role? = null` 透传，`FinchIconButton` 传 `role = Role.Button` + `onClickLabel = contentDescription` |
| **P1-7** | 底栏 tab 用 `clickable` 而非 `selectable`，读屏听不到「已选中 / 第 N 项共 5 项」 | `liquid/LiquidBottomTab.kt:32-37`（`clickable(role = Role.Tab, …)`，**无 `selected` 语义**）；`FinchBottomBar.kt:126`（玻璃关闭时走 `SolidBottomBar`，`finchClickable` **连 role 都没有**）；`LiquidBottomTabs.kt:191` 第二层已正确 `clearAndSetSemantics {}` | TalkBack 用户连点 5 次只听到「主页 / 日程 / 记录 / 总结 / 设置」，不知道自己现在在哪一页；也没有「第 2 项，共 5 项」的定位信息 | `LiquidBottomTab` 增加 `selected: Boolean` 参数 → `Modifier.selectable(selected, role = Role.Tab, onClick)`；外层 `Row` 加 `Modifier.selectableGroup()`；`SolidBottomBar` 同步改造 |
| **P1-8** | 愿望单「取消关注」单击即删，无确认、无撤销、无软删除 | `WishlistPane.kt:162`（`onUnfollow = { upcomingViewModel.toggleFollowByKey(key) }`）→ `UpcomingViewModel.kt:91-95`（`followDao.deleteByKey` 直接删行，含 `notifyDays` 与价格快照） | 想在 WishCard 上点「商店」，误触左边那颗星 → 这条关注连同价格快照一起消失，页头角标 -1，**没有任何垃圾桶或恢复入口**，用户只能凭记忆重新在日程列表里找到它再点一次 | ①两段式：先从列表移除 + `Snackbar("已取消关注「$name」· 撤销", SnackbarDuration.Short)`，超时才真删（需要共享 Snackbar host，见 P2-5）；②或最低成本：愿望单卡片的星改成「…」菜单 →「取消关注」+ `FinchConfirmDialog` |
| **P1-9** | 搜索范围与 placeholder 承诺不符：只在当前数据源里搜 | `UpcomingScreen.kt:178`（placeholder「搜索发售日程…」）、`:213`（`searchPool = s.allEntries`）；`UpcomingViewModel.kt:179-189`（`refresh()` 只拉 `_source.value` 一个源，`allEntries` 里永远只有该源的数据）、`:126`（注释写「全量（搜索用，不过滤）」——只是「该源全量」） | 在 eShop 源下搜「塞尔达」找不到（其实 PSN 港服有预购）；用户会得出「App 里没有这个游戏」的错误结论 | ①placeholder 改「在「${source.label}」中搜索」；②更好：VM 内加 `Map<Source, List<UpcomingEntry>>` 三源结果缓存（switch 回来不用重拉），搜索走三源并给结果加源徽章 `FinchBadge(entry.source)`，结果按源分组 |
| **P1-10** | 横屏 / 平板完全无布局应对；横屏下主页首屏看不到任何游戏 | 全仓库 `grep WindowWidthSizeClass` = **0 命中**（`BoxWithConstraints` 只出现在 `Controls.kt:341` / `LiquidSlider.kt:71` / `LiquidBottomTabs.kt:70` 控件内部）；`AndroidManifest.xml` 无 `screenOrientation` → 可旋转。横屏 1080×2400 → 高 411dp，内容视口 ≈ 411−28−108 = **275dp**，而主页固定区 **395dp**（§2.2） | 手机横过来看游戏库，屏幕上只有 `MonthHeroCard`，一张游戏卡都看不到，还以为列表空了；平板 / 折叠屏展开后 `GameRowCard` 的 96dp 封面横向拉满 700dp 留白 | ①`FinchAppScaffold` 外层 `BoxWithConstraints`，`maxWidth >= 600.dp` 时内容区加 `widthIn(max = 720.dp)` + `align(Center)`；②`maxHeight < 480.dp`（横屏）时 hero 降 120dp、日程页页头收成单行；③**临时兜底**：Manifest 加 `android:screenOrientation="portrait"`，等横屏布局做完再放开 |

### P2

| # | 标题 | 证据 | 用户会怎么踩到 | 具体改法 |
|---|---|---|---|---|
| **P2-1** | tab 名与页头名三处不一致：「主页」↔「游戏库」 | `FinchAppScaffold.kt:60`（tab「主页」）vs `HomeScreen.kt:366`（页头「游戏库」）vs `HomeScreen.kt:344-345`（空态「游戏库还是空的」+「点右下角「添加游戏」」）vs `UpcomingScreen.kt:255`（「数据来自 PSN 港服…」） | 用户对底栏说「主页」，进去看到「游戏库」，空态又说「游戏库」；出现「主页」的唯一位置是底栏 —— 心智没有锚点 | tab 保留「主页」（短、稳），页头改「我的游戏」，空态标题改「还没有游戏」、正文改「点右下角添加，或去「发现」从日程一键入库」；`Tab.Import` 的 `label` 与 `ImportScreen.kt:99` 的「设置」已一致，保持 |
| **P2-2** | 「设置」与「筛选」共用 `Tune` 图标 | `FinchAppScaffold.kt:64`（`Icons.Outlined.Tune` = 设置 tab）vs `HomeScreen.kt:458`（`Icons.Filled.Tune` = 筛选按钮，带角标） | 底栏那个「推子」图标看起来像筛选入口，尤其它和主页筛选按钮长得一样 | 设置 tab 换 `Icons.Outlined.Settings` / `Icons.Filled.Settings`；`Tune` 专供筛选 |
| **P2-3** | `WishSummary` 98dp 恒显示，与关注数无关；同时它随列表滚走 | `WishlistPane.kt:153-155`（`item(key = "summary")` 是列表第 0 项）、`:187-213`（三格 + 8dp + 说明句 + 12dp padding） | 只关注 2 款游戏时，首屏 1/8 花在「关注 2 / 打折中 0 / 最大折扣 —」上；想回看「最大折扣」时它已经滚走了 | ①`if (follows.size >= 3)` 才显示（<3 时无统计意义）；②≥3 时删掉说明句（挪到空态）；③或干脆并进页头副标题一行：`FinchHeader(subtitle = "关注 3 · 打折 1 · 最大 -70%")`，省掉整张卡 98dp |
| **P2-4** | 愿望单页没有下拉刷新，与主页/日程手势不一致 | `WishlistPane.kt:81-121`（只有右上角 `FinchIconButton(Refresh)`）；对比 `UpcomingScreen.kt:188-192`、`HomeScreen.kt:330` 都有 `PullToRefreshBox` | 用户在这个页下意识下拉，什么都不发生——上一轮只修了日程页，漏了这一页 | 包一层 `PullToRefreshBox(isRefreshing = refreshing, onRefresh = { upcomingViewModel.refreshWishlist() })`，右上角图标保留 |
| **P2-5** | 只有主页有 Snackbar host，其余 tab 的操作没有反馈位 | `HomeScreen.kt:255`（全项目唯一 `FinchSnackbarHost` 实例，且只挂在主页 `Box` 内） | 日程页「入库」后卡片变「已在库」但没有一句话说明「是否连计时记录一起建了」；愿望单取消关注零反馈；设置页备份/恢复的成功与否只能看界面变化 | 在 `FinchAppScaffold` 顶层建一个共享 `SnackbarHostState` + `FinchSnackbarHost(Modifier.align(BottomCenter).padding(bottom = FinchSpace.snackbarClearance))`，用 `CompositionLocal` 下发；各屏删掉自己那份 |
| **P2-6** | 加载态仍是整页转圈，刷新还会把内容往下挤 | `UpcomingScreen.kt:193-197`（`Loading` 顶掉整个列表区）；`WishlistPane.kt:118-120`（`refreshing` 时 `FinchLoading` 插在 `Column` 流里，下方列表被下推 38dp） | 日程页每次切数据源：空白 → 转圈 → 内容突然出现，三次布局跳变；愿望单点刷新时列表整体跳一下 | ①首载用 3 张 120dp 骨架（`GlassSurface(Tile)` + 灰块，关掉 `finchEnter`）；②刷新用顶部 2dp 线性进度条（`FinchProgressBar` 或 `LinearProgressIndicator`）绝对定位，不占布局高度 |
| **P2-7** | 记录页 300 条无检索、无按游戏聚合；删除无撤销 | `HistoryScreen.kt:113-135`（只有按天分组，`groups` 从 `sessions` 直接分组）；`:239-246`（删除按钮 → 弹窗）；`:269-280`（确认后 `viewModel.deleteSession` 立即落库）；`:99-100`（副标题自己写着「最近 300 条」） | 想在 300 条里找「上周三晚上玩的那款」只能一直滚；想看「这款游戏我都什么时候玩过」做不到；删错了无法恢复 | ①页头加 `FinchIconButton(Search)`，复用主页/日程那套「展开式搜索」，按 `game.name` 过滤；②分组切换 `FinchSegmented(按天 / 按游戏)`；③删除改「确认 + Snackbar 撤销 5 秒」 |
| **P2-8** | `FinchHeader` 副标题无行数上限，窄屏/大字号会折行顶高页面 | `Containers.kt:165-173`（`Text(subtitle)` 无 `maxLines`）；触发点 `UpcomingScreen.kt:117`（26 字符）、`StatsScreen.kt:192` | 360dp 窄屏 + 3 个图标按钮（144dp）后副标题只剩约 180dp → 折成 2 行，固定区 181dp 变 197dp；fontScale 2.0 时能折 4 行，把首张卡挤出屏幕 | ①`FinchHeader` 的 `subtitle` 加 `maxLines = 1, overflow = TextOverflow.Ellipsis`；②文案缩短：「PSN 港服价格 / 游民星空 / eShop 三源聚合」→「港服 · 游民 · eShop」 |
| **P2-9** | 日程页无「已发售」折叠，历史条目和新内容抢同一列 | `UpcomingScreen.kt:267`（`"recent" to "本年度已发售"` 作为普通分组，`items` 全量渲染） | 想看「今年已经出了什么、我漏了什么」时它是最后一个分组，要滑过全部折扣条目才能到；但一旦按 P1-1 拆页，它又应该有独立入口 | 分组改 `FinchCollapsibleGroup`（`Containers.kt:624-718` 已有，设置页在用）默认收起，标题带计数「本年度已发售 · 27 款」 |
| **P2-10** | 底栏标签在 fontScale 2.0 下吃掉全部纵向空间 | `FinchBottomBar.kt:106`（`SolidBottomBar` 64dp）、`LiquidBottomTabs.kt:177`（`.height(64f.dp)` → 内层 56dp）；`TabLabel` 用 `labelSmall` 11sp → 2.0x 行高 30dp；图标 24 + 间距 2 + 30 = **56dp** 恰好等于内高 | 大字号下 5 个 tab 的图标被压扁、标签贴边 | `TabLabel` 保持 `maxLines = 1`，但给底栏加 `heightIn(min = 64.dp)` 并让 `LiquidBottomTabs` 的高度从调用方传入（`bottomBarHeight: Dp`），`Tokens.kt` 里 `bottomBarClearance` 同步派生成 `bottomBarHeight + 24.dp + navbar` |

**合计 20 条：P0 × 4、P1 × 10、P2 × 10（其中 6 条直接针对日程页 / 愿望单的信息架构与状态：P0-1/2/3、P1-1/2/9、P2-3/4/9）**

---

## 4. 改进后的任务流建议

### 路径 A：「看到折扣 → 判断值不值 → 加愿望单 → 收到降价」（当前断在最后一环）

| # | 画面 | 现状 | 改成 |
|---|---|---|---|
| A1 | 「发现」tab → 分段「折扣」 | 混在「日程」里，往下第 3 屏才看到，且排序按分组而非折扣力度 | 独立分段，按 `discountPercent` 降序（VM 已有此排序，`UpcomingViewModel.kt:334`）；卡片角标显示 `-70%` |
| A2 | 折扣卡 | 封面上限 82 + 标题 + 徽章 + 现价 22sp + 划线原价 + 「优惠至 11月20日（还剩 3 天）」 | 保持——这一版信息层级是对的。补一处：`saleEndText` 剩 ≤2 天时徽章换 `scheme.error` |
| A3 | 「判断值不值」 | **缺口**：用户只能看到当前价，看不到历史最低 | 卡片点按 → `FinchSheet`：历史最低价 / 上次打折价 / 上次折扣幅度（先在 `ReleaseFollow` 存一份 `minPriceSeen`，增量成本很低） |
| A4 | 加愿望单 | 点一颗无标签 ☆，无提示 | `FinchButton(compact, Glass)` 文字「☆ 想买」→ 变「★ 已关注」+ 页头角标 +1 + `Snackbar("已加入愿望单，降价会提醒你 · 撤销")` |
| A5 | 收到降价 | **不存在** | `ReleaseCheckWorker` 比对价格 → 通知「「X」降到 HK$298（-70%），优惠至 11月20日」→ 深链到「发现 → 愿望单」，目标卡描边高亮 + 顶部 `FinchBadge("↓ 降了 HK$170")` |

### 路径 B：「想知道下个月有什么要出 → 决定买不买 → 入库」

| # | 画面 | 改成 |
|---|---|---|
| B1 | 「发现」→ 分段「时间轴」 | 按月份 `stickyHeader`（「11月」「12月」），最近一条置顶并高亮 |
| B2 | 条目卡 | `FinchBadge(releaseCountdownText)` 已有「3天后 / 明天 / 今天发售」；有价格的（PSN 预购）显示现价，没有的显「价格待同步」 |
| B3 | 入库 | 无平台时弹 `PlatformPickerSheet`（已有）；入库后**给一句 Snackbar**「已加入游戏库 · 查看」，而不是只把按钮换成「已在库」静默变化 |
| B4 | 「已发售」 | `FinchCollapsibleGroup` 折叠，标题「本年度已发售 · 27 款」 |

### 路径 C：「翻这周的记录 / 补录一条」

| # | 画面 | 改成 |
|---|---|---|
| C1 | 「记录」页头 | 加搜索图标，展开式（与主页/日程同一套交互，三处一致） |
| C2 | 分组 | `FinchSegmented(按天 / 按游戏)`；按游戏时每条折叠该游戏的本周会话 |
| C3 | 编辑 / 删除 | 编辑保留 `SessionEditDialog`；删除改「确认 + Snackbar 撤销 5 秒」 |

---

## 5. 验收清单（交付前自查）

### 5.1 信息架构
- [ ] 「日程」页首屏不再出现 `deal` 条目（或 tab 已改名「发现」且三分段可横向切换）
- [ ] 从「发现」到「愿望单」≤ 1 次点击，且返回键回到进入前的分段位置（不是回到日程首屏）
- [ ] 加入愿望单有带文字的入口 + 成功反馈 + 撤销；取消关注可撤销
- [ ] 底栏 5 个标签、页头标题、空态文案三处措辞一致（不存在「主页 vs 游戏库」）
- [ ] 同一屏不出现两个含义相同但外观不同的图标（Tune 只代表筛选）

### 5.2 状态覆盖（每屏逐条过）
- [ ] 首载 = 骨架，不是整页转圈
- [ ] 刷新 = 顶部进度条，列表留在原地不跳
- [ ] 空结果 ≠ 失败：空态给出「为什么空 + 下一步做什么」，且提供切源/清筛选按钮
- [ ] 失败态保留上一次成功的内容 + 顶部错误条，而不是整页替换
- [ ] **离线/缓存回退必须显示数据时间**（「数据为 11月2日 21:30 的快照」），价格页尤其
- [ ] 搜索无结果：文案包含实际搜索词，并说明搜索范围（当前源）
- [ ] PSN 区域限制 / 无网络 / DNS / SSL 四种错误各有可读中文（`UpcomingViewModel.kt:158-165` 已有，确认在 UI 上露出）

### 5.3 交互与反馈
- [ ] 所有可点元素命中区 ≥ 48dp（`FinchIconButton` / `FinchStars` / 底栏 tab 已达标；改完 `FinchSegmented`/`FinchButton compact` 后复测）
- [ ] 破坏性操作：确认弹窗 + 可撤销（撤销优先于确认，二选一即可，但不能都没有）
- [ ] 每个 tab 都能拿到 Snackbar（共享 host）
- [ ] 切 tab 再返回：滚动位置、筛选条件、搜索词、展开的子视图全部保留
- [ ] 返回键：详情页 → 列表；愿望单 → 日程（分段位置不变）；非主页 tab → 主页；连续两次退出 App
- [ ] 下拉刷新在主页 / 日程 / 愿望单 / 记录 四屏行为一致（或明确都不支持）

### 5.4 无障碍
- [ ] **对比度**：选中态实心底 + 文字 ≥ 4.5:1（当前 `#3D7FFF` + 白 = 3.7:1，待修）
- [ ] **对比度**：11sp `labelSmall` 在玻璃上的 `onSurfaceVariant` ≥ 4.5:1（`#4F5866` on `#F5F7FB` ≈ 6.7:1 达标；需在真实玻璃叠加后复测）
- [ ] **角色**：所有图标按钮朗读含「按钮」；5 个 tab 朗读含「已选中 / 第 N 项，共 5 项」
- [ ] **朗读顺序**：主页卡依次为「游戏名 → 平台·状态 → 上次玩/累计 → 评分 → 更多操作」；`contentDescription = null` 的装饰图标不打断
- [ ] **评分**：`FinchStars` 每颗星有「评分 N 星，已选/未选」（`strings.xml:34-35` 已有）
- [ ] **封面**：列表缩略图 `contentDescription = null`（正确，标题已在旁边）；详情页大封面用「%s 封面」（`SharedUi.kt:40-41` 已约定）
- [ ] **fontScale 1.3**：无截断、无重叠
- [ ] **fontScale 2.0**：`FinchSegmented` / `FinchButton(compact)` / `StoreEntryCard` / 底栏标签 四处逐一看（前三处当前会失败）
- [ ] **降低动画 / 播放时长 0**：`AnimatedContent` 转场与 `finchEnter` 在系统「移除动画」下退化合理
- [ ] **动态颜色**：确认「不支持」是产品决策（`theme/` 内 `dynamicColorScheme` 0 命中）——玻璃体系需要稳定底色，可以不支持，但设置页要说明

### 5.5 响应式
- [ ] 360dp 窄屏：日程页副标题单行不折；商店卡标题至少 8 个汉字宽
- [ ] 411dp 横屏：主页首屏至少 1 张游戏卡可见（当前 0 张）
- [ ] 600dp+ 平板 / 折叠展开：列表内容区有最大宽度约束（建议 720dp 居中），卡片不被拉伸
- [ ] 分屏（高度 300dp 左右）：底栏不遮挡最后一张卡（`bottomBarClearance` 是否仍够）
- [ ] 大字号 + 窄屏 + 横屏 三条件叠加：不出现 0 内容可见

---

## 6. 落地顺序建议

**第一批（无新组件、纯解阻塞，约 1 天）**
P0-4 `SaveableStateHolder` · P0-3 缓存时间戳 + 副标题 · P0-2 空/错分流 · P1-3 对比度 token · P1-7 底栏 `selectable` · P1-6 `role` 透传

**第二批（改一屏的结构，需要设计确认）**
P1-1 日程页三分段 + tab 改名 · P1-2 愿望单入口文字化 · P2-3 `WishSummary` 条件显示 · P2-4 愿望单下拉刷新 · P1-4/P1-5 大字号布局

**第三批（跨文件、需要数据层配合）**
P0-1 降价通知（`ReleaseFollow` 加字段 + 新 channel）· P2-5 共享 Snackbar · P1-10 宽度分级 + 横屏 · P2-6 骨架屏 · P2-7 记录页检索

---

## 附：本文未覆盖 / 需真机验证的项

1. **真实玻璃底色上的对比度**：`GlassSurface` 会采样氛围光（`FinchBackdrop`），`surface.copy(alpha=0.5)` 叠加在极光渐变上时，`onSurfaceVariant` 的实际对比度可能低于静态计算值。需在浅色主题 + 亮色背景图下用取色器实测。
2. **首屏卡片数**：§2 是按 1080×2400@2.625 推算；若目标机型是 1080×2340@2.75（多数国产中端），可用高度少约 20dp，日程页为 4.4 张、主页为 3.4 张，结论不变。
3. **TalkBack 实际朗读串**：本报告依据 Compose semantics 静态推断（哪些节点 merge、有无 role），最终以真机 TalkBack + `Layout Inspector` 的 semantics 树为准。
4. **`AnimatedContent` 是否真的丢状态**：结论基于「全仓库无 `SaveableStateHolder`」+ `AnimatedContent` 会 dispose 退场内容，属强推断；建议先在主页做一个 3 行验证（滚动 → 切 tab → 返回，看是否回到顶部），确认后再改。
