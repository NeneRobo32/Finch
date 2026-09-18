# Changelog

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
