# Changelog

## 0.29.2 (2026-11-11)

0.29.1 的卡把价格放在底部信息带的左边，字号和标题一样大，扫列表时价格并不跳出来。
这版按港服商店条目卡的样子重排：**价格整列搬到右边，现价放大到 22sp**。

### 卡片按港服商店的样子重排

- **左边**：88×118 封面（尺寸不变，卡片高度仍由它决定）
- **中间**：标题（最多两行）+ 别名，底部压着发售倒计时徽章与平台
- **右边**：一整列价格 —— 折扣角标在上、现价最大、划线原价垫底，全部右对齐
- **底部**：左侧是折扣截止日，右侧是星标与入库/商店按钮

价格从「底部信息带的一个元素」变成「独立的一列」，是这一版唯一的实质改动。原来价格和按钮
同基线，视觉重量一样，注意力会被按钮分走；现在价格竖着占满卡片右侧，和封面等高，扫列表时
先看到图和价，再看到标题。

现价用 `headlineSmall`（22sp）主题色（打折时），比标题的 `titleMedium` 大三档；价格列宽
限制在 56–96dp，`HK$1,388.00` 这种长价格用 `TextAutoSize.StepBased(13..22sp)` 自适应缩到放得下，
不会把中间标题挤成一条缝。

日程页与愿望单共用 `StoreEntryCard`，两边同时生效。

### 合计

测试 212 全过，编译 0 warning。versionCode 100，versionName 0.29.2。DB 仍 23，无迁移。

## 0.29.1 (2026-11-10)

0.29.0 的愿望单占了底栏一格、日程卡两段式留白别扭，这版把两件事一起收拾。

### 愿望单收回日程页里

愿望单本质上还是「日程里关注过的东西」，单独占一个 tab 收益配不上成本：底栏挤到六格、
每格变窄，而从日程点 ☆ 到看价格还得多跳一次 tab。现在它是日程页的子视图 —— 顶部一条窄条
显示关注数量，点进去就是原来那页（三段筛选、汇总、卡片全部保留），左上角箭头或系统返回键回到列表。

子视图状态用 `rememberSaveable` 保存，转屏不会掉回列表；`BackHandler` 只在这一层注册，
不干扰 scaffold 的「返回主页」。

### 卡片重新排版

0.29.0 的卡是「封面+标题」→「价格独占一整行」→「操作行」，价格那一行下面留了半张卡的空，
眼睛看过去像两级台阶，而且星标和入库按钮挤在底部右侧抢位置。现在改成两段：

- **上半**：88×118 封面 + 标题/别名 + 日期与平台一行（中间用 `·` 分隔）
- **下半**：一条信息带 —— 左边价格（现价 + 划线原价 + 折扣角标，仍可换行不截断），右边操作按钮

星标移到标题右侧（收藏语义的常规位置），入库/商店按钮独占右下角，不再和星标抢横向空间。
日程页与愿望单共用同一个 `StoreEntryCard`，两边版式与价格口径完全一致。

### 合计

测试 212 全过，编译 0 warning。versionCode 99，versionName 0.29.1。DB 仍 23，无迁移。

## 0.29.0 (2026-11-10)

日程页收敛到**三个源**（PSN 港服 / 游民星空 / eShop），PSN 港服提到最前并成为默认；
新增「愿望单」tab，把关注过的游戏连同价格、折扣、发售日一起攒下来。

### 删掉 Steam 与 Bangumi 两个源

日程页原来五个源，Steam 与 Bangumi 的关注度和信息密度都最低：Steam「即将推出」只有英文名 +
模糊发售档期，Bangumi 日历主要覆盖日本动画改编作品。删的是**日程源分支**，
`SteamStoreClient` 与 `BangumiClient` 仍然保留——HltbFetch 按名搜索、GameSearchClient 全库搜索、
SwitchTitleClient 都在用它们，删客户端会连带炸掉别处。

顺带删掉的还有状态里的 `note` 字段（原本只用来标「缓存（Bangumi）」）和 `calendar_json` 缓存：
现在默认源是 PSN，第一次打开不该先闪一屏上一次的 Bangumi 数据再换掉。

### 新的「愿望单」tab（日程页 → 愿望单）

- **价格快照跟着关注走**：`release_follows` 加 5 列（priceText / basePriceText / discountText /
  saleEndIso / productId），点 ☆ 时把当前条目上的价格一起写进去
- **每次拉到港服数据顺手回写**：`syncFollowPrices` 用 `followKey(bangumiId, steamAppId, name)` 对齐，
  预购条目优先于折扣条目（避免同名的折扣行把预购的日期信息覆盖成空），字段全等才跳过写库
- **页面内容**：顶部三格汇总（关注数 / 打折中 / 最大折扣），每张卡是大封面 + 标题 + 发售倒计时 +
  现价/划线原价/折扣角标 + 折扣截止日；有 productId 时可以一键跳港服商店页
- **筛选**：全部 / 打折中 / 待打折三段
- **离线可用**：价格是本地快照，没网也能看上次同步到的价

### 日程卡片改大

旧卡是 52dp 封面 + 三件套价格挤在标题右侧一行，港服长中文名一多就把折扣角标挤没了——
这就是"太小、折扣被挡住"的根因。新卡：92×124dp 封面 + 标题/中文名/发售徽章/平台，
**价格独占整行**且用 `FlowRow` 允许换行，折扣截止日单独一行，最后才是关注与入库操作行。

### 数据层

- `FINCH_DB_VERSION` 22 → 23，`MIGRATION_22_23` 是 5 条 `ALTER TABLE release_follows ADD COLUMN`
- 迁移测试补 `checkFrom(22)` 与 22→23 专项（断言 5 个新列存在、旧行价格字段为 null）
- 底栏从 5 tab 变 6 tab，`LiquidBottomTabs` 的 `tabsCount` 本就是动态参数，底栏代码未改

## 0.28.0 (2026-11-10)

日程页多一个源：**PSN 港服**。不补价格到别的源，而是把港服商店本身当成一个日程源——
预购新作按发售日排，正在打折的按力度排，价格直接来自商店，不靠游民名字去猜。

### 为什么新开一个源，而不是给游民条目补价

先按「给现有日程补价格」试过：游民 2027 年那批 45 个候选里，靠商店目录 + 名字相似度只能命中
7 个（15%），靠旧 chihiro 搜索只命中 1 个。原因是两头写法不一样（游民用「：」「重制版」「4」，
港服用「～」「Revival」「４」），而且 **2027 年的游戏港服多半还没上架**——没上架就没价格，
这不是匹配算法能解决的。所以改成让商店自己说话。

### PSN 港服源

- **数据来源**：`web.np.playstation.com/api/graphql/v1/` 的 persisted query（照抄
  mrt1m/playstation-store-api 的查询形状与 sha256 白名单，**没有引入任何依赖**）。
  两个类目：PS5 游戏按 `productReleaseDate` 降序取首 250 条筛预购、`AllDeals` 取全部折扣
- **只认三个 header**：浏览器 UA + `x-psn-store-locale-override: zh-hans-hk`（不带就落英区）+
  `apollo-require-preflight: true`（不带会被判 CSRF 直接 400）。**不需要任何 token**
- **预购**：250 条里 70 个 SKU 按 `npTitleId` 收成 33 个标题，再逐条补发售日（`metGetProductById`，
  单条 14KB/1.2s，8 并发）；同日多档优先取 `FULL_GAME` 本体，不吃 HK$50 的「豪华版升级」包
- **折扣**：235 条里 55 个 SKU 收成 45 个标题。截止日抽 3 条查 campaign，三条一致
  （`endTime` 同一时刻），故整组共用一个截止日，不做 N+1
- **9 秒出结果**：目录、折扣、补日期、抽截止日四条线并发；实测 9.0s（首次 25s 是因为
  测试里没用 IO 线程，`runInterruptible` 被串行化了）。成功写 JSON 进 SharedPreferences，
  失败回落上次缓存，离线也能看上次的价
- **展示**：行内现价 + 原价划线 + `-70%` 角标；折扣条目单独一组「正在打折」，组标题显示
  「至11月20日」，行下再加一行「优惠至11月20日（还剩10天）」

### 三个实测才暴露的问题

- **`optString` 把 JSON null 读成字符串 "null"**：于是每个原价游戏都被当成在打折。
  新增 `strOrNull()` 统一处理，所有语义上可空的字段都改走它
- **同款 PS4/PS5 两条**：两版是两套 `npTitleId`，按 id 去重去不掉。改成 id 去重后再按清洗过的
  名字去一次重
- **「全部折扣」里混着非折扣**：PS+ 库内免费（`discountedPrice` 是「包含」，没有数字价）和
  追加内容 DLC。按「价格能解析出数字」+ 名字不以「(追加内容)」结尾两条滤掉

### 合计

测试 192 → 210 全过（新增 `PsnStoreClientTest` 18 条：价格解析、折扣百分比、名字清洗、
日期/时间戳解析、预购与折扣挑档、JSON 往返、坏 JSON）；编译 0 warning。DB 仍 22，无迁移。

## 0.27.0 (2026-11-06)

修一个作息读错的问题，补两处用户直接提的体验：通关超没超、白金拿没拿。

### 作息改成按自然周独立（DB 不变）

- **「周五周六周日还没玩，怎么柱子上已经有时间了」**：v0.26 把作息改成「最近 90 天」滚动窗口，
  过去几周的五六日本来就会累加进来，看着就像本周的。`ui/StatsRhythm.kt` 新增
  `weekStartOf(date)`（周一为一周起点），作息卡改成按自然周取数：`sessionSpans` 的窗口
  变成「本周一零点 ~ 下周一零点」，跟月/年切换完全脱钩
- **加周切换**：卡片顶部三个控件——`←` 上一周 / 标题（本周显示「本周」，其他周显示 `M/d – M/d`）/
  `→` 下一周。右箭头在「本周」时禁用，翻不到未来
- **没记录也保留卡片**：原来 `weekday` 为空时整张卡不渲染，那样本周一进来没记录就再也翻不回上周了。
  月视图下改成恒渲染
- **年度报告里的作息改看「这一年」**：原来借的是同一个 90 天滚动窗口，现在直接用当期
  `fromMillis/toMillis`，跟「年度报告」这个名字对上

### 通关进度写出超出与剩余（DB 不变）

