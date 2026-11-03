# Finch 路线图

基线：`v0.22.0`（DB v21，versionCode 91）。
IGDB 已整体移除（国内直连不通）；HLTB 改走 Crashdummy 中转 API（Steam 直查+Nlib 英文名+按名搜，默认开、可一键关）；非英文库名走英文名解析链换英文线索再搜（Steam 中文反查/Bangumi 原名/eShop 英文段/游民星空/机翻已移除，v0.15.15–0.15.18）。
下阶段主题：**清理版（重构 / 性能）**。v0.18–v0.22 连续五版都在动 UI，按规划原则第 3 条该跟一个清理版；之后回补原定的 v0.20「年度报告 / backlog 漏斗」。唯一悬空的旧承诺（战报图片海报 v1）已在 v0.22 兑现。

## 规划原则

1. **本地优先**：所有新功能零服务器，推送/备份/统计全走本地 + WorkManager。
2. **一次只升一个 DB 版本**：每个功能版最多一次 `MIGRATION_X_Y`，`app/schemas/` 必须入库，备份校验共用 `FINCH_DB_VERSION`。
3. **功能版与重构版交替**：参考 0.12.1/0.12.2 节奏，大 UI 改动后跟一个清理版。
4. **每个版本可独立发版**：按 `versionName` 打 tag 即能发 Release，不互相卡脖子。

## 总览

