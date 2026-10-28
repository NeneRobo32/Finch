# Finch

个人游戏时长记录 App（Android）。三大平台（PC / Switch / PS）统一记账，手动计时器开玩/暂停/停止，月度与年度总结。

## 功能（v0.18.0）

- **界面**：Liquid Glass 统一视觉——全屏静态氛围光背景 + 四档折射玻璃（底栏/卡片/小格/封面），
  连续曲率圆角与统一控件库；按钮/开关/滑杆/底栏直接复用 AndroidLiquidGlass catalog
  （`LiquidButton` / `LiquidToggle` / `LiquidSlider` / `LiquidBottomTabs`）；
  三套主题（琉璃玻璃 / 暮色玻璃 / 经典纸感）× 亮暗可切，状态栏图标跟随应用亮暗
- **主页**：游戏列表（状态/收藏/平台筛选，最近玩/总时长/评分/名称排序，多平台 PC / Switch / PS 标记），月度「玩得最多」大卡；点卡片进游戏详情页；卡片显示状态与累计时长（有云平台官方累计的游戏统一以官方为准：PS→PSN、PC→Steam，与详情页同一口径，本地会话只作游玩记录）
- **游戏详情页**：状态（想玩/在玩/搁置/通关/全成就）、收藏、已通关勾选（自动记通关日期，与状态双写）、5 星评分、感想、通关进度（主线/支线/全收集三段，点开自动从 HLTB 中转获取或手动填）；计时器放旁边（开玩/暂停/继续/停止 + 实时走秒，已扣暂停）；单游戏统计与游玩记录（可编辑、可删）；长按主页卡片删除游戏
- **记录页**：最近 300 条会话（游戏、时间段、时长，暂停段已扣；进行中/已暂停实时走秒；已完成可编辑起止）。每次同步会按「官方累计 − 记录合计」的差额补历史会话，所以记录合计与主页/详情页的累计一致
- **总结页**：月/年切换，总时长、环比上期、平台分布、每日柱状图、热力图、作息（周几/几点）、游戏 Top10、Steam 终身时长对账、「分享战报」
- **即将发售**：Bangumi 发售日历 / Steam 即将推出 / eShop 多源聚合；☆ 关注后置顶倒计时，发售前 3 天本地推送
- **游戏资料库**：「添加游戏」在线搜封面和平台：Bangumi（免Key）→ Steam 商店（免Key）
- **通关时长**：点开详情自动查 HLTB 中转服务（可在设置页一键关闭；数据来自第三方 Crashdummy/HowLongToBeatApi）。HLTB 只认英文名——中文/日文库名自动走英文名解析链换到官方英文线索再查（Nlib 官方条目（TitleID 直查，NS1）/ Steam 中文反查 / 游民星空游戏库反查（NS1/NS2 通吃）/ Bangumi 原名 / eShop 英文段）；换不到时引导手动贴链接（机翻已移除）；只发游戏名/appid，不发记录与密钥
- **数据同步**：
  - Steam Web API `GetOwnedGames`（快照差分写会话，历史时长不会误算成增量）
  - Switch 家长监护 Moon API（PKCE OAuth，逐日游玩记录，日粒度）
  - PSN（npsso 授权，gamelist 官方总时长 PS4/PS5，快照差分；顺带同步奖杯概要）。**时长口径**：PS 游戏的累计时长一律以 PSN 同步值为准（主页卡片、详情页「累计 · PSN」、通关进度「已玩」、通关联动共用 `Game.effectivePlaytimeMin()`），本地会话只是历史明细，不再出现两个互相矛盾的数
  - 启动手动同步（下拉刷新三源齐跑），可关
- **PS 奖杯**：PSN 同步按 `psnTitleId`（gamelist 的 titleId，如 `CUSA01399_00`）批量映射奖杯集（每批 5 个，接口 `/trophy/v1/users/me/titles/trophyTitles`），白金/金/银/铜已获得数 + 完成度（多奖杯集合集求和、按已获得/总数折算）；详情页奖杯卡；只发游戏/奖杯请求，不发记录与密钥
- **桌面小组件**：正在玩/已暂停的游戏 + 已玩时长（扣暂停）/ 今日+本周累计 / 本月 Top1，点击直达
- **Live Update 计时通知**：Android 16+ 开玩前请求 Live Update 授权（`POST_PROMOTED_NOTIFICATIONS` 运行时权限），计时通知可被系统提升为实时卡片（暂停/继续按钮随状态切换），低版本自动退化为普通常驻通知
- 会话数据落 Room 数据库（`play_sessions`，暂停累计 `pauseAccumMs`），服务被杀后重启自动恢复计时（含暂停态）
- 备份安全：Steam Key / Switch/PSN token 所在的 SharedPreferences 已排除出云备份与换机迁移（`res/xml/backup_rules.xml`、`data_extraction_rules.xml`）
- **备份与恢复**：设置页底部可把游玩记录导出为 zip（不含密钥），或从备份整库恢复（自动校验 schema 版本）；**自动备份**每周把整库 zip 落应用私有目录（保留最近 4 份，可一键恢复；随卸载清除，换机前请手动导出）
- **版本检查**：设置页「版本与更新」点「检查更新」才查一次 GitHub Releases（不后台轮询、不上传数据），有新版可跳浏览器下载

## 构建

需要 JDK 17 + Android SDK（`compileSdk 37`，`targetSdk 36`），无需 Android Studio。

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

构建耗时（4 核 / 12G 机器实测）：

- release 与测试分成两条命令跑：同一条命令里混 `assembleRelease` 和 `testDebugUnitTest` 会多编一遍 debug 变体，配置缓存也要各存一份
- 不要加 `--no-daemon`：单次 daemon 每次冷启动，JIT 与增量编译状态全部作废
- lint 单独跑：`.\gradlew.bat :app:lint`；release 构建已不带 lintVital（`checkReleaseBuilds = false`），完整 lint 由 CI 与这条命令承担

## 测试

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

## 版本管理

发版打 tag（`v0.10.8` 起为基线版本）。

## 开源协议

MIT，见 [LICENSE](./LICENSE)。界面中的玻璃配方与 LiquidButton / LiquidToggle / LiquidSlider /
LiquidBottomTabs 移植自 [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)
（Apache-2.0），已按 Finch 主题色适配。

## 路线图

- PSN 真机联调（代码已就绪，需提供 npsso token 实测，含奖杯接口形态核对）
- 记录粒度（会话标签/备注、时长目标、战报图片海报，见 [ROADMAP.md](./ROADMAP.md)）

已实现：Steam 导入、Switch 导入、PSN 导入（待联调）、手动补录（设置页「手动补录」卡片）、Release 签名（v0.10.9）、备份与恢复（v0.10.9）、桌面小组件（v0.11.0）、自动备份与应用内检查更新（v0.16.0）、PS 奖杯同步与小组件扩展（v0.17.0）、Liquid Glass 界面重构与设计系统（v0.18.0）。明确不做：启动自动同步、国际化。