- **「我有时候玩得比标准通关时间还长」**：`progressFraction` 会把比例钳到 1，所以进度条看不出超了多少。
  `data/GameProgress.kt` 新增 `mainOverrunMin` / `mainRemainingMin` 两个纯函数，
  `HltbSection.kt` 在主文案下面加一行：超了写「已超出主线参考 X」，没到写「还差 X 到主线参考」
- **超了不只是一句坏消息**：还会补一句「离支线参考还有 X」或「离全收集参考还有 X」，
  取两者中第一个大于已玩时长的

### 白金奖杯庆祝（实验性，DB 不变）

- **PSN 同步拿到白金时弹全屏动画**：奖杯、背后光晕、扫过高光全部 Canvas 手绘
  （`ui/gamedetail/PlatinumCelebration.kt`），**没有引入任何外部素材**——零资源体积、无版权问题、
  不依赖网络（做法与既有的 `ConfettiOverlay` 一致）。3400ms：520ms 弹入、停住 2100ms、淡出
- **「首次获得」只能自己比对**：PSN 只给奖杯数，没有获得时间戳。新增 `data/PlatinumWatch.kt`：
  同步前拍 `游戏id → 白金数` 快照，同步后只认 0→≥1 的；播完把 gameId 记进
  `settings.platinumCelebratedGameIds`，否则每次同步都会重弹。两条 PSN 同步入口
  （主页下拉刷新、设置页同步）都接上了
- **挂在外壳最外层**：`FinchAppScaffold` 的根 `Box` 里，所以停在哪个 tab 都能弹
- **开关**：设置 → 实验性功能 → 「白金奖杯庆祝」（默认开）

### 合计

测试 183 → 192 全过（新增 `GameProgressTest` 5 条、`StatsRhythmTest` 4 条周起点用例）；
编译 0 warning。DB 仍 22，无迁移。

## 0.26.1 (2026-11-05)

只做减法与纠错，不加功能：总结页三处口径修好，标签功能整体删掉（DB 21→22）。

### 总结页口径修复（DB 不变）

- **作息 / 高峰时段改看最近 90 天**。原来的区间跟着当期月/年走，于是「只选这个月」时作息里
  上一周的数据还占着位置——那不是作息，是「这个月的作息」。新增 `RHYTHM_DAYS = 90`，
  窗口固定为「今天往前 90 天到明天零点」，与月/年切换无关。卡片副标题直接写明口径
- **高峰时段按小时摊分**。原 SQL 用 `strftime('%H', startTime)` 把整条会话算进开始那一小时，
  20:00 玩到 02:00 会被记成「20 点玩了 6 小时」。新增 `ui/StatsRhythm.kt` 的
  `partitionRhythm(spans, from, to, zone)`：按小时切段、按「有效时长／墙钟时长」占比摊到每一格，
  跨零点的会话按各自日期落进不同周几桶；先裁剪窗口再摊分
- **暂停只能按比例摊**：库里只存 `pauseAccumMs` 累计值，没有暂停起止时间，无法精确定位到哪一小时。
  KDoc 已写明这个取舍
- **删「活动」卡**：它与热力图是同一份 `dailyTotals` 的两种画法，热力图信息更密（还能看连续与断档）。
  `StatsCards.kt` 的 `ActivityCard` / `BarChart` / `Bar` 与三个专用 import 一起移除
- **总时长 hero 改玻璃**：从 `.shadow(18.dp) + Brush.linearGradient(primary→tertiary) + 两个白色光斑`
  换成 `GlassSurface(style = GlassStyle.Hero, shape = FinchShapes.hero)`，文字从固定 `Color.White`
  改成 `onSurface` 系。关掉「全面玻璃化」时由 `SolidPanel` 接管，不会退回刺眼的纯色

### 删掉标签功能（DB 21→22）

- **为什么删**：标签是 v0.19「记录更细」的一半，上线后没有任何一次用它得出结论——
  「主线 8 小时 / 支线 3 小时」既不影响玩什么也不影响买什么，只让每次记录多两次点击。
  真正会写的字段是备注（记这次玩到哪了），备注留下
- **删掉的入口**：记录页顶部胶囊筛选条（`TagFilterRow` + `tagFilter` + 「这个标签下没有记录」分支）、
  编辑弹窗的 `TagPicker`（四档预设 + 自定义输入）、记录卡上的标签徽章、详情页会话行的「· 标签」。
  记录页现在只有一种视图：最近 300 条按天分组
- **删掉的统计**：总结页「标签分布」卡（`TagDistributionCard`）、年度报告里的同一张卡，
  以及 `SessionDao.observeTagTotals/observeTagTotalsForGame/updateTag`、
  `FinchViewModel.tagTotals/tagTotalsForGame/setSessionTag`（`setSessionTag` 本来就是零调用者的死方法）
- **战报海报**：三格速览第三格「主打标签」换成「日均」（有记录的那些天的平均时长，
  `ReportPoster.dailyAverageLabel`）——原来那格是海报上唯一的标签出口
- **`play_sessions.tag` 真删**：`MIGRATION_21_22` 重建表（保留
  `id/gameId/startTime/endTime/source/pauseAccumMs/pauseStartedAt/note` + 两个索引 + 外键 CASCADE），DB 21→22。
  历史迁移 `MIGRATION_19_20`（`ADD COLUMN tag`）**保留不动**——迁移链要能重放，
  改历史迁移等于让老库升不上来
- **备注能力保留**：`data/SessionTag.kt` 拆成 `data/SessionNote.kt`（只剩 `MAX_LENGTH = 120` 与
  `normalize()`），`updateSessionTimes(id, start, end, note)` 去掉 tag 参数
- **注意**：升级后旧的标签数据会随列一起消失，不去读也不先导出——它从来没有被任何统计口径读过；
  备注与其余字段原样保留

### 合计

- 测试 177 → **183** 全过（新增 7 条 `StatsRhythmTest`、3 条 `SessionNoteTest`，
  `MigrationTest` 加 `v21升级到当前_数据保留` 与 `v21到22_标签列被移除_备注与其余数据保留`，
  删掉 5 条 `SessionTagTest`）
- 编译 `:app:compileDebugKotlin --rerun-tasks` **0 warning**；单测 183 条全过；
  `assembleDebug` / `assembleRelease` 均通过（release APK 已签名，versionCode 95 / versionName 0.26.1）；
  提交 `ed7698e`，25 files、+922/−574

## 0.25.0 (2026-11-04)

v0.25 三条候选一次做完（无 DB 变更）：PSN 真机联调、`GameDetailScreen` 拆分、WebDAV 云备份。

### PSN 真机联调（用真 npsso 打进真实接口后发现并修掉三个问题）

- **修：媒体 App 被当成游戏导入**。真机 gamelist 里除了 `ps5_native_game` / `ps4_game`，
  还有 `ps5_web_based_media_app`（bilibili）与 `ps5_native_media_app`（Apple Music），
  而且它们**同样带 `playDuration`**（实测 `PT41M21S` / `PT5M34S`），原来的「有游玩时长就收」
  会把这两个 App 变成两款假游戏。新增 `PsnClient.isPlayableCategory(category)`：
  空值/空白按可玩处理（宁多勿丢），否则只认 `_game` 结尾
- **修：带 ™ 的官方名认领不到老条目，每次同步都新建一款重复游戏**。PSN 返回的
  `name` 带商标符号（`Uncharted™: The Nathan Drake Collection`），而库里用户自己录的名字没有，
  `allByName` 精确匹配落空就走「新建」。新增 `NameVariants.matchKey(name)`
  （剥商标/装饰符号 → 小写 → 压空白），`SyncEngine.runPSN` 在精确匹配落空后按 `matchKey`
  查一次归一索引；**归一值只用于认领，绝不写回库名**，也不剥副标题——那是搜索口径
  `buildNameVariants` 的职责，拿来判同一性会把 `Foo Remastered` 和 `Foo` 并成一条
- **修：refresh_token 有效期文案与实现差了一个量级**。真机实测 `refresh_token_expires_in`
  = 863999（≈10 天），代码注释与设置页文案原写「约两个月」。三处文案统一改成 10 天，
  并把 `ImportViewModel` 的「快过期」提示窗口从 7 天收到 2 天（10 天的票用 7 天窗口，
  授权后第 4 天起就会长期挂警告）
- **实测确认无需改动的部分**：`playDuration` 是 ISO-8601（`PT30H51M4S`）；`titleId` 形如
  `PPSA34579_00`；奖杯批次**带「0 奖杯集」条目不会整批失败**（HTTP 200 + 空数组），
  所以 `fetchTrophiesForTitles` 里「必须退回逐个查」的注释假设不成立（代码留着无害）；
  refresh 换票时 `refresh_token` 不轮换、旧票可重复使用，「立即写回最新值」的做法仍保留
- **测试**：新增 7 条 `PsnNameMatchTest`（类目过滤三组 + `matchKey` 三组）

### `GameDetailScreen` 拆分（669 → 约 260 行，视觉零变化）

- 抽出 `ui/GameDetailCards.kt`：`DetailCover`（封面把原来的 `Animatable` 改成传 `coverScale: Float`，
  避免把状态对象塞进子组件）/ `DetailTitle` / `TimerConsole` / `DetailStatRow` / `RecordGapNote` /
  `StatusRatingCard` / `ThoughtsCard` / `SessionsCard` / `DetailTopBar` / `CoverCircleButton` /
  `TrophyCard` + `TrophyCount`
- 主文件只剩状态计算与排版顺序；`StarGold` 常量随星级代码一起挪走后删除
- **踩坑**：`Icons.AutoMirrored.Filled.ArrowBack` 需要单独 `import
  androidx.compose.material.icons.automirrored.filled.ArrowBack`，写全限定名会 Unresolved

### WebDAV 云备份（新增）

- 新增 `data/WebDavClient.kt`：只做四件事——探活（PROPFIND Depth 0，目录不存在先 MKCOL）、
  上传（PUT）、列出（PROPFIND Depth 1）、下载（GET）；HTTP Basic 认证；**从不删除远端文件**
- 命名约定 `finch-cloud-yyyyMMdd-HHmmss.zip`，恢复时按文件名时间戳取最新一份
- 新增 `BackupManager.exportBytes(...)` / `restoreFromBytes(...)`，与已有的 zip 导出/恢复共用同一套
  写入与 schema 校验逻辑（不复制代码）
