# Finch

个人游戏时长记录 App（Android）。三大平台（PC / Switch / PS）统一记账，手动计时器开玩/停止，月度与年度总结。

## 功能（v0.10.9）

- **计时页**：游戏列表（按最近游玩排序，可添加，多平台 PC / Switch / PS 标记），点「开玩」开始计时、「停止」结束；长按游戏可删除
- **记录页**：最近 300 条会话（游戏、时间段、时长、进行中的实时走秒）
- **总结页**：月/年切换，总时长、环比上期、平台分布、每日柱状图、游戏 Top10、Steam 终身时长对账
- **即将发售**：Bangumi 发售日历 / Steam 即将推出 / eShop 多源聚合
- **数据同步**：
  - Steam Web API `GetOwnedGames`（快照差分写会话，历史时长不会误算成增量）
  - Switch 家长监护 Moon API（PKCE OAuth，逐日游玩记录，日粒度）
  - 启动自动同步（6 小时节流，可关），下拉刷新手动同步
- **超级岛适配**：Android 16 / HyperOS 3.1+ 上计时通知以 Live Update 形式上岛（`POST_PROMOTED_NOTIFICATIONS` + `setRequestPromotedOngoing`），低版本自动退化为普通常驻通知
- 会话数据落 Room 数据库（`play_sessions`），服务被杀后重启自动恢复计时
- 备份安全：Steam Key / Switch token 所在的 SharedPreferences 已排除出云备份与换机迁移（`res/xml/backup_rules.xml`、`data_extraction_rules.xml`）
- **备份与恢复**：导入页底部可把游玩记录导出为 zip（不含密钥），或从备份整库恢复（自动校验 schema 版本）

## 构建

工具链已装在 `E:\Android\tools\`（JDK17、Gradle 9.7.1、Android SDK），无需 Android Studio。

Gradle Wrapper 固定 9.7.1，`distributionUrl` 指向腾讯镜像；本地已有 `E:\Android\gradle-9.7.1-bin.zip`，离线时可解压到 `tools\` 使用。

Git Bash：

```bash
export JAVA_HOME='E:\Android\tools\jdk-17.0.20.1+1'
export GRADLE_USER_HOME='E:\Android\tools\gradle-home'
cd /e/Android/Finch
./gradlew :app:assembleDebug
```

PowerShell：

```powershell
$env:JAVA_HOME='E:\Android\tools\jdk-17.0.20.1+1'
$env:GRADLE_USER_HOME='E:\Android\tools\gradle-home'
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

仓库用 git 管理，发版打 tag（`v0.10.8` 对应基线 `2b813e6`）。提交身份当前是仓库本地配置（`cao / cao@localhost`），推送 GitHub 前请改成本人邮箱：

```bash
git config user.email "you@example.com"
```

## 路线图

- PSN 数据导入（PSNAWP / psn-api；PS5 有官方时长，PS4 轮询估算）
- 桌面小组件（正在玩的游戏 + 今日时长）
- 私有分发渠道（GitHub Releases 或自建静态页 + 应用内版本检查）
- 国际化（UI 字符串资源化，仅当准备公开分发时）

已实现：Steam 导入、Switch 导入、手动补录（导入页「手动补录」卡片）、Release 签名（v0.10.9）、备份与恢复（v0.10.9）。明确不做：启动自动同步。
