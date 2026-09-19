# Changelog

## 0.15.5 (2026-09-19)

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