- 设置页「数据安全」分组内新增「云端备份」卡：地址 / 账号 / 应用密码 + 「保存并测试」
  「上传备份」「从云端最新备份恢复」（恢复前二次确认，destructive）
- 密码存 `SharedPreferences` 且走已有的 Keystore 加密路径（`readSecret`/`writeSecret`），
  与 Steam Key / PSN token 同等待遇；地址会被归一化成「带协议 + 以 / 结尾」
- **不做后台定时上传**：网盘流量与密码都在用户手里，什么时候传由他决定；开关只表示「保留配置」
- **测试**：新增 11 条 `WebDavClientTest`（地址归一化、备份名识别、PROPFIND 命名空间解析、
  href 解码、取最新、文件名与 URL 编码）

### 合计

- 测试 159 → **177** 全过（PSN 7 条 + WebDAV 11 条）

## 0.24.0 (2026-11-04)

年度报告 + 库存漏斗（无 DB 变更）：

- **新增「年度报告」整页**：总结页右上角多一个按钮，进去是固定某一年、可从头读到尾的纵向叙事——
  今年的总时长／同比／目标进度 →「这一年」一句话总结（玩了几天、几款游戏、最长连续几天、
  平均每天多久、最投入的月份）→「今年通关」清单（含通关日期）→「库存变动」
  （今年入库多少、通关多少、进得比清得快）→ 热力图／作息／平台／标签／Top 游戏 →
  库存漏斗 → 页脚。返回键或左上箭头回到原来的月／年视图
- **新增「库存漏斗」卡**：按 想玩 → 在玩 → 搁置 → 通关 → 全成就 五段堆叠，
  每段显示款数、占比、这段合计时长；标题右上角直接给完成率；末尾点名「吃灰最久」的三款
- **新增纯口径模块** `ui/report/YearReportData.kt`：连续游玩天数（日期去重后找最长连续区间）、
  活跃天数、峰值月份／星期／单日、本年通关／入库（按 `completedAt` / `createdAt` 年份）、
  完成率、吃灰最久——全部无状态纯函数，UI 只负责排版
- **测试**：新增 16 条 `YearReportDataTest`（漏斗分桶、老数据 `status=null` 按 `completed` 兜底、
  连续天数含补零与重复日期、峰值为空、越界星期兜底、本年清单排序、吃灰只挑想玩/搁置），
  总数 143 → 159 全过
- **实现细节**：年度报告直接复用总结页已有的 8 个卡片（`StatsCards.kt`）与战报海报渲染器，
  没有新增 DAO、没有新增查询；年份取自总结页当前 `anchorMonth`，所以「翻到 2024 年再点年度报告」
  看到的就是 2024 的报告

## 0.23.0 (2026-11-04)

清理版重构（无 DB 变更，**视觉零变化**）：只拆文件、不改绘制逻辑。

- **`StatsScreen.kt`** 1079 → 288 行：8 个卡片（Hero / 月度报告 / 平台 / 标签 / 活跃度 /
  Top 游戏 / 热力图 / 作息）搬到新的 `StatsCards.kt`（857 行），`private fun` 改 `internal fun`
- **`ImportScreen.kt`** 1185 → 396 行：按主题拆成 6 个文件 —— `SettingsBackground.kt`（背景选择 /
  图片副本 / 清理）、`SettingsTheme.kt`（主题色板）、`SettingsGoal.kt`（HLTB 行 / 时长目标）、
  `SettingsSync.kt`（PSN / Switch 卡＋同步状态）、`SettingsBackup.kt`（备份 / 更新）、
  `SettingsSwitch.kt`（任天堂网页登录 + 深链回调）
- **`HomeScreen.kt`** 806 → 479 行：主视觉卡与游戏行卡搬到 `HomeCards.kt`（350 行）
- **`FinchViewModel.kt`** 551 → 285 行：255 行的 HLTB 英文名解析链整段搬到 `HltbFetch.kt`，
  签名从成员函数改成扩展函数 `internal fun FinchViewModel.fetchHltbTimes(...)`；
  为让扩展能摸到成员，`gameDao` / `settings` / `updateGame` 由 `private` 放宽到 `internal`
- **实现细节**：拆分全程只做搬运——函数体、绘制顺序、颜色、间距、弹簧参数一字未改，
  所以视觉可回归点为零；`internal` 而非 `public` 是为了不把内部组合函数暴露成 API
- **实现细节**：`HltbSection.kt` 的调用点需要显式 `import dev.cao.finch.ui.fetchHltbTimes`，
  扩展函数不会跟着 `FinchViewModel` 一起被带进来（漏了就是 `Unresolved reference`）
- **测试**：仍是 143 条全过；编译 0 warning

## 0.22.0 (2026-11-03)

战报图片海报 v1（无 DB 变更）：

- **新增**：总结页「分享战报」从纯文本改成**图片海报**——1080×1920 竖版，
  打开后弹出预览，可「分享」给任意 App 或「存到相册」（`Pictures/Finch`）
- **海报内容**：期数标题＋“月度/年度战报”标签、本期总时长大数字、环比上期、
  目标进度条（设了目标才画）、三格速览（游戏数／最高产的一天／主打标签）、
  玩得最久 Top3（带封面）、每日／每月热力分布、页脚署名
- **决策**：不用 Compose 截屏，改为**纯 Canvas 按固定设计稿绘制**（`ReportPoster`）。
  截屏要先塞一个隐藏 Composable 等它完成布局，分享弹窗那一瞬间海报还没排版，
  要么白屏要么等一帧再截图；固定尺寸绘制位置全部可预期，也不受用户字号／屏幕宽度影响
- **决策**：海报配色由当前主题实时推导（`posterPalette()`），深色主题出深底浅字，
  浅色主题出浅底深字，避免“截图分享出去一片黑/一片白”
- **实现细节**：封面走 Coil 拉取，**必须 `allowHardware(false)`**——
  硬件位图画不到软件 `Canvas` 上；拉不到就给 `null`，海报用主题色块兜底，
  不让一张封面失败拖垮整个分享
- **实现细节**：新增 `FileProvider`（authority `${applicationId}.fileprovider`），
  `res/xml/file_paths.xml` **只暴露 `cacheDir/share/`**，不开 cache 根——
  否则连 Coil 图片缓存一起暴露了。文件名带时间戳，复用同名文件时部分目标会读到旧内容
- **实现细节**：Android 10+ 存相册走 MediaStore `RELATIVE_PATH` + `IS_PENDING` 两段式，
  不需要任何存储权限；更老系统退回已废弃的 `insertImage`
- **测试**：新增 11 条单测（`ReportPosterTest`），盯住几处肉眼难核对的分箱逻辑——
  月度格子长度与跨月隔离、年度柱状按月汇总与峰值归一、环比文案（上期为 0／持平和阈值 0.5%）、
  大数字拆分（≥1 小时拆“小时＋分”，不足 1 小时用“分钟”）

## 0.21.0 (2026-11-03)

玻璃更浓 ＋ 背景可换（无 DB 变更）：

- **修复**：玻璃看起来"像单纯一个磨砂遮罩"。根因是**染色重、折射轻**——
  Card 档 `tintScale` 0.50 的厚色把底下的纹理全盖住，折射位移又只有 14dp，
  两者叠加的结果就是一块磨砂塑料片。四档配方同比例重调：
  tintScale 0.50→**0.22**（Tile 0.42→0.18、Chrome 0.62→0.30、Hero 0.30→0.12），
  折射高度 14→**24dp**、强度 22→**32dp**，并打开 `depthEffect`（边缘纵深变形）
  与 `chromaticAberration`（边缘细微色散）。后者是"看着像玻璃"的关键，之前一直关着
- **变更**：进场动画幅度加大——上浮 28→**42dp**、起始缩放 0.95→**0.88**、
  弹簧换 `bouncy`（落定轻微回弹）、错开间隔 45→**60ms**。原来那组参数在手机上几乎看不出来
- **新增**：背景可换，设置 → 外观 → 背景，三个预设
  - **氛围光**：原来的主题光斑，默认档，最省电
  - **粒子星空**：PS5 开机风格——深空底、中央蓝青光晕、地平线辉光、260 颗分层粒子
  - **自定义图片**：选自己的图，铺满裁切，压一层跟主题走的色
- **关键决策**：背景就是玻璃的采样源——玻璃像不像玻璃，几乎全看它下面透出什么。
  纯平滑渐变折射几个像素等于没变，所以"玻璃变浓"和"背景可换"是同一件事的两面。
  换成有细节的图片，折射会立刻明显起来
- **决策**：粒子背景做成**静态一帧**，不做逐帧动画。这一层是玻璃的采样源，
  `layerBackdrop` 每次内容重绘都要重录整张全屏离屏层——背景一动，所有玻璃面板的折射纹理跟着抖，
  每帧还多录一张全屏位图。代价不值得
- **实现细节**：自定义图片会复制一份进应用私有目录（`filesDir`）再使用——系统给的
  `content://` 授权会过期，直接存 URI 很可能几次启动后背景就空了；换图时旧副本自动清理。
  图片压色跟主题走（浅色压白、深色压黑），否则深色主题配一张亮图，白字直接看不清
- **配置**：新增 `background_style` / `background_image_path`，默认氛围光，老用户升级后观感不变

## 0.20.1 (2026-11-03)

玻璃覆盖全部组件 ＋ 进场动画全局生效（无 DB 变更）：

- **修复**：只有底栏看得出来是玻璃，卡片就是一块半透明色板。根因是**采样源错了**——
  卡片采的是根部氛围光（`backgroundBackdrop`），只有三团大面积平滑光斑；
  透镜折射做的是像素位移，把一片几乎恒定的渐变挪几个像素，画面当然没变化。
  底栏采的是「背景＋内容」合并层（`rememberCombinedBackdrop`），有文字和封面这些高频细节，才显得像玻璃。
  现在 `drawBackdrop` 传 `exportedBackdrop`，把卡片已经画好的玻璃结果导成一层采样源，
  再通过 `CompositionLocalProvider` 改写 `LocalFinchBackdrop` 交给卡内控件——
  开关、滑块、分段控件、芯片都在**卡片自己的玻璃面上**折射，而不是去采最底下的氛围光
  （后者会让控件区域对不上，看起来像在卡片上挖了个洞）。
  只导出「根背景 → 卡片」这一层（`MAX_GLASS_DEPTH = 1`），再深要每层多录一张离屏层，视觉上已看不出差别
