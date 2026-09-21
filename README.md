# Finch

个人游戏时长记录 App（Android）。三大平台（PC / Switch / PS）统一记账，手动计时器开玩/暂停/停止，月度与年度总结。

## 功能（v0.15.6）

- **主页**：游戏列表（状态/收藏/平台筛选，最近玩/总时长/评分/名称排序，多平台 PC / Switch / PS 标记），月度「玩得最多」大卡；点卡片进游戏详情页；卡片显示状态与累计时长
- **游戏详情页**：状态（想玩/在玩/搁置/通关/全成就）、收藏、已通关勾选（自动记通关日期，与状态双写）、5 星评分、感想、通关进度（主线/支线/全收集三段，点开自动从 HLTB 中转获取或手动填）；计时器放旁边（开玩/暂停/继续/停止 + 实时走秒，已扣暂停）；单游戏统计与游玩记录（可编辑、可删）；长按主页卡片删除游戏
- **记录页**：最近 300 条会话（游戏、时间段、时长，暂停段已扣；进行中/已暂停实时走秒；已完成可编辑起止）
- **总结页**：月/年切换，总时长、环比上期、平台分布、每日柱状图、热力图、作息（周几/几点）、游戏 Top10、Steam 终身时长对账、「分享战报」
- **即将发售**：Bangumi 发售日历 / Steam 即将推出 / eShop 多源聚合；☆ 关注后置顶倒计时，发售前 3 天本地推送
- **游戏资料库**：「添加游戏」在线搜封面和平台：Bangumi（免Key）→ Steam 商店（免Key）
- **通关时长**：点开详情自动查 HLTB 中转服务（只发游戏名/appid，可在导入页一键关闭；数据来自第三方 Crashdummy/HowLongToBeatApi）
- **数据同步**：
  - Steam Web API `GetOwnedGames`（快照差分写会话，历史时长不会误算成增量）
  - Switch 家长监护 Moon API（PKCE OAuth，逐日游玩记录，日粒度）
  - PSN（npsso 授权，gamelist 官方总时长 PS4/PS5，快照差分）
  - 启动手动同步（下拉刷新三源齐跑），可关
- **桌面小组件**：正在玩/已暂停的游戏 + 已玩时长（扣暂停）/ 今日总时长，点击直达
- **Live Update 计时通知**：Android 16+ 开玩前请求 Live Update 授权（`POST_PROMOTED_NOTIFICATIONS` 运行时权限），计时通知可被系统提升为实时卡片（暂停/继续按钮随状态切换），低版本自动退化为普通常驻通知
- 会话数据落 Room 数据库（`play_sessions`，暂停累计 `pauseAccumMs`），服务被杀后重启自动恢复计时（含暂停态）
- 备份安全：Steam Key / Switch/PSN token 所在的 SharedPreferences 已排除出云备份与换机迁移（`res/xml/backup_rules.xml`、`data_extraction_rules.xml`）
- **备份与恢复**：导入页底部可把游玩记录导出为 zip（不含密钥），或从备份整库恢复（自动校验 schema 版本）

## 构建

需要 JDK 17 + Android SDK（`compileSdk 36`），无需 Android Studio。

Git Bash：

```bash
export JAVA_HOME='<你的 JDK 17 路径>'
cd Finch
./gradlew :app:assembleDebug
```

PowerShell：

```powershell
$env:JAVA_HOME='<你的 JDK 17 路径>'
.\gradlew.bat :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`
安装：`adb install app-debug.apk`，或直接把 APK 拷到手机安装。

Release：`.\gradlew.bat :app:assembleRelease`（已开 R8 + 资源缩减）。仓库根放 `keystore.properties`（模板：storeFile / storePassword / keyAlias / keyPassword，文件已被 .gitignore 排除）即自动签名；没有则产出未签名 APK。

## 测试

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

## 版本管理

发版打 tag（`v0.10.8` 起为基线版本）。

## 开源协议

MIT，见 [LICENSE](./LICENSE)。

## 路线图

- PSN 真机联调（代码已就绪，需提供 npsso token 实测）
- 私有分发渠道（GitHub Releases 或自建静态页 + 应用内版本检查）

已实现：Steam 导入、Switch 导入、PSN 导入（待联调）、手动补录（导入页「手动补录」卡片）、Release 签名（v0.10.9）、备份与恢复（v0.10.9）、桌面小组件（v0.11.0）。明确不做：启动自动同步、国际化。
