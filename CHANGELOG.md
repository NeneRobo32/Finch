# Changelog

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