- **新增**：背景加颗粒——氛围背景铺 620 颗固定种子颗粒，分细粒／中团／大散景三层。
  中团尺寸正好落在折射位移量级上，透镜边缘能把它们拉出可见的弯曲。种子固定 → 每帧一模一样，采样层不跳
- **变更**：`InsetSurface`（芯片、分段控件、玻璃按钮的底座）在开关打开时改走玻璃面板；
  输入框底色改透明，只留玻璃描边，让卡片纹理透上来
- **修复**：进场动画只播一次。`Reveal.kt` 原先用 `rememberSaveable` 记「播过了」，
  状态被保存后 LazyColumn 回收再复用、切 tab 回来都不会重播。改回 `remember` 后
  下滑页面时新滑进屏幕的卡片自己都会浮现一次；顺带修掉一个更严重的隐患——
  延迟被打断（快速滚动）时可能永久停在 alpha=0 的白屏

## 0.20.0 (2026-11-03)

全面玻璃化 UI ＋ 设置页折叠分类 ＋ 卡片进场动画（无 DB 变更）：

- **新增**：设置页「实验性功能」分组里的「全面玻璃化」开关（`experimental_glass_enabled`，默认关闭）。
  关闭时所有组件走 `SolidPanel` 实色降级路径（纯色渐变 + 细描边），零离屏层、零 shader，
  观感与 v0.19.1 一致；打开才走 `drawBackdrop` 的折射玻璃。
  开关状态经 `GlassState` → `LocalGlassEnabled` 下发给全应用，切换即时生效不需重启
- **变更**：设置页原本九张卡片一条长列表，现在重排为六个可展开折叠分组——
  外观 / 数据同步 / 时长目标 / 数据安全 / 实验性功能 / 关于。
  头部显示图标、标题与收起时的摘要（比如主题名、玻璃开关状态），点击展开，箭头旋转 + 高度动画
- **新增**：卡片进场动画（`Modifier.finchEnter`）——淡入 + 上浮 + 缩放，同屏多张按 `order` 错开。
  记账用 `remember` 而非 `rememberSaveable`，滚动列表里新出现的卡片也会播
- **实现细节**：`GLASS_BLUR` 从 `false` 改为 `true`（只在开关打开时才跑 blur）；
  `GlassSurface` 的 `remember` 键里带上开关值与相关派生值，避免切换开关时复用旧的 shader 链

## 0.19.1 (2026-11-02)

同步写不进记录修复 ＋ 记录备注（DB 20→21）：

- **修复**：主界面同步提示「匹配更新 11 款」却「写入 0 条游玩记录」（例如 Phantom Blade Zero
  玩了一整天不显示）。两处叠加的根因都在 `SyncEngine.diffWriteSessions`：
  ① 遇到同起点的旧占位会话就 `continue` 把整天增量丢掉；② 结尾无条件把快照基线对齐官方累计，
  于是这次没写进去的增量从下一轮起永远算成 0。
  现在撞到占位就延长它的结束时间，并且只要份额没被分配器丢弃就计入「已落账」；
  基线只推进到 `prev.totalMin + writtenMin`，没写进去的余量下次同步继续补
- **新增**：每条游玩记录可以写备注（最多 120 字，最多折 2 行显示），记录页与详情页都能写能看。
  备注不参与任何统计口径
- **迁移**：`MIGRATION_20_21` 给 `play_sessions` 加 `note TEXT`（无 DEFAULT，老记录为 null）
- **单测**：DiffWriteSessionsTest 三例重写（撞占位延长而不丢增量 / 份额放不下时基线只推进已写入量 /
  只补差额不重复计）；MigrationTest 增 v20→21 专项（note 列存在且无默认值、可写可清、
  备注不影响时长口径）

## 0.19.0 (2026-11-02)

记录更细：会话标签 ＋ 时长目标 ＋ 标签分布（DB 19→20）：

- **新增**：每条游玩记录可以打标签——主线 / 支线 / 联机 / 多周目，或者自己填。
  在「记录」页点一条记录就能选，详情页的游玩记录同样可改
- **筛选**：记录页顶部多了一排标签胶囊，点一下只看这一档，再点和「全部」取消
- **统计**：总结页新增「标签分布」卡，按标签聚合本期时长与条数，未打标的归「未分类」；
  战报分享文本里也会带上本期主打的那个标签
- **时长目标**：设置页新增「时长目标」，可填每月/每年目标小时数（0 或留空 = 不设）。
  设了之后总结页 Hero 卡显示「目标 X · 还差 Y」与完成百分比
- **迁移**：`MIGRATION_19_20` 给 `play_sessions` 加 `tag TEXT`（无 DEFAULT）。
  老会话一律是 null = 未分类，不猜用户当年在跑哪条线，数据一条不动
- **单测**：新增 SessionTagTest（5 例：预设顺序 / 空白归一 / 裁剪 / 超长截断 / 预设稳定）；
  MigrationTest 增 v19→20 专项（tag 列存在且无默认值、老会话为 null、可写可清）；
  FakeSessionDao 补 updateTag 与标签聚合的空实现

## 0.18.8 (2026-10-30)

不再造假记录：本月时长归零修复 ＋ 底栏更透（DB 18→19）：

- **修复**：本月/今年时长归零、首页没有「本月」。v0.18.6 加 `estimated` 标记时用
  `UPDATE play_sessions SET estimated = 1 WHERE source IN ('STEAM','SWITCH','PS')`
  一刀切回标，把 PS/Steam **真玩**的会话也标成了推算，于是所有区间统计把它们全排除，
  只剩本机计时（TIMER）的记录——记录页不过滤所以还看得到，总结页/首页却是空的
- **变更**：彻底删掉「按官方累计差额摊历史会话」这套对账逻辑
  （`SyncEngine.reconcileGame` / `allocateHistorySessions` / 启动时的本地对账全部移除）。
  同步只按快照差分写**真实新增**的时长；首次同步只落基线快照、不写任何记录
- **迁移**：`MIGRATION_18_19` 删掉 `play_sessions.estimated` 列，并清掉
  `estimated = 1` 的推算行——记录页从此只有真实会话，不会再出现成片的假记录
- **新增**：详情页累计下方显示「本地记录 X · 差额 Y」，
  说明官方终身累计与本地记录本来就有差距（同步只记新增时长），不再靠造假补齐
- **底栏**：去掉 `vibrancy()`（会把背景整体提亮成磨砂白板）、`blur` 8dp→3dp、
  遮罩 alpha 暗 0.18→0.10 / 亮 0.26→0.16，配合内容层采样真正透出滚动卡片
- **单测**：删除 AllocateHistorySessionsTest；DiffWriteSessionsTest 重写为 6 例
  （首次同步不写记录 / 无增量不写 / 多日摊分守恒 / 负增量 / 撞占位不重复计）；
  MigrationTest 增 v18→19 专项（推算行被清、列已移除、真实行保留）

## 0.18.7 (2026-10-30)

底栏变成真正透明、能透出滚动内容的玻璃（无 DB 变更）：

- **修复**：根屏的玻璃背板（backdrop）一直只录了静态氛围光 `FinchAurora`，内容层从未进背板，
  底栏玻璃后面等于一张固定壁纸——再怎么压 alpha 也透不出滚到它身后的卡片。
  现在给内容层单独挂一个 `contentBackdrop`，底栏采样源改成
  `rememberCombinedBackdrop(backgroundBackdrop, contentBackdrop)`（氛围光 ＋ 内容层合成），
  底栏终于能透出真实的滚动内容
- **变更**：底栏遮罩 alpha 暗色 0.34→0.18、亮色 0.46→0.26，配合真实采样源才有透明观感
- **保留**：卡片玻璃仍只采氛围光（卡片本身在内容层里，若也采内容层会自采样递归）

## 0.18.6 (2026-10-30)

修正「本月时长几千小时」（DB 17→18）：

- **修复**：v0.18.3 起把云端终身累计按差额补成历史会话，却没地方区分「本机真玩的记录」与
  「推算出来的历史」，推算段还排在最近 60 天内 → 总结页「本月总时长/热力图/作息/Top 游戏」
  把整份终身累计算进当月。用户反馈本月几千小时即此原因
- **新增**：`play_sessions.estimated`（DB 18，`MIGRATION_17_18`）标记推算会话；
  本月/今年/热力图/按周几/按小时/平台分布/Top 游戏/玩过的游戏数等**区间统计一律排除**它，
  累计口径（主页卡片、详情页累计、HLTB 已玩、记录页合计）仍包含，维持「记录合计＝云同步累计」
- **变更**：推算的历史会话锚点改为**上个月月末之前**（不再落进当月），最多回溯 400 天
  （原来 60 天封顶，会让 1000+ 小时的游戏变成每天三十多小时），
  已有真实记录更早时仍贴着它往前排
- **迁移**：旧版本写下的云端来源会话（STEAM/SWITCH/PS）统一回标为 estimated，按钮不改数据、不删记录；
  本机计时/手动补录（TIMER/MANUAL）不受影响
- **单测**：AllocateHistorySessionsTest 增锚点与上限两条、MigrationTest 增 v17→18 专项、
  覆盖起始版本补到 v17

## 0.18.5 (2026-10-30)

游玩记录一定跟得上云端累计（无 DB 变更）：

- **修复**：新建游戏（Steam/PSN 首次同步建档）旧代码只写快照、不写游玩记录，导致「累计有 PSN 时间、记录页空白」。
  现在建档后同样按差额补历史会话（`SyncEngine.runSteam`/`runPSN` 的 created 分支改走 diffWriteSessions）
- **新增**：App 启动时做一次**本地对账**（不联网，`SyncEngine.reconcileRecordsLocally`，`FinchViewModel` init 调用）——
  凡有云官方累计的游戏，按「官方累计 − 本地记录合计」的差额补历史会话；云同步失败或没配也能补齐，且幂等无差额不写
- 对账抽成 `reconcileGame` 单一实现，同步内与本地启动共用同一口径

## 0.18.4 (2026-10-29)

通关进度不再被游玩时长自动标成通关（无 DB 变更）：