| 版本 | 主题 | 核心内容 | DB | 依赖新增 |
|------|------|----------|----|----------|
| v0.13 ✅已交付 | 库管与计时可信 | 游戏状态＋主页筛选；计时暂停/继续；会话可编辑 | 8→9 | 无 |
| v0.14 ✅已交付 | 总结可视化 | 热力图；作息（周几/几点）；战报分享 v1（回本率已移除） | 9→10→11 | 无 |
| v0.15 ✅已交付 | 日程变有用 | 发售关注＋推送；通关进度条（手动）；WorkManager 基建 | 11→12 | `work-runtime-ktx` |
| v0.15.1–0.15.3 ❌已移除 | 资料库换源 | TGDB→IGDB、HLTB 自动获取（国内直连不通，整体回滚） | 12→13 | 无 |
| v0.15.4 ✅已交付 | 移除 | IGDB/HLTB 代码与 DB 列全删，搜索链退回 Bangumi→Steam | 13→14 | 无 |
| v0.15.5 ✅已交付 | HLTB 写回 | 详情直抓+Bangumi联动（免配置）；点开自动获取三围 | 14→15 | 无 |
| v0.15.6 ✅已交付 | HLTB 中转 | 改走 Crashdummy 中转 API（Steam 直查+按名搜+开关，默认开） | 15（不变） | 无 |
| v0.15.7 ✅已交付 | 体验修复 | Switch 按名搜多关键词轮询；通关勾选联动进度封顶 | 15（不变） | 无 |
| v0.15.8 ✅已交付 | 体验修复 | Switch 搜不到根因修：eShop 英文名联动（中文库名→英文段→中转） | 15（不变） | 无 |
| v0.15.9 ✅已交付 | 体验修复 | Switch 真根因修：Moon 语言头发 en-GB，库名回英文（HLTB 按名搜才可能命中） | 15（不变） | 无 |
| v0.15.10 ✅已交付 | 体验修复 | 中文库名跳过直搜（必 404），失败文案列实际关键词 | 15（不变） | 无 |
| v0.15.11 ✅已交付 | 体验修复 | eShop 只取拉丁英文段（日文词不再污染查询）；删 Bangumi 兜底；无英文词直说文案 | 15（不变） | 无 |
| v0.15.12 ✅已交付 | 体验修复 | Switch TitleID→英文名联动（Nlib 免配置，命中写回库名） | 15（不变） | 无 |
| v0.16 ✅已交付 | 数据安全收尾 | 自动备份＋恢复入口；点按钮检查更新（不轮询）；CSV 不做（zip 导出已覆盖） | 16（不变） | 无（复用 WorkManager） |
| v0.17 ✅已交付 | 奖杯与小组件 | PS 奖杯同步（详情页奖杯卡＋总结页汇总）；小组件扩展 | 16→17 | 无 |
| v0.18 ✅已交付 | 界面重构 | Liquid Glass 设计系统＋统一玻璃层级＋逐屏重做；玻璃性能修复；构建提速 | 17（不变） | 无 |
| v0.18.1 ✅热修 | 奖杯同步修正 | 实测 trophyTitles 无 npTitleId → 改 `/titles/trophyTitles` 按 psnTitleId 批量映射（每批≤5） | 17（不变） | 无 |
| v0.18.2 ✅热修 | 总结页瘦身 | 移除总结页 PSN 奖杯汇总卡（详情页奖杯卡保留） | 17（不变） | 无 |
| v0.18.3 ✅ | 时长口径统一 | PS 游戏单款累计只认云官方值（PSN 优先，0 回退本地会话）；主页卡片/排序/HLTB「已玩」统一口径；同步按差额补历史会话，让游玩记录合计＝累计 | 17（不变） | 无 |
| v0.18.4 ✅热修 | 通关判定修正 | 通关只认手动勾选，时长达标仅显示 100%＋一行提示（不再自动标「已通关」） | 17（不变） | 无 |
| v0.18.5 ✅热修 | 记录补齐 | 新建游戏建档即补历史会话；新增本地对账（不联网、启动时跑），云同步失败也不空记录 | 17（不变） | 无 |
| v0.18.6 ✅热修 | 本月时长修正 | 推算的历史会话加 `estimated` 标记（DB 18），本月/今年/热力图/作息/平台/Top 等区间统计一律排除；锚点改到上个月末之前、上限 400 天；旧云端来源会话迁移回标 | 17→18 | 无 |
| v0.18.7 ✅热修 | 底栏透明玻璃 | 内容层单独挂 `contentBackdrop`，底栏采样源改为「氛围光 ＋ 内容层」合成，真正透出滚动内容；遮罩 alpha 0.34/0.46 → 0.18/0.26 | 不变 | 无 |
| v0.18.8 ✅热修 | 记录只存真实 | 删掉「按官方累计差额摊历史会话」的对账逻辑与推算行，`play_sessions.estimated` 列移除（DB 19）；同步只写真实新增，首次同步仅落基线；详情页显示「本地记录 X · 差额 Y」；底栏去 vibrancy＋blur 8→3dp | 18→19 | 无 |
| v0.19 ✅已交付 | 记录更细 | 会话标签（主线/支线/联机/多周目/自定义）＋记录页胶囊筛选＋总结页标签分布；时长目标（月/年，设置页可填，Hero 卡出进度）；海报拆到 v0.21 | 19→20 | 无 |
| v0.19.1 ✅热修 | 同步写不进记录 | `diffWriteSessions` 撞旧占位就延长而非丢弃整天增量；基线只推进到「已落账」量，余量下次补；每条记录可写备注（`play_sessions.note TEXT`，DB 21） | 20→21 | 无 |
| v0.20.0 ✅已交付 | 玻璃化 UI | 设置页加「实验性功能」开关（`experimentalGlassEnabled`，默认关）；开关关闭时全组件走实色降级面板、打开才走折射玻璃；设置页重排为 6 个可展开折叠分组；卡片进场动画（淡入＋上浮＋缩放，按序错开） | 21（不变） | 无 |
| v0.20.1 ✅热修 | 玻璃覆盖全组件 | 卡片导出自己的玻璃层（`exportedBackdrop`）供卡内控件采样，原先前者只采根部氛围光、只剩磨砂感；`InsetSurface`/输入框一并玻璃化；进场动画改回 `remember` 记账，滚动新出现的卡片也会播 | 21（不变） | 无 |
| v0.21.0 ✅已交付 | 玻璃变浓＋背景可换 | 四档染色减半、折射翻倍、打开 `depthEffect` 与 `chromaticAberration`；进场幅度加大（42dp/0.88/回弹）；新增背景预设三档（氛围光 / 粒子星空 / 自定义图片），背景即玻璃采样源 | 21（不变） | 无 |
| v0.22 ✅已交付 | 战报海报 | 1080×1920 图片海报；预览 / 分享 / 存相册；FileProvider | 21（不变） | 无 |
| 远期 / 明确不做 | — | WebDAV 同步（可选）；前台自动识别、账号云同步、国际化明确不做 | — | — |

