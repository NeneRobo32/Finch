# Changelog

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