- **修复**：详情页「通关进度」卡在 `已玩 ≥ 主线参考` 时就直接显示标题「已通关」+「已通关，本参考已封顶 ✓」，
  哪怕用户从没勾过通关；v0.18.3 把「已玩」换成 PSN/Steam 官方累计后，很多游戏一同步就顶到参考，
  看起来像被自动判通关了。现在通关状态只认手动勾选（`Game.completed` / 状态「通关」），
  时长达标只表现为进度 100%，并加一行小字「已达主线参考时长，是否通关以你的勾选为准」
- 参考时长、进度百分比、支线/全收集参考行、手动改参考、HLTB 自动获取都不变

## 0.18.3 (2026-10-27)

统一 PS 游戏时长口径（无 DB 变更）：

- **修复**：PS 游戏的累计时长在两处各显示一个数——主页卡片按本地会话累计，详情页按 PSN 官方
  累计（且只在官方更大时才生效），用户反馈「两个时间互相矛盾」。新增单一口径
  `Game.effectivePlaytimeMin()`：有云平台官方累计（PS→PSN、PC→Steam，都有一律 PSN 优先）
  就以官方为准，本地会话只当「游玩记录」明细
- **变更**：主页卡片累计（含「按时长排序」）、详情页「累计 · PSN」、通关进度（HLTB）「已玩」、
  勾通关时的进度封顶，全部改用同一口径；标签固定显示来源（累计 · PSN / 累计 · Steam），
  不再按数值大小来回切换
- **变更**：游玩记录与同步时间对齐——每次同步算「官方累计 − 本地记录合计」的差额，
  差额补成历史会话（每天最多一条、最长 8 小时、最多回溯 60 天，排在既有记录之前），
  所以记录页合计 = 主页/详情页累计；首次同步、早于安装的历史都会补上，已同步过的游戏也会补
- **单测**：新增 PlaytimePolicyTest（PSN 优先、双云优先 PSN、零时长回退、无云回退）、
  AllocateHistorySessionsTest（摊分合计守恒/每日上限/锚点），DiffWriteSessionsTest 改按对账口径断言

## 0.18.2 (2026-10-26)

UI 精简版（无 DB 变更）：

- **移除**：总结页的 PSN 奖杯汇总卡（全库四围 + 有奖杯游戏数）——用户反馈只想在详情页看奖杯；
  一并删掉只服务于它的 `FinchViewModel.trophyTotals()` 与 `TrophyTotals` 数据类
- 保留：详情页奖杯卡（白金/金/银/铜 + 完成度）不变

## 0.18.1 (2026-10-26)

热修版（PS 奖杯同步修正，无 DB 变更）：

- **修复（真机实测）**：v0.17.0 的奖杯同步走 `trophyTitles` 概要接口 + npTitleId 归认，
  但实测该响应**不含 npTitleId**（0/8 条目），PS 行名字兜底也因「合集 vs 单作重制」对不上——
  线上实际一个奖杯都没写进库。改为 `psn-api` 文档的映射接口
  `/trophy/v1/users/{accountId}/titles/trophyTitles?npTitleIds=a,b,...`（实测每批最多 5 个，
  6 个即 400），用 `games.psnTitleId`（gamelist 的 titleId，如 `CUSA01399_00`）精确取奖杯集
- **变更**：多奖杯集（合集/多版本）四围求和、完成度按 已获得/总数 折算，单集直取接口 progress；
  批次失败自动退回逐个查，仍失败静默跳过（不影响时长同步）；每次同步最多查 100 个标题
  （20 批）控制请求预算
- **清理**：删除失效的名字/npTitleId 归认纯函数与对应单测；解析与聚合单测改用真机响应样本
- **联调**：npsso 真机全链路实测通过（授权 → gamelist v2 → 映射接口 → 多奖杯集解析）

## 0.18.0 (2026-10-20)

界面重构版（无 DB 变更，纯前端）：

- **设计系统**：新增 `ui/design`——间距/动效 tokens、连续曲率圆角（`com.kyant.shapes`）、
  玻璃层级与统一控件（按钮 / 筛选胶囊 / 分段控件 / 输入框 / 开关 / 进度条 / 星级 / FAB /
  卡片 / 页头 / 空状态 / 弹窗 / 底部弹层 / Snackbar）；屏幕不再直接用 Material 默认控件，
  圆角、字级、间距、按压动效全 App 一套口径
- **Liquid Glass 统一**：玻璃分 Chrome / Card / Tile / Hero 四档（Kyant0/backdrop 2.0 的
  vibrancy + blur + lens 配方）；卡片内部控件改用 InsetSurface 平面玻璃，避免「嵌套玻璃
  采到根背景」的破洞感；悬浮底栏颜色改为跟主题走（此前写死 iOS 蓝）
- **官方组件**：控件直接复用 AndroidLiquidGlass catalog——`LiquidButton`（主/危险操作，带按下形变、
  指针跟随高光）、`LiquidToggle`（设置开关）、`LiquidSlider`（总结页月/年快速拖动）、
  `LiquidBottomTabs`（底栏）；只把上游写死的 iOS 绿/蓝换成主题色，并补 `enabled` / 紧凑高度参数
- **氛围背景**：全屏静态三点光斑 + 垂直明暗，作为唯一玻璃采样层；三套主题各自有底色与
  光斑，滚动/动画时玻璃不闪；状态栏、导航栏图标跟随应用亮暗
- **逐屏重构**：主页（问候页头 / 本月 hero / 平台分段 / FlowRow 状态筛选 / 横卡 / 圆形 FAB）、
  详情页（封面沉浸 / 深色顶栏胶囊 / 计时控制台 / 统计格 / 奖杯 / HLTB / 状态评分 / 感想 / 记录）、
  记录页（按天分组 + 计时中实时走秒）、总结页（月年分段 + 期数卡 + 渐变 hero + 统一图表配色）、
  日程页（数据源胶囊 + 关注卡 + 分组列表）、设置页（外观 / 同步 / 补录 / 备份 / 更新分区卡片）
- **修复**：深色模式下未显式指定颜色的文字/图标是黑色、不可读（FinchTheme 补 `LocalContentColor`）；
  「X 天前」因 Int 溢出恒显示日期；记录页计时中时长不再只在重组时刷新
- **修复（用户反馈）**：底栏选中玻璃点击 tab 不跟随、只有按住拖拽才移动——根因是
  `remember(selectedTabIndex)` 在 lambda 身份变化时重建了 `MutableIntState`，而正在收集的
  `snapshotFlow` 仍盯着旧状态；改为按 `selectedIndex` 的值直接驱动 `animateToValue`，
  点击与拖拽都会移动。选中态同时改为常驻底色 + 轻折射 + 高光/投影，静止时也看得见；
  总结页游戏排行行距加大（排名 28dp / 封面 46dp / 行高 8dp）
- **性能**：玻璃绘制链锁成同一个 Modifier 实例（`drawBackdrop` 每次调用都会新建
  `ShapeProvider` 且按引用比较，卡片一重组就重建 shader 链、重录 5 个离屏层）；
  `GlassSurface` 不再为每张卡白建一个 `LayerBackdrop`；玻璃绘制里的渐变 Brush 从「每帧新建」
  改为常驻；记录页秒级走秒只重组进行中的那一行（原来整列表每秒一起重组）；
  玻璃默认不再叠 blur——采样源只有平滑的氛围背景，模糊前后几乎无差别，关掉可省一遍全表面
  像素运算（`GLASS_BLUR` 开关可恢复）
- **验证**：`:app:assembleDebug` 通过；Robolectric 无头渲染逐屏检查布局与字级
  （软件画布不支持 RuntimeShader，玻璃以纯色替身渲染，仅用于布局校验）

## 0.17.0 (2026-10-19)

功能版（PS 奖杯同步 + 小组件扩展，DB 16→17）：

- **新增（PS 奖杯同步）**：PSN 同步顺带拉奖杯概要（`trophy/v1 trophyTitles`，一次分页拉全量，
  不逐奖杯展开），归认口径 npTitleId（剥 `_NN` 后缀等价）优先、PS 行名字唯一兜底（宁漏勿错）；
  详情页新增奖杯卡（白金/金/银/铜 + 完成度条），总结页新增全库奖杯汇总卡（四围 + 有奖杯游戏数）；
  奖杯拉取失败静默跳过，不影响时长同步主流程。DB 16→17：`games` 加 6 个奖杯列
- **新增（小组件扩展）**：宽格小组件加「今日 X 分钟 · 本周 Y 分钟」与「月 Top1：游戏名」两行
  （周一口径，点击直达行为不变）
- **修复（升级崩溃，迁移测试恢复后抓出）**：v12 新装库缺 `index_release_follows_key` 唯一索引，
  v0.15.0 新装用户升级到后续版本过不了 Room schema 校验会启动崩溃——`MIGRATION_12_13` 补建
- **测试**：MigrationTest 改走原生 SQLite（`@SQLiteMode(NATIVE)`）修复开发机 sqlite4java
  解压写盘被拒的环境故障，迁移回归防线恢复；新增 7 个单测（奖杯解析 2、归认 5）

## 0.16.0 (2026-10-08)

功能版（数据安全收尾，无 DB 变更）：

- **新增（自动备份）**：每周自动把整库 zip 备份到应用私有目录（WorkManager 周期任务，
  保留最近 4 份，失败的半截 zip 即删）；导入页「备份与恢复」卡显示「上次自动备份时间」、
  开关可关（关了跑到了也跳过），并支持「从最近自动备份恢复」（带确认弹窗，
  覆盖全部记录后自动重启应用）。注意：自动备份随卸载清除，换机前请走「导出备份」存外部
- **新增（点查更新）**：导入页「版本与更新」卡，点「检查更新」才联网查一次 GitHub
  `releases/latest`（不后台轮询、不上传任何数据），有新版显示版本号并「去下载」跳浏览器；
  版本比较逐段数字（0.15.9 < 0.15.20），飞行模式下只报网络失败不崩
- **范围取舍**：CSV 导出不做——「导出备份」zip 已覆盖导出需求（用户拍板）
- **测试**：+7 单测（自动备份保留策略 2、版本号比较 3、Release 响应解析 2）；
  108 个单测 97 通过，MigrationTest 11 个仍受开发机 Robolectric+sqlite4java
  原生库解压写盘被拒阻塞（环境问题，与本改动无关），环境修复后补跑

## 0.15.20 (2026-10-08)

功能小修版（无 DB 变更）：