```mermaid
gantt
    title Finch 路线图
    dateFormat  YYYY-MM-DD
    section v0.13 库管计时
    状态+暂停+编辑+PSN联调       :a1, 2026-09-20, 14d
    section v0.14 总结可视化
    热力图+时段+回本+海报        :a2, after a1, 14d
    section v0.15 日程推送
    关注推送+HLTB               :a3, after a2, 21d
    section v0.16 安全收尾
    自动备份+点查更新           :a4, after a3, 14d
    section v0.17 奖杯小组件
    PS奖杯同步+小组件扩展        :a5, after a4, 14d
    section v0.18 界面重构
    Liquid Glass 设计系统        :a6, after a5, 14d
    section v0.19 记录粒度
    标签+目标+备注               :a7, after a6, 7d
    section v0.20 玻璃化 UI
    开关+折叠分组+进场动画       :a8, after a7, 5d
    section v0.21 玻璃变浓
    配方重调+背景可换            :a9, after a8, 3d
    section v0.22 战报海报
    海报渲染+分享+存相册         :a10, after a9, 3d
```

---

## v0.13 —— 库管与计时可信（DB 8→9）✅ 已交付（v0.13.0）

**为什么先做**：计时是记账的命，不准后面全错；backlog 管不住库会越堆越乱。两者都是纯本地、无联调。

- **游戏状态**：`Game.status: 想玩 / 在玩 / 搁置 / 通关 / 全成就`（与旧 `completed` 布尔双写兼容）✅
- **主页筛选排序**：状态＋收藏＋平台 FilterChip，排序四档 ✅
- **计时暂停/继续**：`ACTION_PAUSE / ACTION_RESUME` + 通知按钮 + 恢复路径 + 小组件 ✅
- **会话可编辑**：记录页/详情页起止编辑 ✅
- **PSN 真机联调**：仍待实测（需提供 npsso token），延后到有真机条件时联调，不 blocking 后续版本。

<details>
<summary>原计划明细（已实现，展开查看）</summary>

- **游戏状态**：`Game.status: 想玩 / 在玩 / 搁置 / 通关 / 全成就`（替代散装的 `completed` 布尔，`completed` 保留做兼容，勾选通关自动同步 status）。
  主页加一排 FilterChip（状态＋收藏＋平台）＋排序（最近玩 / 总时长 / 评分）。
  改动：`data/Game.kt`、`data/FinchDatabase.kt(MIGRATION_8_9)`、`ui/HomeScreen.kt`、`ui/FinchViewModel.kt`。
- **计时暂停/继续**：`TimerService` 加 `ACTION_PAUSE / ACTION_RESUME`，通知加暂停按钮；`play_sessions` 加 `pauseAccumMs + pauseStartedAt`（暂停段不计入时长，走秒= now - start - pauseAccum）。
  注意同步改 `restoreOrStop()` 恢复路径和 `FinchWidgetSync`。
  改动：`timer/TimerService.kt`、`timer/TimerNotifications.kt`、`data/PlaySession.kt`。
- **会话可编辑**：记录页/详情页长按会话 → 改起止时间（`sessionDao().update()`），带“结束必须晚于开始”校验。无 schema 变更。
- **验收**：暂停 10 分钟只记 1 分钟；杀进程恢复暂停态不错；状态筛选+排序可用；`schemas/9.json` 入库。
- **工作量**：M（约 1.5–2 周，暂停是风险点）。

</details>

## v0.14 —— 总结可视化（DB 9→10→11）✅ 已交付（v0.14.1）

**为什么**：`StatsScreen.kt` 已有 `dailyTotals/topGames`，全是现成数据做展示，零联调、传播最强。

- **热力图** ✅：月视图按周对齐，年视图 12 月横滑
- **作息** ✅：周几条形（周一开头）+ 24 小时高峰柱
- **回本率** ❌已移除（v0.14.1：详情页价格卡 + `SteamStoreClient.fetchPrice` + `FinchViewModel.fetchPriceOnce` + 价格单测全删；DB 11 重建 games 表去掉价格列）
- **战报分享 v1** ✅：文本版走系统分享（图片模板延到 v0.17）

<details>
<summary>原计划明细（已实现，展开查看）</summary>
- **年度热力图**：GitHub 式 365 格（月视图 30 格），复用 `observeDailyTotals`，峰值高亮。不新增 DAO。
  改动：`ui/StatsScreen.kt` 新 `HeatmapCard`。
- **时段分布**：`SessionDao` 加两个聚合查询——按周几（`strftime('%w')`）、按小时（`strftime('%H')`）分组求和，画两排条形。
  改动：`data/FinchDaos.kt`（只加查询，不改表，**DB 版本不变也行**，但为省事可并入 v10）。
