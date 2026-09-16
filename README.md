# Finch

个人游戏时长记录 App（Android）。三大平台（PC / Switch / PS）统一记账，手动计时器开玩/停止，月度与年度总结。

## 功能（v0.1.0）

- **计时页**：游戏列表（可添加，选 PC / Switch / PS 平台），点「开玩」开始计时、「停止」结束；长按游戏可删除
- **记录页**：最近 300 条会话（游戏、时间段、时长、进行中的实时走秒）
- **总结页**：月/年切换，总时长+环比上期、平台分布、每日柱状图、游戏 Top10
- **超级岛适配**：Android 16 / HyperOS 3.1+ 上计时通知以 Live Update 形式上岛（`POST_PROMOTED_NOTIFICATIONS` + `setRequestPromotedOngoing`），低版本自动退化为普通常驻通知
- 会话数据落 Room 数据库（`play_sessions`），服务被杀后重启自动恢复计时

## 构建

工具链已装在 `E:\Android\tools\`（JDK17、Gradle 8.14.3、Android SDK），无需 Android Studio：

```bash
export JAVA_HOME='E:\Android\tools\jdk-17.0.20.1+1'
export GRADLE_USER_HOME='E:\Android\tools\gradle-home'
cd /e/Android/Finch
/e/Android/tools/gradle-8.14.3/bin/gradle :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

安装：`adb install app-debug.apk`，或直接把 APK 拷到手机安装。

## 路线图

- Steam 数据导入（官方 WebAPI `GetOwnedGames`）
- PSN 数据导入（PSNAWP / psn-api；PS5 有官方时长，PS4 轮询估算）
- Switch 数据导入（nxapi 拉家长监护 App 游玩记录，日粒度）
- 手动补录（历史会话）
- 桌面小组件