- **新增（用户反馈）**：PSN 导入卡加「退出账号」（与 Switch 卡同款入口）——
  已授权态下同步按钮旁一键登出，清 `psnRefreshToken` 与过期时间（`writeSecret("")` 即删条目，
  加密存储随清）、置未登录；登出走 `ImportViewModel.logoutPSN()` 唯一入口，UI 不直写状态
- **测试**：纯 UI/VM 入口新增，沿用现有单测口径（90 个通过；MigrationTest 11 个仍受
  开发机 Robolectric+sqlite4java 运行时故障阻塞，与本改动无关，环境修复后补跑）

## 0.15.19 (2026-10-08)

UI 修复版（无 DB 变更）：

- **修复（用户截图）**：详情页三格统计的「最近」显示完整日期（`2026-10-04`）时一行放不下、
  折行断字——统计格值文本改单行 + 自适应缩字号（`TextAutoSize.StepBased` 10–22sp，
  上限锁 titleLarge 本值：短值观感不变，长值如完整日期/`6066h 7m` 缩到单行放得下）
- **API 核验**：`autoSize`/`StepBased(minFontSize, maxFontSize, stepSize)` 形态以本地
  material3/foundation jar 的 javap + class 元数据核验为准，未凭记忆写签名
- **测试**：101 个单测 90 通过；MigrationTest 11 个因开发机 Robolectric+sqlite4java
  运行时故障（建表 SQL 报 SQLITE_CANTOPEN，已排除权限/临时目录/daemon 状态）暂无法执行，
  与本版纯 UI 改动无关（v0.15.18 发版时同套件全绿），环境修复后补跑

## 0.15.18 (2026-10-08)

功能版（Nlib 优先、移除机翻，无 DB 变更）：

- **变更（移除 MyMemory 机翻兜底）**：错译率高（「异度神剑」→ Divergent Sword）、时好时坏，
  污染查询词还可能误配——换不到官方英文线索时直接引导手动贴链接，不给错答案
- **增强（Nlib 官方条目，按文档 https://api.nlib.cc/nx/#game 实测后重写）**：
  - 一次 GET 拉全 `name/publisher/releaseDate/type/isDemo/icon`（实测 name 恒为官方英文名，lang 参数不影响）
  - **update 形态 TitleID 自动回退本体**：升级数据 TitleID（低 12 位 `0x800`）Nlib 库里 404、
    本体记录命中——部分同步游戏 Nlib 查空很可能就是这个原因（`baseTitleId` 清低 12 位重查）
  - `isDemo` 试玩版直接引导手动填（HLTB 不单独记试玩时长，不白跑搜索）
  - 官方英文名送搜前剥「Nintendo Switch 2 Edition」等平台尾巴（剥尾巴变体优先搜）
  - `icon` 顺手回填：库无封面时用 Nlib 官方图标
- **新增（游民星空游戏库反查，补 NS2 覆盖）**：中文名搜游民游戏库（so.gamersky.com 服务端直出，
  国内直连稳定）→ 相似词条（≥0.5 防译名误配）→ ① 词条 Steam appid 直查 `/steam/<appid>`（本体优先）
  ② 词条官方英文名（meta keywords 口径）送 HLTB 按名搜。**任天堂独占 / NS2 新作没有 Steam 版也能兜**
  （Nlib 数据源 TitleDB 无 NS2 游戏，这级是 NS2 中文名的主要英文名来源）
- **变更（eShop 联动）**：过滤 DLC 条目（`sctg=aoc`），DLC 标题英文段不再污染按名搜
- **测试**：Nlib 条目解析 / update→本体回退 / 试玩版标记 / 游民搜索与词条解析 / 无拉丁数据异常用例（101 个单测）

## 0.15.17 (2026-10-08)

热修复版（v0.15.16 直搜判定仍误判，无 DB 变更）：

- **修复（用户实测反例：Switch 2 升级版 / 带 1st·2nd 序数的名字）**：
  「有没有拉丁字母」判定对**尾巴/片段带拉丁词**的中日韩名仍误判为可直搜——
  如「ゼルダの伝説 …Nintendo Switch 2 Edition」「英雄伝説 空の軌跡 the 1st」，
  主体是日文直搜必 404，却又把英文名解析链跳过了。判定改为 **`isHltbSearchable`：
  剥掉平台/版本尾巴后，核心名必须是纯拉丁**才算可直搜；名字掺中日韩字一律走英文名解析链
- **变更**：Steam 中文反查 / Bangumi 原名 / MyMemory 机翻 / eShop 联动的**查询词统一用剥尾巴后的核心名**
  （带 "Nintendo Switch 2 Edition" 尾巴的全名去搜 Steam/Bangumi 都对不上正体；
  Bangumi 相似度匹配同理，避免被尾巴拉低到 0.5 门槛之下）
- **测试**：可直搜判定用例重写，固化「Switch 2 升级版」「空之轨迹 the 1st」「少女☆歌劇」三类回归（100 个单测）

## 0.15.16 (2026-10-08)

热修复版（v0.15.15 英文名解析链未触发，无 DB 变更）：

- **修复（用户实测：失败文案「试了」全是原文名、一个英文词都没有）**：
  带符号的中/日/韩文库名（如「少女☆歌劇 レヴュースタァライト」）被「纯 CJK」判定误判为可直搜——
  ☆/♪/～/™ 等符号不算 CJK，但也不是拉丁字母。后果：原名（及其变体）进查询词白搜，
  而 Steam 中文反查 / Bangumi 原名 / MyMemory 机翻三级的触发条件同样用该判定 → 整体被跳过，
  英文线索一个都产出不了。判定口径换成 **「有没有拉丁字母」**（`hasLatin`，HLTB 能否按名搜的真正标准）
- **修复（韩文名）**：机翻方向补韩文档（`ko`→`en`，此前韩文名按中文送翻出不来可用结果）；
  方向判定抽成纯函数 `sourceLang`（假名→ja、谚文→ko、其余 zh-CN，混排名按最具体先判）
- **变更**：`stripMarks` 扩到全部 Symbol 类符号（☆★♪ 等装饰符一并剥掉，匹配更稳）
- **测试**：判定/机翻方向用例更新，补「少女☆歌劇」符号混排回归用例（100 个单测）

## 0.15.15 (2026-10-07)

功能修复版（HLTB 非英文名查询，无 DB 变更）：

- **修复（Switch/中文库名查不到 HLTB）**：HLTB 是纯英文库（只认英文名），中文/日文库名按名搜必 404。
  自动获取新增「英文名解析链」逐级兜底（各自静默失败，单级 5s 预算）：
  - **Steam 中文反查（新）**：中文名命中 Steam 中文索引 → 拿 appid 直查中转 `/steam/<appid>`，
    完全绕开 HLTB 名字匹配（数字强制否决：「女神异闻录3」绝不写 5 代的三围，宁漏勿错）
  - **Bangumi 原名联动（新）**：中文库名取日/英原名（与库名相似度 ≥0.5 防系列误配）；
    拉丁原名直接搜，日文原名走 eShop 日区换英文段
  - **MyMemory 机翻兜底（新）**：中/日文名机翻成英文做最后候选（含假名走 ja→en，音译还原成功率高；
    错译只导致搜不到，候选仍要过相似度 ≥0.4 + 数字强制才写库）
  - **eShop 联动修复**：不再用纯中文名搜日区索引（必空、浪费一次请求），改用日文原名/拉丁名做查询词
  - **Nlib 增强**：TitleID 变体归一（带后缀如 `_002` 取前 16 位）；官方英文名剥商标符号（™®©）再匹配
- **隐私**：新来源只发游戏名（HTTPS），与 HLTB 中转同级，共用导入页联网开关；失败静默回手动填
- **测试**：补 9 个单测（TitleID 归一/商标剥离/CJK·假名判定/Steam 反查匹配与数字强制/机翻候选解析）

## 0.15.14 (2026-10-05)

重构版（无行为变化，接 v0.15.13 之后的清理节奏）：

- **重构**：GameDetailScreen 拆包——通关彩带（ConfettiOverlay）、HLTB 进度卡组（HltbSection：
  进度卡/空态行/获取块/状态机）移入 `ui/gamedetail/` 子包，主文件从 928 行瘦身至 ~590 行
- **重构**：会话编辑弹窗双份副本合并为共用 `SessionEditDialog`；SessionRow 归入同一组件文件
- **重构**：网络异常分类收敛为 `data/NetworkMessages.kt`（4 处类型判断样板 → 1 个纯函数，
  各处用户文案保持不变）；HLTB 获取块、会话时间输入框收敛为共享组件
- **测试**：全部单测零回归（纯 JVM 64 + Robolectric 解析 16 全绿）

## 0.15.13 (2026-10-05)

数据安全与测试加固版（DB 15→16）：

- **修复（数据正确性）**：
  - Android 13+ 从未请求 `POST_NOTIFICATIONS`，计时通知/发售提醒在通知栏不显示——启动时请求授权
  - Steam/PSN 快照差分丢时长：基线只推进实际入账份额、去重改精确起点，同日二次同步不再撞旧占位
    （旧版「基线已推进、会话被丢弃」会让增量永久丢失）；摊分份额起点钳到上次快照之后
  - PSN refresh_token 轮换后立即写回（旧 token 即刻失效），中途网络失败不再掉登录
  - TimerService 及时 `startForeground`（防 FGS 超时崩溃）；`startTimer` 查库结算孤儿会话
    （进程被杀残留的 `endTime=NULL` 会话不再漏计时长）
  - 发售提醒：无通知权限不再误标「已提醒」（提醒不丢，下次重试）；通知 ID 全 32 位哈希防互相覆盖
  - 备份：meta.json + 核心表 + 版本交叉校验（任意 SQLite 文件不再能顶掉 `finch.db`）；
    `VACUUM INTO` 一致性导出（旧 SQLite 回退 checkpoint+copy）；失败路径不再关死 DB；`restartApp` NPE
  - Switch 多台主机同日日报按 (date, appId) 求和（原去重丢弃会少算）；同名多行按平台优先认领
  - `pauseAccumMs` DDL 对齐：实体补 `defaultValue="0"`，DB 15→16 重建 `play_sessions`（升级装/新装一致）
  - HLTB `parseGameId` 带尾部 query 的 URL 取错 id（`/game/68151?foo=999` 取 999）——优先取 path 末段
  - PSN 分页 `nextOffset` 不前进死循环防护；Moon 时区头取系统时区（原写死 Asia/Shanghai）；
    统计 SQL 加 `MAX(0, …)` 防负时长；未来会话保时长钳制（原一律挪 24h）；LIKE 通配符转义
  - UI：主页 Flow 重组重建闪 0、两处永不取消的 `CoroutineScope` 泄漏、走秒每秒弹簧动画、
    永不播放的死动画/死参数、跨夜记录显示歧义、跨天倒计时陈旧值、`LaunchedEffect` key 误用等