- **元/小时回本率**：`games` 加 `priceCny REAL + priceFetchedAt INTEGER`（MIGRATION_9_10），Steam 游戏从 `SteamStoreClient`（`appdetails` 价格接口）拉一次存下来；详情页显示“68 元玩了 120h，0.6 元/h，已回本 ✓”。
  改动：`data/Game.kt`、`data/SteamStoreClient.kt`、`ui/GameDetailScreen.kt`。
  （❌ v0.14.1 已移除；DB 11 去掉价格列）
- **月度战报海报 v1**：把现有 `HeroTotalCard + MonthReportCard + TopGames` 拼成一张 Bitmap，长按分享（系统 ShareSheet）。先做静态拼图，动画/模板放到 v0.17。
- **验收**：热力图与柱状图数字与 Hero 总时长对得上；无价格游戏不显示回本行；海报在浅/深主题下都不糊。
- **工作量**：M（约 2 周）。

</details>

## v0.15 —— 日程变有用（DB 11→12）✅ 已交付（v0.15.0）

**为什么**：日程页从“看”到“用”。WorkManager 在此版一次性引入，v0.16 自动备份直接复用。

- **发售关注＋推送** ✅：`release_follows` 表（MIGRATION_11_12），日程行 ☆/★ + 顶部置顶卡，Worker 每天检查发售前 3 天推送
- **通关进度条** ✅：`games.hltbMainMin`（并入 11→12），手动填参考时长（清空隐藏）；HLTB 在线抓取降级为手动，可用性优先
- **WorkManager 基建** ✅：`work-runtime-ktx:2.10.4`，`FinchApp.onCreate` 调度（KEEP）

<details>
<summary>原计划明细（已实现，展开查看）</summary>
- **WorkManager 基建**：加 `androidx.work:work-runtime-ktx`，先做一个 `DailyCheckWorker` 空壳（每天跑一次，打 log），为关注推送和自动备份铺路。注意 Doze/省电白名单提示文案。
- **发售关注＋推送**：新表 `release_follows(name/coverUrl/date/source/notifyDays)`（MIGRATION_10_11 建表），日程页每行加“☆关注”，3 天内发售发本地通知，点通知进详情/入库。
  改动：`ui/UpcomingScreen.kt`、`ui/UpcomingViewModel.kt`、新 `data/ReleaseFollow.kt + Worker`。
- **通关进度条（HLTB 参考）**：`games` 加 `hltbMainMin INTEGER`（并入 10→11 迁移），数据源先用 Bangumi 条目信息/手动填，HLTB 抓取做成可降级（抓不到就隐藏进度条，不报错）。详情页显示“已玩 Xh / 主线 Yh（Z%）”。
- **验收**：关注 3 天后的游戏能收到通知（杀进程后仍能）；无 HLTB 数据时详情页无变化；WorkManager 在小米/三星上不过度耗电。
- **工作量**：L（约 3 周，推送联调是风险点）。

</details>

## v0.16 —— 数据安全收尾（DB 不变）✅ 已交付（v0.16.0）

**为什么**：数据多了以后备份就是命。纯工程版，无 DB 变更。
**范围修订（2026-10-08 拍板）**：导出已有「导出备份」zip 覆盖需求，**CSV 不做**；版本检查改**点按钮才查**，不做每周后台轮询。

- **自动备份**：`SettingsStore` 加 `autoBackupEnabled + lastAutoBackupAt`，新 `AutoBackupWorker`（WorkManager 每周跑）调 `BackupManager.autoBackup()` 写应用私有目录、保留最近 4 份（失败半截 zip 即删）；导入页「备份与恢复」卡显示「上次自动备份时间」、开关、可从最近自动备份恢复（带确认弹窗）。
  改动：`data/BackupManager.kt`、`data/SettingsStore.kt`、新 `data/AutoBackupWorker.kt`、`ui/ImportViewModel.kt`、`ui/ImportScreen.kt`。
- **点查更新**：导入页新增「版本与更新」卡，点「检查更新」才 GET 一次 GitHub `releases/latest`（不后台轮询、不上传任何数据），有新版列出版本号并「去下载」跳浏览器；比对口径 `UpdateChecker.isNewerVersion`（逐段数字比较）。
  改动：新 `data/UpdateChecker.kt`、`ui/ImportViewModel.kt`、`ui/ImportScreen.kt`。
- ~~**CSV 导出**~~：不做——「导出备份」zip 已覆盖导出需求。
- **验收**：自动备份开启后落盘、只留最近 4 份、可一键恢复并重启应用；飞行模式下点检查更新只报网络失败、不崩；版本号逐段比较（0.15.9 < 0.15.20）。
- **注意**：自动备份在应用私有目录，**卸载即清**——换机/重装前走「导出备份」存外部（文案已提示）。
- **工作量**：S。

## v0.17 —— 奖杯与小组件（DB 16→17）✅ 已交付（v0.17.0）

**范围修订（2026-10-08 拍板）**：~~成就/连击徽章~~、~~游玩预算提醒~~ 都不做，换成 **PS 奖杯同步**；小组件扩展保留。
**为什么**：PSN 同步通道（npsso→OAuth→accountId）已就绪，奖杯概要接口与 gamelist **同域同 token**（`m.np.playstation.com/api/trophy/v1/users/{accountId}/trophyTitles`，一次分页最多 800 条，per-game 白金/金/银/铜＋进度＋最近获奖时间），边际成本低、对 PS 玩家感知强。

- **PS 奖杯同步（DB 16→17）**：`games` 加奖杯列（`trophyPlatinum/Gold/Silver/Bronze + trophyProgress + trophyUpdatedAt`，MIGRATION_16_17）；PSN 同步顺带拉奖杯概要，按名字归认到库内游戏（沿用同步的匹配口径，宁漏勿错）。
  - 详情页加奖杯卡（白金/金/银/铜 + 进度条）；总结页加奖杯汇总（总白金数等 + 奖杯等级，`getUserTrophyProfileSummary`）
  - 改动：`data/PsnClient.kt`（trophyTitles 分页 + 概要解析）、`data/SyncEngine.kt`、`data/Game.kt` + `FinchDatabase.kt`（迁移与 schema 17）、`ui/GameDetailScreen.kt`、`ui/StatsScreen.kt`
- **小组件扩展**：现有 `FinchWidget` 加周累计 + 月 Top1，点击直达对应详情页（沿用整分推送节奏不加耗电）。
- **~~成就/连击徽章~~ / ~~游玩预算提醒~~**：不做（用户拍板）。
- **战报海报 v2（可选）**：多模板/深色模板，v0.14 反馈好才做，否则砍。
- **验收**：PSN 同步后库内 PS 游戏带奖杯四围与进度、非 PS 游戏无变化；`schemas/17.json` 入库、老库升级数据不丢；奖杯接口解析有单测（口径入 JsonParseTest）；小组件不增加耗电。
- **风险**：奖杯接口形态以实测为准（与 Nlib/游民同规矩，文档仅参考）；接口若要逐游戏细查会超请求预算——只拉概要级数据，不逐奖杯展开。
- **实测修正（2026-10-26，v0.18.1）**：`trophyTitles` 概要接口实测**不含 npTitleId**（0/8），PS 行名字兜底又遇「合集 vs 单作重制」对不上 → v0.17.0 线上实际未写入任何奖杯；改为 `/trophy/v1/users/me/titles/trophyTitles?npTitleIds=`（每批 ≤5，实测 6 个报 400）按 `games.psnTitleId` 精确归认，多奖杯集四围求和、完成度按已获得/总数折算。
- **工作量**：M（约 2 周，PSN 接口实测是风险点）。

## v0.19 ✅ 已交付 —— 记录粒度（DB 19→20）

会话标签与时长目标落地；图片海报拆到 v0.21 单独做（需要 FileProvider，单独一版更干净）。

- ✅ **会话标签**：`play_sessions.tag TEXT`（`MIGRATION_19_20`，主线/支线/联机/多周目/自定义，null = 未分类）；记录页顶部胶囊筛选、编辑弹窗内打标、详情页同步可改
- ✅ **标签分布**：总结页「标签分布」卡（本期时长＋条数＋占比条），战报文本带主打标签
- ✅ **时长目标**：`SettingsStore.monthGoalMin / yearGoalMin`（纯配置无 DB），设置页可填可清，总结页 Hero 卡出进度与百分比
- ⏭ **战报图片海报 v1**：改到 v0.21（与分享增强同批，需 `FileProvider`）
- **验收**：`schemas/20.json` 入库；标签升级迁移老会话不丢；单测 5+1 新增全绿
- **工作量**：M（约 2 周）→ 实际一轮完成

---

## v0.19.1 ✅ 热修 —— 同步不再丢记录 ＋ 备注（DB 20→21）

用户报「同步提示匹配更新 11 款，却写入 0 条游玩记录」。根因全在 `SyncEngine.diffWriteSessions`：