- **体验与状态**：
  - `rememberSaveable` 全量：旋转/切 tab 不再丢搜索词、筛选、详情页位置、当前 tab
  - 返回栈：非 Home tab 按返回先回 Home tab；桌面小组件点击直达正在计时的游戏详情
  - OkHttp 全面收敛：共享 client + `callTimeout 30s`；网络调用 `runInterruptible` + `delay` 退避，
    协程取消即中断（原 `Thread.sleep` 吞中断）；4xx 不再无效重试；Gamersky 线程池泄漏改协程超时
  - 系统可见文本入 strings.xml（小组件/发售通知与渠道/分享面板）；无障碍描述补齐
  - Coil 全局配置（crossfade/内存磁盘缓存）+ 统一 `GameCover` 组件（占位/失败兜底）
  - `imePadding` 修键盘遮挡；底部留白统一常量；widget `SizeMode.Responsive` 自适应 +「今日」按日历日边界
- **安全**：
  - Steam Key / Switch session_token / PSN refresh_token 改 Android Keystore + AES-GCM 加密落盘，
    旧明文读取时惰性迁移；解密失败删坏值返回空（宁可重填不崩启动）
  - 签名口令优先环境变量 / `gradle.properties`（`FINCH_KEYSTORE_*`），明文 `keystore.properties` 降为兜底
- **测试**（新增 30+ 用例）：
  - 复刻单测全部改调生产函数（`shouldRename*`/`stripPlatformTails`/`latinSegment`/`snapRefForCompleted`/
    `followKey`/`progressFraction`），被测即生产口径
  - 快照差分口径单测（含「丢份额不丢时长」回归用例）、备份校验决策单测
  - Room 迁移测试（Robolectric）：v7→v16 逐版升级数据保留 + 15→16 重建专项 + 新装直开
  - JSON/HTML 解析测试（PSN gameList/Bangumi 日历/Switch TitleID/Gamersky 页面）
- **工程化**：
  - CI（GitHub Actions：单测 + lint + assemble）；lint 显式策略（错误阻断，完整 lint 不再只靠 lintVital）
  - version catalog（`gradle/libs.versions.toml`）统一依赖版本；移除死配置 `kotlin.android` 插件
  - wrapper 补官方 `distributionSha256Sum`（镜像分发也验真）+ `retries=3`
  - 重复代码收敛：会话编辑弹窗双份合并为共用件、HLTB 获取块/时间输入框/网络异常分类收敛为共享组件
  - README compileSdk 修正、ROADMAP 过期行清理与 DB 16 行、.gitignore/.gitattributes 补全
- **验证**：全部单测通过；`assembleDebug` / `assembleRelease`（R8）构建通过

## 0.15.12 (2026-10-05)

体验修复版（无 DB 变更）：

- **新增**：Switch TitleID→英文名联动（Nlib-API，免配置）——`switchAppId` 是 16 位
  TitleID 时一次 GET 拿官方英文名，直送中转搜；非法格式/未知/超时静默回退老链路；
  命中同时写回库名（复用 v0.15.9 回填），以后不再查
- **测试**：50 个单测全部通过（新增 Nlib 校验/解析 3 个单测）

体验修复版（无 DB 变更）：

- **修复**：eShop 联动产出日文/中文词污染查询——`searchTitles` 只返回拉丁英文段
  （`latinSegment`：括号前段无拉丁字母直接丢弃，如 `ゼルダの伝説` 不再返回）；
  删 Bangumi 中文名兜底（手机直连 `api.bgm.tv` 超时，且中文别名命中率≈0）
- **修复**：纯中文库名失败文案误导——无英文词时直说“无英文关键词”，
  不再列一串中文词让人干瞪眼
- **测试**：47 个单测全部通过（`latinSegment` 单测替换旧英文段单测）

体验修复版（无 DB 变更）：

- **修复**：中文库名（如 `异度神剑2 Nintendo Switch 2 Edition`）自动获取必失败——
  纯 CJK 查询词直送中转必 404，还浪费请求还污染失败文案。现纯中文名/变体直接跳过，
  只送英文词（eShop 英文段）；失败文案列出实际试过的关键词，不再只报数量
- **测试**：47 个单测全部通过

修复版（无 DB 变更）：

- **修复**：Switch 通关时长自动获取必失败——根因在客户端自己：`SwitchClient.moonHeaders`
  一直发 `X-Moon-App-Language: zh-CN`，而 Moon 的 `meta.title` 是按这两个 header 在服务端
  本地化的（不是账号语言），库名拿回来就是中文；HLTB 是纯英文库（`game_name` 只有拉丁字母，
  别名只收欧美版差异），中文名按名搜必然匹配不上。Steam 侧不受影响是因为它走
  `GET /steam/<appid>` 精确查，根本不经过名字匹配
- **变更**：语言头改 `en-GB`——只影响接口返回的标题语言，App UI 文案仍是中文；库名回到英文后，
  v0.15.7 的剥尾巴 + v0.15.8 的 eShop 英文段两条链路即可正常命中
- **新增**：老库英文名回填——`runSwitch` 里同 `switchAppId` 命中但标题不同时自动改名
  （只动 `name`/`coverUrl`，会话与统计不动）；下次 Switch 同步即全量转英文，
  转完点详情自动获取三围。同时 Steam 侧按 `appid` 认领官方改名，避免改名分裂统计
- **测试**：`GameRepositoryTest` 补改名口径单测，共 47 个单测全部通过
- **验证**：真机同步验证待做（装机后下拉刷新 Switch → 看库名是否转英文）

## 0.15.8 (2026-09-21)

体验修复版（无 DB 变更）：

- **修复**：Switch 基本搜不到——根因是库名是中文/日文（家长监护 title 按机器语言），
  HLTB 是英文库，中文直搜必 404。链路加 **eShop 英文名联动**：拿库名去日区 search.json
  搜，取括号前英文段（如 `Xenoblade2` / `Hollow Knight`）再送中转模糊匹配；
  链路：原名 → 剥尾巴 → eShop 英文段 → Bangumi 中文名兜底，多关键词轮询
- **测试**：HltbProxyTest 补 eShop 英文段单测，共 46 个单测

体验修复版（无 DB 变更）：

- **修复**：Switch 游戏搜不到——库名常带平台后缀（`Nintendo Switch 2 Edition` 等），
  按名搜前逐级剥尾巴（平台/版本/合集后缀）+ Bangumi 中文名兜底，多关键词轮询；
  失败文案带试过的关键词数
- **新增**：通关联动——勾通关/设为通关·全成就时，已玩 < 主线参考则参考钳到已玩，
  进度瞬间 100%（“已通关，本参考已封顶 ✓”）；玩超了不动；取消通关不恢复参考（手动改）
- **测试**：HltbProxyTest 补变体剥离/联动口径 2 个单测，共 45 个单测

功能版（HLTB 改走中转 API）：

- **变更**：自动获取不再直连 HLTB/Bangumi，改走 Crashdummy/HowLongToBeatApi 中转
  （Steam 游戏 `GET /steam/<appid>` 直查；非 Steam `POST /hltb/search` 按名搜，
  数字强制匹配 + 相似度 ≥0.4；双 base 容灾；命中写库三围，点开无数据自动抓一次）
- **新增**：导入页「联网获取通关时长」总开关（默认开；只发游戏名/appid，不发记录与密钥；
  关了就纯手动，一个包都不发）
- **隐私**：详情页注明第三方中转；失败静默回手动填
- 无 DB 变更（DB 15 不变）
- **测试**：新增 HltbProxyTest（映射/过滤/相似度 6 个单测），共 43 个单测全部通过

功能版（HLTB 写回 + 修获取不到）：

- **写回**：HLTB 自动获取（v0.15.3 那套详情直抓+三围+点开自动获取；IGDB 联动段剔除，不再依赖 Twitch）
- **修复**：之前怎么都获取不到——v0.15.3 的 id 来源是 Steam 商店页外链（实测 4 款全无）+ IGDB 联动
  （国内直连 Twitch 不通），两条全死。改走 **Bangumi 联动**：按名调 Bangumi 搜索（中英双名都试）
  → bgm.tv 条目页正则找 HLTB 外链（国内直连可用，免配置）；链路：手动贴 > Bangumi 联动
- **变更**：DB 14→15（加回 hltbExtraMin/hltb100Min；13→14 移除迁移保留空转）；schema 15.json 入库
- **测试**：写回 HltbTest（解析/id 5 个单测），共 37 个单测全部通过

移除版（0.15.x 可直接覆盖安装）：

- **移除**：IGDB（Client/鉴权/搜索链/导入页凭证卡/设置字段全删；国内直连 Twitch 不通，不可用）
- **移除**：HLTB 自动获取（搜索三步链/详情直抓/自动抓取文案全删；通关进度退回纯手动填主线参考时长）
- **变更**：DB 13→14（重建 games 表去掉 igdbId/hltbExtraMin/hltb100Min；12→13 加列迁移保留空转）；schema 14.json 入库
- **测试**：32 个单测全部通过（IGDB/HLTB 单测随代码删除）

功能版（HLTB 自动获取修好）：

- **修复**：点获取永远失败——Steam 商店页实测 4 款全无 HLTB 外链，主力 id 来源改走 **IGDB 联动**
  （按名搜 IGDB → 取 IGDB 网页 → 正则找 HLTB 外链）；链路：手动贴 > IGDB 联动 > Steam 页
- **新增**：点开详情卡片自动获取一次（无三围时触发；已有数据不再抓）；失败显示具体原因
  （IGDB 未配凭证 / 无匹配 / 抓取失败），附重试 + 手动贴链接/填小时兜底
- **测试**：IgdbHltbTest 补归一化单测，共 42 个单测全部通过
- 无 DB 变更（DB 13 不变）