- ✅ **撞占位不再丢增量**：旧代码 `if (sessionDao.at(...) != null) continue` 会把整天份额直接跳过；
  现在改成延长当天占位的 `endTime`，并且只要份额没被分配器丢弃就计入「已落账」
- ✅ **基线只推进已落账量**：旧代码结尾无条件把快照对齐官方累计，没写进去的增量从此永久丢失；
  改为 `prev.totalMin + writtenMin`（本轮写满才对齐 totalMin），余量下次同步继续补
- ✅ **单条备注**：`play_sessions.note TEXT`（`MIGRATION_20_21`，null = 没写，最长 120 字）；
  编辑弹窗可写，记录页与详情页最多折 2 行显示，不进任何统计口径
- **验收**：DiffWriteSessionsTest 三例重写、MigrationTest 增 v20→21 专项；单测 132 全绿

---

## v0.20.0 ✅ —— 全面玻璃化（DB 不变）

用户需求：把整套 UI 做成真玻璃、设置页分组成可展开菜单、每张卡出现时有动画。
玻璃开关放在「设置 → 实验性功能」，默认关闭，老用户升级后观感不变。

- ✅ **实验性开关**：`SettingsStore.experimentalGlassEnabled`（`experimental_glass_enabled`）+ `GlassState` + `LocalGlassEnabled`
- ✅ **双路渲染**：开关关闭时 `GlassSurface` 走 `SolidPanel`（实色渐变 + 细描边，零离屏层）；打开才走 `drawBackdrop`
- ✅ **设置页折叠**：`FinchCollapsibleGroup`，原九张卡归为外观 / 数据同步 / 时长目标 / 数据安全 / 实验性功能 / 关于六组，头部显示摘要，箭头旋转 + 高度动画
- ✅ **进场动画**：`Modifier.finchEnter(order)`，淡入 + 上浮 + 缩放，同屏多卡按 `order` 错开
- **验收**：开关关闭时内存与滑动手感与 v0.19.1 一致；开关打开后底栏与卡片都是折射玻璃；六个分组可独立展开

## v0.20.1 ✅ 热修 —— 玻璃覆盖全组件 ＋ 进场动画全局生效（DB 不变）

用户报「只有底栏是玻璃」「下滑新出现的卡片没有动画」。两条根因都成立：

- ✅ **卡片导出自己的玻璃层**：原来卡片采样的 `LocalFinchBackdrop` 是根部氛围光（三团平滑光斑），
  透镜折射做的是像素位移，把近乎恒定的渐变换个位置等于没变；底栏采样的是「背景＋内容」合并层，
  有文字封面这些高频细节，才显得像玻璃。现在 `drawBackdrop` 传 `exportedBackdrop`，
  把卡片已经画好的玻璃结果导成采样源，再用 `CompositionLocalProvider` 改写 `LocalFinchBackdrop` 供给卡内控件。
  `MAX_GLASS_DEPTH = 1`——只导出「根背景 → 卡片」这一层，再深要每层多录一张离屏层，视觉上已看不出差别
- ✅ **背景加颗粒**：氛围背景铺 620 颗固定种子颗粒（细粒 / 中团 / 大散景三层），中团尺寸落在折射位移量级上
- ✅ **其余控件玻璃化**：`InsetSurface`（芯片 / 分段 / 按钮底座）改走玻璃，输入框底色透明只留玻璃描边
- ✅ **动画记账改回 `remember`**：原先 `rememberSaveable` 播过就永久不再播；顺带修掉「延迟被打断时永久停在 alpha=0」的白屏隐患
- **验收**：滚动主页/记录页时新卡片逐个浮现；卡片内的开关滑杆在卡片玻璃面上折射而非采根部背景

## v0.21.0 ✅ —— 玻璃变浓 ＋ 背景可换（DB 不变）

用户报「玻璃太弱，看着像单纯一个磨砂遮罩」「动画幅度太小」「想换背景图」。

- ✅ **玻璃配方重调**（根因是染色重、折射轻）：Card 档 tintScale 0.50→0.22、折射高度 14→24dp、强度 22→32dp，
  打开 `depthEffect`（边缘纵深变形）与 `chromaticAberration`（边缘细微色散）。四档同比例调整，层级差不变
- ✅ **进场幅度加大**：上浮 28→42dp、缩放 0.95→0.88、弹簧换 `bouncy`（落定轻微回弹）、错开 45→60ms
- ✅ **背景可换**：新增 `BackgroundStyle { AURORA / PS5 / CUSTOM }` + `BackgroundState` + `FinchBackdrop` 统一调度
  - 氛围光 = 原 `FinchAurora`，保留为预设
  - 粒子星空 = PS5 开机风格（深空底 + 中央蓝青光晕 + 地平线辉光 + 260 颗分层粒子），**静态一帧**
  - 自定义图片 = 选图后复制进 `filesDir`（`content://` 授权会过期，直接存 URI 会失效），压一层跟主题走的色
- **关键决策**：背景就是玻璃的采样源——换背景等于换整块玻璃的观感，所以「玻璃变浓」和「背景可换」是一件事的两面。
  粒子背景刻意不做逐帧动画：`layerBackdrop` 每次内容重绘都要重录整张全屏离屏层，背景一动所有玻璃面板跟着抖
- **验收**：三档背景可切且重启保持；自定义图换图后旧副本被清理；深色主题配亮图时文字仍可读

## v0.22 ✅ —— 战报图片海报 v1（DB 不变）

**为什么先做**：这是唯一悬空的旧承诺（v0.19 时承诺改到 v0.21，v0.21 干了玻璃化）。体量小，能独立交付。

- **新增**：总结页「分享战报」从纯文本改成 1080×1920 竖版海报，弹预览后可分享或存相册
- **实现**：`ui/share/ReportPoster.kt` 纯 `Canvas` 按固定设计稿绘制，任意线程同步渲染
- **实现**：`ui/share/PosterShare.kt` 负责拉封面（`allowHardware(false)`）/ 写 PNG / 分享 / 存相册
- **实现**：`ui/share/PosterPreviewDialog.kt` 预览弹窗，`produceState` + `Dispatchers.Default` 后台渲染
- **实现**：`ui/share/PosterPalette.kt` 把当前主题色转成海报色板（深色主题出深底浅字）
- **关键决策**：不用 Compose 截屏。截屏需要目标组合已挂载并完成布局，而分享弹窗弹出的瞬间
  海报还没排版——要么先塞隐藏 Composable（额外开销 + 生命周期坑），要么等一帧（预览会闪）。
  固定尺寸绘制位置可预期，也不受用户字号/屏幕宽度影响，还能用单测盯住分箱逻辑
- **关键决策**：封面加载失败给 `null` 而不是中断——海报用主题色块兜底，分享不该因为一张图挂掉
- **安全**：`file_paths.xml` 只暴露 `cacheDir/share/`，不开 cache 根（否则连 Coil 缓存一起暴露）
- **兼容**：Android 10+ 存相册走 MediaStore `RELATIVE_PATH`，无需权限；更老系统退回 `insertImage`
- **测试**：11 条单测覆盖 `monthCells` / `yearCells` / `deltaLabel` / `heroParts`

## v0.23 ⏳ 候选（待拍板）

原定的「年度报告 / backlog 漏斗」主题仍未重排。按规划原则第 3 条（功能版与重构版交替），
v0.18–v0.22 已连续五版动 UI，下一版建议先做清理。备选：

- **清理版重构 / 性能** —— 大 UI 文件拆分（`StatsScreen` 1000+ 行、`ImportScreen` 1100+ 行、
  `GameDetailScreen`、`HomeScreen`），`FinchViewModel` 单例拆分子 VM；列表项重组范围收窄
- **年度报告 / backlog 漏斗** —— 原 v0.20 主题，体量大，能把「看」变成「回看一年」
- **PSN 真机联调** —— 从 v0.13 挂到现在的技术债，需 npsso token（用户已明确暂时不做）

---

## DB 演进一览