热修复版（无 DB 版本变更，无需迁移）：

- **修复**：v0.15.0/0.15.1 启动闪退——`ReleaseFollow.key` 的唯一索引只在 `MIGRATION_11_12` 手写 SQL 里建了，
  `@Entity` 上漏了 `indices` 声明，Room 校验 Expected（无索引）vs Found（有索引）直接抛
  `Migration didn't properly handle`。补上 `@Entity(indices=[Index(key, unique)])` 后与迁移 SQL 一致
- **注意**：已装 0.15.x 且闪退的用户，装本版直接恢复（DB 版本号不变，无需迁移，数据不丢）

功能版（资料库换源 + HLTB 自动获取）：

- **变更**：TheGamesDB 下线，换 IGDB——「添加游戏」搜索链 Bangumi → IGDB → Steam；封面/平台/首发日走 IGDB，需自填 Twitch App 凭证（导入页「游戏资料库」卡片保存并测活）
- **新增**：HLTB 自动获取——详情页通关进度卡「从 HLTB 获取」（Steam 游戏先试商店页外链，否则贴 HLTB 链接/id），三围（主线/支线/全收集）一次写库；失败静默提示手动填
- **变更**：DB 12→13（games 加 hltbExtraMin/hltb100Min/igdbId）；schema 13.json 入库
- **测试**：新增 IgdbHltbTest（平台映射/结果映射/HLTB 解析/id 解析 9 个单测），共 41 个单测全部通过

功能版（日程变有用）：

- **新增**：发售关注——日程页每行 ☆/★ 关注，顶部「我的关注」置顶卡（倒计时：今天/明天/N 天后发售）
- **新增**：发售前推送——WorkManager 每天检查一次，发售前 3 天发本地通知（点通知进主界面；同日期只推一次；省电模式下可能延迟）
- **新增**：通关进度条——详情页「已玩 Xh / 主线约 Yh（Z%）」，参考时长手动填（清空隐藏）；超了显示加量不加价
- **变更**：DB 11→12（新表 release_follows；games 加 hltbMainMin）；加 `androidx.work:work-runtime-ktx`；schema 12.json 入库
- **测试**：新增 ReleaseCountdownTest（倒计时/去重键/百分比钳制 6 个单测），共 32 个单测全部通过

体验修复版（无 DB 变更）：

- **修复**：详情页返回后列表回到顶部——`LazyListState` 提升到详情切换容器外部，进出详情不再重建滚动位置；筛选/搜索/排序变化仍自动回顶部
- **移除**：列表项入场滑入动画（返回/筛选重组时反复触发又闪又干扰滚动，干掉）

移除版：

- **移除**：回本率（详情页价格卡片、Steam appdetails 查价、30 天价格缓存、价格分支单测全部删除）
- **变更**：DB 10→11（重建 games 表去掉 priceCny/priceFetchedAt；v9→v10 的加列迁移保留为空转，保证已升级用户能继续迁移）；schema 11.json 入库
- **测试**：26 个单测全部通过

功能版（总结可视化）：

- **新增**：热力图——月视图按周对齐每天一格，年视图 12 个月横滑，浅→深=玩得少→多
- **新增**：作息——周几玩得最多（周一开头横向条形）+ 24 小时高峰时段柱，峰值高亮
- **新增**：回本率——Steam 游戏详情页自动查一次国区价格（30 天有效），显示“N 元玩了 Xh，Y 元/小时，已回本 ✓”；免费游戏显示已回血；抓不到价格整卡隐藏
- **新增**：分享战报 v1——总结页「分享战报」把本期关键数字拼成文本走系统分享（图片模板 v0.17 再做）
- **变更**：DB 9→10（games 加 priceCny/priceFetchedAt）；schema 10.json 入库；时段聚合查询只加 SQL 不改表
- **测试**：新增 SteamPriceParseTest（价格分支 6 个单测），共 32 个单测全部通过

排版修复版（无 DB 变更）：

- **修复**：主页筛选区把排序挤成两行——排序收进标题行右侧（单行不换行），状态/平台各占一行独立横滑，芯片全部单行，行尾留白暗示可滑
- **修复**：FAB 被悬浮底栏胶囊压住——抬高到底栏上方，列表底部留白同步加深
- **修复**：详情页 5 个状态芯片在窄屏挤爆——改横滑

## 0.13.0 (2026-09-18)

功能版（库管与计时可信）：

- **新增**：游戏状态——想玩 / 在玩 / 搁置 / 通关 / 全成就（详情页状态卡一键切换，与旧「已通关」勾选双写兼容）；主页卡片显示状态
- **新增**：主页筛选＋排序——状态 / ★收藏 / 平台 FilterChip + 最近玩 / 总时长 / 评分 / 名称排序
- **新增**：计时暂停/继续——详情页暂停按钮 + 通知栏暂停/继续按钮；暂停段不计入时长（走秒/通知/小组件/统计全扣）；服务被杀恢复暂停态
- **新增**：会话可编辑——记录页/详情页点「编辑」改起止时间（跨夜自动+1天，结束必须晚于开始，进行中不可改）
- **变更**：DB 8→9（games 加 status/statusUpdatedAt，已通关回填 COMPLETED；play_sessions 加 pauseAccumMs/pauseStartedAt）；全部统计口径扣暂停；schema 9.json 入库
- **测试**：新增 GameStatusPauseTest（状态解析/暂停口径 6 个单测），共 26 个单测全部通过

重构版（无行为变更，纯结构与性能整理）：

- **拆分**：HomeScreen.kt 833 行拆为三个文件——HomeScreen（主页+月度大卡+游戏卡片）、AddGameDialog、SharedUi（StatBox/platformIcon/platformLabel/relativeTime 跨屏共享）
- **抽取**：SyncEngine 的 Steam/PSN 快照差分逻辑合并为 `diffWriteSessions` + `saveSnapshot`，消除约 50 行重复
- **抽取**：FinchViewModel 四个详情 setter 共用 `updateGame` 读写样板；ImportViewModel 全限定名改为 import
- **性能**：详情页空闲时计时协程不再每秒空转（未计时直接返回）
- **清理**：删除死代码 `addGame`、`GameCover`、`platformLabelStatic`、未用的 snapshotDao 字段与失效 import

## 0.12.1 (2026-09-17)

体验修复版（详情页反馈五连修）：

- **修复**：详情页「累计」优先展示 Steam / PSN 的官方总时长（标签带来源），不再只显示 Finch 自己记到的会话时长
- **修复**：点星星/勾通关不再整页切换——详情页转场改用 gameId 作 key，游戏数据更新不重放动画
- **修复**：底栏遮挡——详情页底部留白 130dp
- **修复**：状态栏沉浸——去掉全局 statusBarsPadding，详情页封面全出血 + 悬浮顶栏 + 渐变遮罩；主页/日程/记录/总结/导入各自处理 insets
- **新增**：勾选「已通关」瞬间触发彩带庆祝特效（Canvas 粒子，播完自动消失）

## 0.12.0 (2026-09-17)

功能版：

- **变更**：首个标签页「计时」改为「主页」——游戏列表照旧，点卡片进入新的游戏详情页
- **新增**：游戏详情页——收藏（心形）、已通关勾选（自动记通关日期）、5 星评分、感想文本框；计时器收进详情页放在走秒旁边（开玩/停止）；单游戏统计（累计/会话/最近）与游玩记录（可删）
- **新增**：主页游戏卡片显示「已通关」「★ 评分」徽章
- **变更**：DB 7→8（games 加 favorite/completed/completedAt/rating/thoughts）；schema 8.json 入库
- **清理**：删除旧专门计时页（GamePlayScreen）与未使用的 RunningBanner；文件改名 TimerScreen.kt → HomeScreen.kt

## 0.11.1 (2026-09-17)

修复版：

- **修复**：Android 16+ 计时 Live Update 不再生效——`POST_PROMOTED_NOTIFICATIONS` 在 QPR1 起是需要用户授权的运行时权限，此前从未请求过；现在点「开玩」时未授权会弹系统授权框（拒绝则退化为普通常驻通知）
- **清理**：移除界面与文档中的「小米超级岛」文案，改为中性的 Live Update 说明

## 0.11.0 (2026-09-16)

功能版：

- **新增**：PSN 导入——npsso 授权（约两个月有效，过期前卡片提示）→ gamelist 官方总时长（PS4/PS5）→ 快照差分写会话，与 Steam 同机制；协议按 PSNAWP（authz/v3 OAuth + gamelist/v2）逐项对齐；DB 6→7（games 加 psnTitleId/psnPlaytimeMin/psnSyncedAt）
- **新增**：桌面小组件（Glance）——正在玩的游戏 + 已玩时长 / 今日总时长，点击进主界面；计时开/停、整分走分、App 启动时推送刷新
- **新增**：Room schema 导出（`app/schemas/`，迁移测试与备份校验依据）
- **变更**：下拉刷新同时覆盖 Steam / Switch / PSN
- **测试**：新增 PsnClient 解析单测（playDuration / ISO 时间戳），共 20 个单测

## 0.10.9 (2026-09-16)

维护版（首个 git 纳管后的功能+维护混合版）：

- **新增**：数据备份与恢复——导入页底部「备份与恢复」卡片，导出游玩记录为 zip（不含密钥），从备份恢复会校验 schema 版本后整库替换并重启应用
- **新增**：release 自动签名（仓库根放 `keystore.properties` 即生效，文件不入库）
- **修复**：TimerService 主线程 `runBlocking` 查库的 ANR 隐患；无效 gameId 不调 `startForeground` 的系统超时崩溃隐患
- **修复**：Steam Key / Switch token 所在 SharedPreferences 排除出云备份与换机迁移
- **变更**：release 开启 R8 + 资源缩减（13.7MB → ~1.7MB）
- **变更**：SyncEngine 写入整体事务化；Steam 摊日逻辑抽为纯函数 `allocateSteamSessions` 并补单测；`SteamClient` 归并共享 okhttp（移除全局 keepAlive 副作用）
- **清理**：`BangumiClient` 重试恢复、UA 更新、过期 DoH 注释、死代码 `observeTotalForUtcDay`
- **构建**：Gradle Wrapper（9.7.1，腾讯镜像）、Gradle 配置缓存、显式 okhttp 依赖

## 0.10.8

git 纳管前的最后版本（基线提交 `2b813e6`）。