| DB | 版本 | 变更 |
|----|------|------|
| 8 | v0.12.2（旧基线） | `favorite/completed/completedAt/rating/thoughts` |
| 9 | v0.13.x（已交付） | `games.status + statusUpdatedAt`（已通关回填 COMPLETED）；`play_sessions.pauseAccumMs + pauseStartedAt` |
| 10 | v0.14.0（过渡） | `games.priceCny + priceFetchedAt`（回本率引入；v0.14.1 移除，仅作迁移垫脚） |
| 11 | v0.14.x（已交付） | 重建 games 表去掉价格列（与 v9 表结构一致） |
| 12 | v0.15.0（已交付） | 新表 `release_follows`；`games.hltbMainMin` |
| 13 | v0.15.1–0.15.3（过渡，已移除） | `games.hltbExtraMin + hltb100Min + igdbId`（IGDB/HLTB 引入；v0.15.4 移除，仅作迁移垫脚） |
| 14 | v0.15.4（过渡） | 重建 games 表去掉 IGDB/HLTB 列（与 v12 表结构一致） |
| 15 | v0.15.5–0.15.12（已交付） | 加回 `games.hltbExtraMin + hltb100Min`（HLTB 写回；v0.15.6 改走中转 API，DB 不变） |
| 16 | v0.15.13+（已交付） | 重建 `play_sessions` 统一 `pauseAccumMs` DDL（实体补 `defaultValue="0"`，升级装/新装一致） |
| 17 | v0.17.0（已交付） | `games` 加奖杯 6 列（`trophyPlatinum/Gold/Silver/Bronze/Progress/UpdatedAt`）；`MIGRATION_12_13` 补建 `index_release_follows_key`（v12 新装库升级崩溃修复） |
| 18 | v0.18.6（已交付） | `play_sessions.estimated`（0=真实/1=云端对账摊出的推算历史）；`MIGRATION_17_18` 回标旧 STEAM/SWITCH/PS 会话为估算，区间统计加 `estimated = 0` 过滤 |
| 19 | v0.18.8（已交付） | 移除 `play_sessions.estimated`（`MIGRATION_18_19` 先 `DELETE ... WHERE estimated = 1` 清推算行，再重建表去列）；取消「按官方累计差额摊历史会话」，记录只存真实会话 |
| 20 | v0.19.0（已交付） | `play_sessions.tag TEXT`（`MIGRATION_19_20`，无 DEFAULT；null = 未分类）；会话标签 + 记录页筛选 + 总结页标签分布；时长目标走 SettingsStore 不动 DB |
| 21 | v0.19.1（已交付） | `play_sessions.note TEXT`（`MIGRATION_20_21`，无 DEFAULT；null = 没写）；单条记录备注，记录页/详情页可写可看，不进统计口径 |

备份恢复逻辑（`BackupManager.validate()`）按 `FINCH_DB_VERSION` 校验，跨版恢复走 Room 自动迁移，老备份可读。

## 明确不做

- 启动自动同步（README 已定，不改）。
- 账号体系＋云同步（成本爆炸；WebDAV 可列为远期可选，优先级低于上面全部）。
- 前台应用自动识别在玩什么（`UsageStats` 权限重、耗电、易被差评）。
- 国际化（README 已定，不做）。

## 建议的动手顺序

1. **v0.13 ✅ 已交付**：状态＋暂停＋编辑，一版让“记”可信。
2. **v0.14 ✅ 已交付**：热力图＋作息＋战报分享 v1，让“看”值得分享。
3. **v0.15 ✅ 已交付**：发售关注＋推送、通关进度条，WorkManager 基建一次到位。
4. **v0.16 ✅ 已交付**（v0.16.0）：自动备份＋点查更新（复用 WorkManager），纯工程版。
5. **v0.17 ✅ 已交付**（v0.17.0）：PS 奖杯同步＋小组件扩展（徽章/预算已砍）。
6. **v0.18 ✅ 已交付**（v0.18.0）：Liquid Glass 界面重构（设计系统＋逐屏重做＋性能修复），纯前端版。
   - 后续热修：v0.18.1 奖杯映射、v0.18.2 总结页瘦身、v0.18.3 时长口径统一、v0.18.4 通关判定、v0.18.5 记录补齐（均 DB 不变）
7. **v0.19 ✅ 已交付**（v0.19.0 / v0.19.1）：记录粒度——会话标签＋标签筛选＋标签分布、时长目标、单条备注（DB 20→21）。
8. **v0.20 ✅ 已交付**（v0.20.0 / v0.20.1）：全面玻璃化——实验性开关＋双路渲染、设置页折叠分组、进场动画；热修补齐"玻璃只有底栏看得出来"与"动画只播一次"。
9. **v0.21 ✅ 已交付**（v0.21.0）：玻璃变浓（纵深变形＋边缘色散）＋背景可换三预设。
10. **v0.22 ✅ 已交付**（v0.22.0）：战报图片海报 v1——1080×1920 竖版海报、预览 / 分享 / 存相册、FileProvider。
11. **v0.23 ⏳ 待拍板**：清理版重构（大 UI 文件拆分、ViewModel 拆分）／年度报告与 backlog 漏斗（原 v0.20 主题）／PSN 真机联调。
