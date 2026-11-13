# Finch UI 实现审查 —— Compose 性能 / 令牌落地 / 可维护性

| 项目 | 内容 |
|---|---|
| 应用 | Finch（`dev.cao.finch`）v0.29.5 |
| 审查范围 | `app/src/main/java/dev/cao/finch/ui/**`（58 个文件，15,005 行）+ `FinchAppScaffold` 调用链 |
| 审查日期 | 2026-11-13 |
| 审查方式 | 全量静态阅读（design / liquid / theme / 全部页面），非抽样；统计类结论附可复现命令 |
| 与前两轮的关系 | 不重复 `CODE_REVIEW_2026-11-12.md`（数据层/网络/安全）、`UI_REVIEW_2026-11-12.md`（视觉/信息架构）。本轮只谈**与 UI 强绑定的实现问题** |
| 运行环境 | Kotlin 2.3.10 / Compose BOM 2026.08.00 / AGP 9.3.2（**strong skipping 默认开启**），单模块 `:app` |

---

## 一、一句话总体判断

**这套实现的"设计系统意识"在个人项目里属于上游水平（令牌、四档玻璃、双路降级、按压三要素都有明文规范并真的落到了代码），但性能模型建立在两个错误假设上——"Card 档只用于大卡片"和"弹层里采不到背景"——而最危险的一处是 `Glass.kt` 的 `clip` 排在 `drawBackdrop` 之前：每张玻璃卡都在为一个永远画不出来的 18dp 阴影付 BlurMaskFilter 的钱，同时又在主页列表里为一行纯文字卡导出一张没人消费的离屏层。**

成熟度评分（10 分制，仅代表实现质量，不含视觉）：

| 维度 | 分 | 依据 |
|---|---|---|
| 设计令牌落地 | 7.5 | `FinchSpace`/`FinchMotion`/`FinchShapes` 覆盖主干；226 处 `.dp` 里 45 处是可收敛的间距字面量 |
| 重组时机 | 6.5 | 秒级状态已按行隔离（HistoryScreen:128-130 做得对）；但列表项 key/lambda/内联 List 仍有 3 处系统性浪费 |
| 绘制成本 | 5.0 | 玻璃链路 3 处结构性浪费（clip 顺序 / 无人消费的导出层 / 弹层跨窗口采样） |
| 组件复用 | 8.0 | `Containers`+`Controls` 覆盖率高，页面基本只做组合；`StatsCards.kt` 是唯一例外 |
| 可维护性 | 6.0 | `StatsCards.kt` 744 行 7 个卡片混居；`UpcomingScreen` 659 行含取数分支 |
| 可测试性 | 2.0 | **0 个 `@Preview`、0 个 `androidTest`**，UI 层完全无回归护栏 |

---

## 二、令牌落地数据表

统计口径（全部可复现）：

```bash
cd app/src/main/java/dev/cao/finch/ui
grep -rnoE "[0-9]+(\.[0-9]+)?\.dp"   --include=*.kt . | wc -l    # 226
grep -rno  "Color(0x"                --include=*.kt . | wc -l    # 209
grep -rno  "spring("                 --include=*.kt . | wc -l    # 16
grep -rno  "tween("                  --include=*.kt . | wc -l    # 28
grep -rnoE "[0-9]+(\.[0-9]+)?\.sp"   --include=*.kt . | wc -l    # 45（其中 42 在 theme/Type.kt，属定义本体）
```

按文件聚合（Top 榜，`—` 表示 0）：

| # | 文件 | `.dp` | `Color(0x` | `spring(`/`tween(` | 判定 | 建议 |
|---|---|---|---|---|---|---|
| 1 | `theme/AppThemes.kt` | 0 | **161** | — | **白名单**（主题定义本体，三套 palettes） | 不动。唯一要求：新增主题必须走这里 |
| 2 | `design/Controls.kt` | **41** | 1 | — | 混合 | **14 处应收敛**：`Spacer(width(8.dp))`×3（:156/158/159）、`padding(h=14,v=12)`×2（:312/321）、`padding(h=20,v=13)`（:203）、`padding(h=14,v=10)`（:213）、`padding(h=8,v=3)`（:571）。余下 27 处为图标/星标/进度条等内容自身尺寸，白名单 |
| 3 | `StatsCards.kt` | **36** | 4 | 1 / 3 | **技术债最重** | 4 个 `Color(0xFF…)`（:70-73 金银铜+中性）应提为 `FinchColorScheme.rank*` 并写进规范白名单；`RoundedCornerShape(6/3/2.dp)`（:439/493/635）是规范点名的 3 处存量；`fontSize = 12.sp`（:348）违反"字号只引用 typography" |
| 4 | `design/Containers.kt` | **21** | 0 | — / 2 | 组件层 | `Spacer(height(2.dp))`（:166）、`height(3.dp)`（:237）、`width(40)/height(4)`（:503-504）应为 `FinchSpace.xxs` / 新 `handleWidth` 令牌 |
| 5 | `HomeCards.kt` | **20** | 2 | — | 页面级 | 2 处 `Color(0xFFE5484D)`（:268 计时红点）、`Color(0xFFF5B301)`（:337 星色）应为 `FinchColorScheme` 语义色（recordingDot / ratingStar） |
| 6 | `liquid/LiquidBottomTabs.kt` | **19** | 2 | 1 / — | **豁免**（§2.2 明确放行） | 但 `:63-64` 的默认参数 `Color(0xFF0088FF)` / `Color(0xFFFAFAFA)` 是**死默认值**——调用方 `FinchBottomBar.kt:71-74` 永远覆盖。删默认值改必填，否则下一个人会以为它有用 |
| 7 | `design/Tokens.kt` | 15 | 0 | 6 / — | **白名单**（令牌本体） | 不动 |
| 8 | `theme/Shapes.kt` | 13 | 0 | — | **白名单**（令牌本体） | 不动 |
| 9 | `liquid/LiquidToggle.kt` | 10 | 2 | — | 豁免 | `:168` `blur(8.dp * (1-progress))` |
| 10 | `GameDetailCards.kt` | 10 | 5 | — | 页面级 | `RoundedCornerShape(bottomStart=30.dp, bottomEnd=30.dp)`（:79）应为 `FinchShapes.sheetTop`（同 Shapes.kt:30 已有一份） |
| 11 | `report/BacklogFunnelCard.kt` | 9 | — | — | 页面级 | `RoundedCornerShape(8.dp)`（:167）存量 |
| 12 | `liquid/LiquidSlider.kt` | 9 | 2 | — | 豁免 | — |
| 13 | `liquid/LiquidButton.kt` | 8 | — | — | 豁免 | — |
| 14 | `design/Glass.kt` | 7 | — | — | **冻结区** | §3.1 明文冻结，不动 |
| 15 | `UpcomingScreen.kt` | 7 | — | — / 3 | 页面级 | 3 处 `tween()` 无；7 处 `.dp` 基本是 `15.dp` 角标（白名单） |
| 16 | `HomeScreen.kt` | 5 | — | — / **5** | 页面级 | 5 处 `tween()` 在 `AnimatedContent` transitionSpec（:195-206），转场动效应从 `FinchMotion` 出；`FinchMotion` 缺 `exit`/`fade` 档 |
| 其余 42 个文件 | 15 | 25 | 9 / 14 | — | 分散，单文件 ≤5 |

**汇总**：`.dp` 226 处 → **45 处是"该变令牌"的间距类**（已用 `grep -rnoE "Spacer\(Modifier\.(width|height)\([0-9.]+\.dp\)|spacedBy\([0-9.]+\.dp|padding\([a-zA-Z]* = [0-9.]+\.dp)"` 精确计数），96 处是尺寸类（内容自身，白名单），14 处是 1dp 描边（白名单）。
`Color(0x` 209 处 → 161 在主题定义、48 散落 UI，其中 **应入令牌的 13 处**（其余是 `Color.Black.copy(alpha)` 这类中性遮罩，合法）。
`spring(` 16 处 → 6 在 `FinchMotion` 本体，5 在 `DampedDragAnimation`（豁免），**5 处应改调用令牌**（`LiquidBottomTabs.kt:115` 的 `spring(1f,300f,0.5f)`、`StatsCards.kt:118`、`GameDetailScreen.kt`）。
`tween(` 28 处 → **全部无令牌**，其中 10 处（`HomeScreen` 5 / `UpcomingScreen` 3 / `Containers` 2）应加 `FinchMotion.fadeOut()` 一档。

> **结论**：令牌覆盖率（按"该不该出字面量"计）约 **82%**，已经高于多数商业项目。真正的问题不是"还有字面量"，而是 **`FinchMotion` 缺 fade/exit 档，导致转场动效 100% 靠字面量**——这是令牌体系本身的一个缺口，不是使用者的疏忽。

---

## 三、问题清单

严重度口径：**P0** = 掉帧 / 崩溃 / 明显 bug；**P1** = 技术债（可量化但用户无感）；**P2** = 打磨。

| 编号 | 标题 | 严重度 | 证据（文件:行） | 后果 | 具体改法（代码级） |
|---|---|---|---|---|---|
| **A1** | 列表行用 `Card` 档，每行导出一张**无人消费**的离屏层 | **P0** | `HomeCards.kt:224-231`；判定逻辑 `Glass.kt:147-150` | 主页首屏 8~10 张卡，每张多一次整卡离屏重绘（`rememberLayerBackdrop` + `exportedBackdrop`）。`GameRowCard` 的子控件是 Text / Icon / `FinchIconButton(ghost=true)`（走 `IconButton`，无玻璃）/ `FinchDropdownMenu`（独立 Popup）——**没有任何一个读 `LocalFinchBackdrop`**，导出 100% 浪费。`Glass.kt:144-146` 的注释假设"Card 只出现在大卡片上"，被列表行打破 | `HomeCards.kt:228` 改 `style = GlassStyle.Tile`（与 `HistoryScreen.kt:175`、`UpcomingScreen.kt:346`、`WishlistPane.kt:193` 一致）。若要保留 Card 的浑浊度，给 `GlassSurface` 加 `exportBackdrop: Boolean = style != Tile` 参数，列表行显式传 `false` |
| **A2** | `clip` 排在 `drawBackdrop` 之前，玻璃阴影被自己裁掉 | **P0** | `Glass.kt:203-219`（`clip` → `finchClickable` → `glassModifier` → `border`） | `drawBackdrop` 的 `shadow = Shadow.Default.copy(radius = 18.dp, …)`（`Glass.kt:166-168`）是画在形状**外部**的；外层 `graphicsLayer(clip=true)` 会把越界部分裁掉 → 阴影不可见。**但每一帧仍在跑 `BlurMaskFilter`**（`Glass.kt:172` 的注释自己写了"shadow / highlight 的 BlurMaskFilter 也在其中"）。每张卡一次无效模糊 | 把 `clip` 移到 `drawBackdrop` 之后，或直接删掉 `clip`（`drawBackdrop` 的 `shape` 参数已负责裁剪）：<br>`val boxModifier = modifier.then(clickable…).then(glassModifier).border(border, shape)`<br>顺序参照本仓库**已经做对的** `Containers.kt:391-392`（`shadow` → `clip`）。改完必须真机看一眼：如果阴影出现，说明原来确实是 bug；如果视觉零变化，说明 `drawBackdrop` 内部自己裁，那这层 `clip` 本来就该删 |
| **A3** | `FinchSheet` / `FinchDialog` 未把 `LocalFinchBackdrop` 置空，弹层内控件跨窗口采样 | **P0** | 下发点唯一：`FinchAppScaffold.kt:93`；消费者 `Glass.kt:112`、`Controls.kt:175/464/486`、`Controls.kt:102`（`InsetSurface`→`GlassSurface`） | `ModalBottomSheet` / `Dialog` 是独立窗口，采不到 App 背景——`UI_DESIGN_GUIDE.md` §3.1 明文写了这条禁令，但**代码没有兑现**。受影响面很大：`HomeFilterSheet` 的 `FinchChip`×6（`HomeScreen.kt:564`）、`PlatformPickerSheet` 的 chip + 「加入游戏库」主按钮（`UpcomingScreen.kt:621/637`）、`StatsPeriodSheet` 的 12+20 个 chip（`StatsScreen.kt:362/379`）、所有 `FinchConfirmDialog` 的确认按钮（`Containers.kt:433`，走 `LiquidButton`）。观感是按钮底下透出**主窗口的历史帧快照**，部分 ROM 上是黑边 | 在 `FinchSheet` / `FinchDialog` 的 content 外包一层：<br>```kotlin<br>CompositionLocalProvider(LocalFinchBackdrop provides null, LocalGlassEnabled provides false) { … }<br>```<br>`GlassSurface` 已经在 `backdrop == null` 时走 `rememberOwnBackdrop()`（`Glass.kt:137`），但弹层里连这一层都不该建——直接逼到 `SolidPanel` 才是文档的设计意图。注意 `LocalGlassEnabled provides false` 会让 `InsetSurface` 走实色分支（`Controls.kt:113-127`），这条分支是现成的、对比度已验证 |
| **A4** | 年视图热力图每次重组执行 ~365 次 `String.format` | **P0** | `StatsCards.kt:482-485`（12 个月 × 每月天数），月视图 `:423-426`（31 次） | `"%04d-%02d-%02d".format(...)` 走 `String.format(Locale.getDefault(), …)`，单次约 3~6µs。年视图 ≈ **1.1~2.2ms，纯主线程**，且**没有任何 `remember`**——翻期动画、切 tab、`total` 变化都会重跑。加上 `daily.associate{}`（:417）重建一次 Map | ① `remember(daily, anchorMonth, mode)` 包住整个 `byDay` + cells 计算；② 更彻底：日 key 不用 String，用 `Int`（`year*10000+month*100+day`），或让 `DailyTotal.day` 直接带 `LocalDate`。③ `heatColor` 里的 `MaterialTheme.colorScheme.primary` 提到顶层一次，`heatColor` 改成普通函数（见 B4） |
| **A5** | 切 tab 丢失全部页面 UI 状态（无 `SaveableStateHolder`） | **P0** | `FinchAppScaffold.kt:115-144`（`AnimatedContent` 直接 `when(t)`）；受害点 `StatsScreen.kt:105-106,155`（`mode`/`anchorMonth`/`weekStart` 全是 `remember` 而非 `rememberSaveable`）、`UpcomingScreen.kt:100-101`（`query`、`platformPickerFor`）、`HistoryScreen` 滚动位置 | 「总结页翻到 9 月 → 去主页看一眼 → 切回总结」→ 回到当月。用户会认为是 bug 而不是特性。同理 `UpcomingScreen` 的搜索词、`HomeScreen` 的 LazyColumn 滚动位置 | ① 脚手架加 `val holder = rememberSaveableStateHolder()`，`AnimatedContent` 内容包 `holder.SaveableStateProvider(t) { … }`（`t` 是 enum，可 Serializable 进 Bundle）；② 顺手把 `StatsScreen.kt:105-106/155` 三个 `remember` 改 `rememberSaveable`（`YearMonth` 不可直接存，用 `SavedStateHandle` 或存 `"2026-09"` 字符串）。风险：`AnimatedContent` 转场期间新旧两个 key 同时存在，`SaveableStateProvider` 支持但需实测一次 |
| **B1** | `finchEnter()` 每张卡 3 个 `Animatable` + 一个 alpha 离屏层 | P1 | `Reveal.kt:56-77`；调用点 `HomeCards.kt:227`、`UpcomingScreen.kt:345`、`HistoryScreen.kt:174`、`Containers.kt:100` | `graphicsLayer { alpha = … }` 中 `alpha < 1` 强制整卡走离屏合成；一屏 10 张卡 = 10 个额外 layer，且入场 400ms 内每帧重录。叠加 A1 的导出层，单卡峰值 layer 数 = **alpha 层 1 + 导出层 1 + drawBackdrop 内部 5（shadow/highlight 各一）**。注释说"动画全部落在 graphicsLayer 上，不触发重新测量"——这句对，但没说"每次都要开一张新离屏层" | 入场结束（`shown == true` 且动画收敛）后把 `alpha` 归 1、`scaleX/Y` 归 1，让 Compose 可以回收 layer：<br>```kotlin<br>this.graphicsLayer {<br>    alpha = alphaAnim<br>    translationY = offsetAnim<br>    scaleX = scaleAnim; scaleY = scaleAnim<br>}<br>```<br>→ 改为动画结束即摘掉整个 modifier（用一个 `var done by remember` 在动画 callback 里置位，`if (done) this else graphicsLayer{…}`）。收益：稳态滚动时列表项 **0 个** alpha 层 |
| **B2** | `finchClickable` 用 `composed {}`，每个可点项一个子组合 + 一次动画 | P1 | `Tokens.kt:84-128`；15 个调用点，其中列表内使用：`HomeCards.kt:231`（经 `GlassSurface`）、`Containers.kt:267`（`FinchListRow`）、`Controls.kt:309/608` | `Modifier.composed` 每次使用都建一个独立组合节点（`Tokens.kt:92`），在 `LazyColumn` 里等于**每个 item 多一层 combo**；配合 `collectIsPressedAsState` + `animateFloatAsState`（:94-99），一屏 10 项 = 10 个 `MutableInteractionSource` + 10 个 `Float` 动画。且 `graphicsLayer{}` 无条件挂载（:101-104），稳态也各占一个 RenderNode | 迁到 `Modifier.Node`（`androidx.compose.ui.Modifier.Node` + `ModifierNodeElement`），把 `MutableInteractionSource` 和 `Animatable` 收进 Node 里，`alpha` 为 1 时不设 `scale`：<br>```kotlin<br>private class FinchClickableNode(...) : Modifier.Node(), … { val src = MutableInteractionSource(); val scale = Animatable(1f) }<br>```<br>改动量集中在 `Tokens.kt` 一个文件，**所有 15 个调用点零改动**。这也是 Compose 官方对 `composed` 的明确建议（`composed` 已被标注为"prefer Modifier.Node"）。收益主要体现在长列表的首次组合时间 |
| **B3** | `GameCover` 每次重组新建 `ImageRequest` | P1 | `SharedUi.kt:57-63` | `ImageRequest.Builder(context).data(url).size(requestSize).build()` 未 `remember`。主页 8 张缩略图，任何一次列表重组 → 8 次 Builder 分配 + Coil 内部 request `equals` 比对（`target`/`listener` 每次都不同，比对必然不等）→ 触发重新 resolve/可能重新解码。滚动时叠加 | ```kotlin<br>val request = remember(url, requestSize, context) {<br>    if (requestSize != null) ImageRequest.Builder(context).data(url).size(requestSize).build() else null<br>}<br>AsyncImage(model = request ?: url, …)<br>``` |
| **B4** | `heatColor` 是 `@Composable`，热力图每格一次 restart group | P1 | `StatsCards.kt:506-517`；调用 `:440/454/494` | 月视图 42 格、年视图 12×~37 ≈ **450 格**，每格一次 composable 调用 = 450 个 restart group + 450 次 `MaterialTheme.colorScheme` 读取。改期时整片重建 | 改成普通函数，颜色从参数进：<br>```kotlin<br>private fun heatColor(frac: Float, blank: Boolean, primary: Color, tertiary: Color, surface: Color): Color<br>```<br>调用点先 `val scheme = MaterialTheme.colorScheme` 再传值。省 450 个 group，视觉零变化 |
| **B5** | `TopGamesCard` 在循环里反复 `topGames.take(10)` | P1 | `StatsCards.kt:332` + `:404` | `topGames.take(10)` 每次调用分配一个新 `ArrayList`；`forEachIndexed` 内每次迭代再算一遍 = **10 次额外分配 / 次重组**（`take` 是 O(k) 复制）。在 `LazyColumn` 的 item 里，任何父级状态变化都会重跑 | `val shown = remember(topGames) { topGames.take(10) }`，循环用 `shown.forEachIndexed`，条件用 `i != shown.lastIndex` |
| **B6** | `UpcomingScreen` 在 `for` 循环里调 `item()`，且 key 靠字符串拼接 | P1 | `UpcomingScreen.kt:269-295`；key 构造 `:280` | 300 条日程 = 每次重组 300 次 `item()` 注册 + 300 次三段字符串拼接（`"entry_" + source + ":" + (bangumiId ?: steamAppId ?: productId ?: name)`）。`LazyColumn` 的 content lambda 无法跳过，滚动/任何 state 变化都重跑。更实际的隐患：key 依赖 `?:` 回退链，某次刷新里 `productId` 从 null 变有值 → key 变了 → item 状态（如展开态）丢失 | ① 换成 `items(entries, key = { it.stableKey })`，`stableKey` 作为 `UpcomingEntry` 的 `val` 在构造时算一次（`UpcomingViewModel` 侧）；② 或者给 `UpcomingEntry` 加 `val id: Long`（构造时递增），key 直接用 id——最稳 |
| **B7** | 主页搜索框每输入一个字符都 `scrollToItem(0)` | P1 | `HomeScreen.kt:166-168`（`LaunchedEffect(… query)` 里有 `listState.scrollToItem(0)`） | 边打字边把列表拉回顶部。用户输到第 3 个字时看到列表反复跳。而且 `query` 变化本身会触发 `shown` 重算（`:312`），再叠加一次滚动，输入法联想期间会连续触发 | `query` 不该在 key 里。改成只对"筛选条件"重置滚动：`LaunchedEffect(statusFilter, favOnly, platformFilter, sortMode)`；如果确实想在搜索时回到顶部，用 `derivedStateOf` 判断"从有结果变为无结果"再滚 |
| **B8** | `PlatformPickerSheet` / `StatsPeriodSheet` 用 `FlowRow` 铺 12~32 个玻璃 chip | P1 | `StatsScreen.kt:357-386`（20 个年份 + 12 个月）、`UpcomingScreen.kt:619-629` | 每个未选中 chip 走 `InsetSurface` → `GlassSurface(Tile)` → `drawBackdrop`（`Controls.kt:316-322`）。**32 个 chip = 32 次 drawBackdrop surface pass**，且都在 `ModalBottomSheet` 里（叠加 A3 的跨窗口采样）。打开期数选择时会有可感的掉帧 | ① 修完 A3 后这批 chip 会退化成实色，成本直接归零（这是 A3 的附加收益）；② 弹层里本来就该用 `InsetSurface` 的 solid 分支——可以在 `FinchSheet` 内部统一 `LocalGlassEnabled provides false`（同 A3 建议） |
| **C1** | `UpcomingScreen` 的 `buckets` 用 `remember(filtered)` 捕获了过期的 `now` | P1 | `UpcomingScreen.kt:221-232` | `val now = LocalDate.now()` 在组合作用域，`remember(filtered)` 的 block 只在 `filtered` 变化时执行 → **跨过午夜后分组永远停在旧日期**（"3天后"的卡片继续留在"本年度待发售"）。且 `LocalDate.now()` 是组合期读取非快照源，本身不可响应 | 把 `now` 变成"天"粒度的状态：<br>```kotlin<br>val today by produceState(initialValue = LocalDate.now()) {<br>    while (true) { value = LocalDate.now(); delay(60_000) }<br>}<br>val buckets = remember(filtered, today) { … }<br>```<br>或者更省：在 `UpcomingViewModel` 里暴露 `todayFlow`（`MutableStateFlow<LocalDate>`，每分钟检查一次） |
| **C2** | 组合期读挂钟：`System.currentTimeMillis()` / `LocalDate.now()` 参与渲染决策 | P2 | `HomeCards.kt:323`（7 天内强调色）、`UpcomingScreen.kt:222/541`、`SharedUi.kt:126`（`relativeTime`） | 卡片过了 7 天不会自己变色，`relativeTime` 的"3 分钟前"在页面停留 5 分钟后仍是"3 分钟前"。属"看起来对但永远慢一拍" | 列表页统一注入一个 `nowProvider: () -> Long` 参数（Compose test 也能用）；或接受现状但在注释里写明"仅在重组时求值"。**不建议**为此加秒级状态（重蹈 HistoryScreen 的坑） |
| **C3** | `FinchSegmented(options: List<T>)` + 调用点内联 `listOf()` → 永不 skip | P1 | `HomeScreen.kt:442-452`（`listOf(null, PC, SWITCH, PS)` 每次重组新建）；`WishlistPane.kt:110`（`WishFilter.entries.toList()` 同） | strong skipping 下不稳定参数靠**实例相等**判定；内联 `listOf` 每次新实例 → `FinchSegmented` 及其 `BoxWithConstraints` + `InsetSurface` 每次都重组。主页 `query` 每敲一个字都会重跑这一段 | 提到文件级：`private val PlatformOptions = listOf(null, Platform.PC, Platform.SWITCH, Platform.PS)`、`private val WishFilters = WishFilter.entries.toList()` |
| **C4** | `FinchSegmented` 高亮块用 `Modifier.offset(x=…)`（布局相位）而非 `graphicsLayer` | P2 | `Controls.kt:343-353` | 每次切换 tab，`animateDpAsState` 逐帧驱动 `offset` → 逐帧 remeasure + relayout 整条 `BoxWithConstraints`（含 N 个 label）。46dp 控件影响很小，但这是唯一一处"动画挂在布局相位"的地方，和 `Reveal.kt:32` 自己定的规矩（"动画全部落在 graphicsLayer 上"）不一致 | `offset` 改 `graphicsLayer { translationX = offsetPx }`，配 `FinchMotion.settle()`。或者保留（成本可忽略），但在规范里把例外写清楚 |
| **C5** | `UseCase`: `widthIn(min=92,max=108)*rightScale` + `TextAutoSize.StepBased(13..22sp)` 的组合在 fontScale 2.0 下的行为 | P1 | `UpcomingScreen.kt:414-416`、`PriceColumn:560`、`TextAutoSize:588-592` | `rightScale` 封顶 1.45 → fontScale 2.0 时右列仍是 133dp，但价格字号上限 22sp **不随 fontScale 缩放**（`TextAutoSize.StepBased` 的 sp 会被 fontScale 放大后**再**向下拟合到 13..22sp 区间 → 2.0x 下 22sp 实际渲染成 44px，超出 133dp 宽度后 `StepBased` 会一路缩到 13sp，视觉上价格凭空变小）。`TextAutoSize` 的拟合是**逐帧测量**（`onTextLayout` 迭代二分），不是每帧，但布局期间会跑 3~5 次 `TextLayoutResult`。真正风险不是每帧重测，而是**大字号下价格反而变小**的荒诞结果 | 大字号下应该放弃 `autoSize` 改 `maxLines=1 + Ellipsis`（价格被截断也比缩成 13sp 可读），或把右列宽度改成 `widthIn(min = 92.dp * rightScale)` 不给 max，让卡宽决定。**建议在 fontScale 2.0 下真机走查这一张卡**（Checklist 里"1.3x"不够） |
| **C6** | 0 个 `@Preview`、0 个 `androidTest`；UI 层无回归护栏 | P1 | `grep -rn "@Preview" app/src/main` → 0；`app/src/test` 23 个文件全是 `data/` 与纯函数（`StatsRhythmTest`、`YearReportDataTest`） | 任何一次令牌/玻璃改动只能靠真机肉眼回归。本报告的 A1/A2 这类"改一个词就修好"的问题，靠 Preview 能一次看全 5 个档位 | 最小落地：给 `Containers.kt` / `Controls.kt` 各加 1 个 `@Preview`，用 `@PreviewParameter` 遍历 `GlassStyle.entries` × `glassEnabled` × `fontScale`。这几个组件的入参全是 String/Color/枚举，能直接 Preview。UI 一旦有 Preview，`assembleDebug` 之外的"视觉回归"就有了第一道闸 |
| **D1** | `LiquidBottomTabs` 把 `content` 组合两次 | P2 | `LiquidBottomTabs.kt:181`（可见 Row）与 `:221`（`alpha(0f)` 的镜像 Row） | 5 个 tab × 2 = **每次底栏重组 10 次 `LiquidBottomTab` 组合**，10 个 `graphicsLayer`。`alpha(0f)` 的 Row 仍会完整测量/绘制/录制（它存在的目的就是给 `tabsBackdrop` 提供内容） | 这是上游配方的一部分（§2.2 豁免），**不建议改**。仅记录：底栏是 App 内单位面积绘制成本最高的 64dp（3 条 `drawBackdrop` 链 + 1 层录制 + shadow + innerShadow），任何往底栏加元素的需求都应先算这笔账 |
| **D2** | `GLASS_BLUR` 常量名为"开关"，实为编译期死代码 | P2 | `Glass.kt:93`（`private const val GLASS_BLUR = true`）、`:182` | 名字暗示可配置，但 `const val` 永远为 true，`if (GLASS_BLUR)` 分支是死代码，关不掉。**注意：把它改成 false 并不等于"零成本"**（见 §五 Q1） | 如果要留开关，用 `BuildConfig` 或 `CompositionLocal`；如果不要，删 `if` 直接 `blur(...)`，别让后来者以为有个性能逃生阀 |
| **D3** | `UpcomingScreen` 边界：`remember` 与 `rememberSaveable` 混用 | P2 | `UpcomingScreen.kt:100-101`（`platformPickerFor`/`query` 用 `remember`）、`:87/106`（`showWishlist`/`searchOpen` 用 `rememberSaveable`） | 同一个文件里两套内存策略。旋转屏幕后"搜到一半的日程页"搜索词清空、平台选择弹层消失，但"搜索框是展开的"仍然保留 → 状态自相矛盾 | 纯 UI 临时态统一 `rememberSaveable`；`platformPickerFor` 存的是 `UpcomingEntry`（不可序列化），改成存 `entry` 的稳定 key（`String`），弹层打开时按 key 反查 |
| **D4** | `FinchStars` 的金色 `Color(0xFFF5B301)` 未入白名单 | P2 | `Controls.kt:597`；同色重复出现在 `HomeCards.kt:337` | 同一个"评分星色"在两个文件各写一遍，改一处就会不同步。规范 §2.5 的白名单只提了 `StatsCards` 的金银铜 | 收进 `FinchColorScheme`：`val ratingStar: Color`，两处引用 |
| **D5** | `ui/Motion.kt` 死代码仍在 | P2 | `Motion.kt`（65 行）；`grep springEnterSpec/springPageSpec/springPressSpec/pressScale/emphasizedTween` 在 `Motion.kt` 外**零命中** | 规范 §2.2 已标注"待删"但一直没删。文件里的阻尼口径（MediumBouncy）与 `FinchMotion` 不一致，是个等着被误用的陷阱 | 整文件删除。这是零风险的纯删除（已确认无引用），五分钟的事，顺手做掉 |
| **D6** | `StatsCards.kt` 744 行 / 7 个卡片混居 | P1 | `StatsCards.kt`（`HeroTotalCard` / `MonthReportCard` / `PlatformCard` / `TopGamesCard` / `HeatmapCard` / `RhythmCard` / `MonthlyBars`） | 与本仓库自己的先例矛盾——`GameDetailCards.kt:69` 注释写着"v0.25.0 从 GameDetailScreen.kt 拆出，纯搬运不改行为"。统计页没享受同等待遇。744 行里改热力图要滚 500 行找上下文 | 按现有先例拆成 `stats/HeroCard.kt` / `stats/HeatmapCard.kt` / `stats/RhythmCard.kt` / `stats/RankingCard.kt`（纯搬运，`git mv` 语义，零行为变更）。这一步同时也是 A4/B4/B5 的安全网：拆完每张卡一个文件，改热力图不碰排行 |

**统计**：P0 × 5、P1 × 13、P2 × 9。

---

## 四、三个高杠杆重构

排序依据：**收益（省下的主线程毫秒 / 消除的错误面）÷ 改动量**。

### 重构 ① 修玻璃三处结构性浪费（A1 + A2 + A3 + B8 连带）

| | |
|---|---|
| **改动量** | 3 个文件，~15 行净增；**无 API 破坏** |
| **收益** | 主页首屏每帧省 ~8 次整卡离屏重绘 + 8 次无效 BlurMaskFilter；弹层（筛选/期数/平台选择/所有确认框）从"32 次跨窗口采样"降到 0；一次性消除文档与代码的最大矛盾 |
| **风险** | 低。A2 有 5% 概率是"视觉零变化"（说明 `drawBackdrop` 内部自己裁）——这种结果也算赢（说明该删 `clip`）。A3 会让弹层里的 chip/按钮从"玻璃"变"实色"，**这是设计意图**，但需 `visual-architect` 确认弹层内观感可接受 |

**文件与步骤**

1. `design/Glass.kt`
   - `:203-219`：`boxModifier` 重排为 `modifier → clickable → glassModifier → border(shape)`，删掉 `clip`。
   - `:147-150`：`canExport` 增加调用方开关。签名加 `exportBackdrop: Boolean = true`，判定改 `val canExport = exportBackdrop && depth < MAX_GLASS_DEPTH && (style == Card || style == Chrome)`。
2. `HomeCards.kt:228`：`style = GlassStyle.Card` → `GlassStyle.Tile`（或保持 Card + 传 `exportBackdrop = false`，二选一；**推荐前者**，与其余 3 个列表页一致）。
3. `design/Containers.kt`
   - `FinchSheet`（`:471`）与 `FinchDialog`（`:357`）的 content 外包：
     ```kotlin
     CompositionLocalProvider(
         LocalFinchBackdrop provides null,
         LocalGlassEnabled provides false,
     ) { /* 原有的 Column / content */ }
     ```
     注意要包在**面板内部**（不是 `ModalBottomSheet` 外面），否则 `SolidPanel` 的底色也会被换掉。
4. 顺带：`Containers.kt:433` 的确认按钮在弹层里会退化成 `SolidPanel`，检查对比度（`onPrimary` 压在 `surfaceContainerLow 0.58α` 上）。

**验证**

```bash
# 1. 编译
./gradlew :app:assembleDebug
# 2. 主页滚动 + 打开筛选弹层 / 期数弹层 / 删除确认框，肉眼过一遍
# 3. 玻璃关闭（设置→实验性功能→全面玻璃化 OFF）再走一遍，两路都不能塌
# 4. 关键：Layout Inspector 里选中 GameRowCard，看 "Layers" 面板
#    改前每行应有 3 层（alpha / export / surface），改后应只剩 1~2 层
```

---

### 重构 ② 列表项零浪费（A1 后续 + B1 + B3 + C3 + B7）

| | |
|---|---|
| **改动量** | 4 个文件，~40 行；`Reveal.kt` 有一处小重构 |
| **收益** | 稳态滚动时列表项 **0 个 alpha 离屏层、0 次 ImageRequest 分配**；搜索输入不再触发 `FinchSegmented` 全量重组与列表跳动 |
| **风险** | 中低。B1（入场动画结束后摘掉 graphicsLayer）逻辑要小心：摘得太早会闪，太晚没收益。建议保留 `translationY` 直到动画完全收敛再用 `Animatable.isRunning` 判定 |

**步骤**

1. `Reveal.kt:39-77`：加"动画完成后摘掉修饰符"。
   ```kotlin
   val finished = alphaAnim == 1f && offsetAnim == 0f && scaleAnim == 1f
   if (finished) this else this.graphicsLayer { /* 三个值 */ }
   ```
   （`animateFloatAsState` 收敛后值恒等于目标，读取不会引起额外重组——`finished` 在 `graphicsLayer` 外的组合作用域读，需实测是否引起重组；若引起，改为在 `LaunchedEffect` 里 `snapshotFlow` 监听后置一个 `mutableStateOf`。）
2. `SharedUi.kt:57-63`：`remember(url, requestSize, context)` 包 `ImageRequest.Builder`。
3. `HomeScreen.kt:442-452` + `WishlistPane.kt:110`：内联 `listOf` 提为文件级 `val`。
4. `HomeScreen.kt:166-168`：`query` 从 `LaunchedEffect` 的 key 里删掉。

**验证**

```bash
./gradlew :app:assembleDebug
# Layout Inspector → 开 "Recomposition counts" 与 "Show layers"
# 场景：主页滚动 3 屏后停住，观察 GameRowCard 的 layer 数应为 2（surface + 文本），无 alpha 层
# 场景：搜索框连续输入 5 个字符，观察 LazyColumn 与 FinchSegmented 的重组计数
#   改前：FinchSegmented 重组 5 次（每次 new list）
#   改后：0 次（实例相等，skip）
```

---

### 重构 ③ 状态可存活性（A5 + D6 + C6）

| | |
|---|---|
| **改动量** | 6 个文件；`StatsCards.kt` 拆 4 个文件（纯搬运） |
| **收益** | 切 tab / 旋转屏幕不再丢 3 个页面的状态（这是用户可感的 bug，不只是技术债）；`StatsCards` 从 744 行降到 ~180 行的入口 + 4 个 ~150 行的卡片；同时补齐 `@Preview` 的骨架 |
| **风险** | 中。`SaveableStateHolder` + `AnimatedContent` 的组合在转场期间两个 key 同时活跃，需要实测；`YearMonth` 不能直接进 Bundle，要转字符串。拆文件是纯搬运，风险接近 0，但会产生大 diff，建议单独一个 commit |

**步骤**

1. `FinchAppScaffold.kt:89-144`：
   ```kotlin
   val holder = rememberSaveableStateHolder()
   // …
   AnimatedContent(targetState = tab, …) { t ->
       holder.SaveableStateProvider(t) {
           Box(Modifier.fillMaxSize()) { when (t) { … } }
       }
   }
   ```
2. `StatsScreen.kt:105-106,155`：`mode`/`anchorMonth`/`weekStart` 改 `rememberSaveable`，`YearMonth`/`LocalDate` 用自定义 `Saver`（存 `toString()`，读 `YearMonth.parse`）。
3. `UpcomingScreen.kt:100-101`：同 D3 方案。
4. `StatsCards.kt` 拆为 `stats/` 下 4 个文件（纯搬运）。
5. 新增 `design/design/PreviewGallery.kt`：一个文件集中放 `Containers`/`Controls` 的 `@Preview`，`@PreviewParameter` 遍历 `GlassStyle.entries`。

**验证**

```bash
./gradlew :app:assembleDebug
# 场景 A：总结页翻到 9 月 → 切主页 → 切回总结 → 应仍是 9 月
# 场景 B：日程页搜 "zelda" → 切记录 → 切回 → 搜索词应还在
# 场景 C：旋转屏幕（或 adb shell settings put system accelerometer_rotation 0 + 手动转）
# 场景 D：Android Studio 打开 PreviewGallery.kt，4 档玻璃 × 明暗 应一次看全
```

---

## 五、性能验收方法

### 5.1 现状：**没有任何量化手段**

- 无 `:benchmark` 模块（`settings.gradle.kts` 只有 `:app`）
- 无 `androidTest`
- 无 baseline profile（`app/src/main/baseline-prof.txt` 不存在）
- 因此本报告所有"成本"都是静态推断。**下面的方案按优先级排列，Q1 是唯一能真正证伪/证实的实验。**

### 5.2 先做实验 Q1：`GLASS_BLUR` / `drawBackdrop` 的真实成本

这是团队-lead 指定要回答的问题，也是整个玻璃体系的成本基线。

**方法**：用 `Layout Inspector` 的 GPU 渲染条 + `dumpsys gfxinfo` 对比三档，不改代码，只改 `Glass.kt:93` 的常量再重编译。

| 档位 | 配置 | 预期 |
|---|---|---|
| T0 | `LocalGlassEnabled = false`（设置里关玻璃）→ 走 `SolidPanel`，**完全不进 `drawBackdrop`** | 基线 |
| T1 | 玻璃开，`GLASS_BLUR = false` | 应该**不是**零成本：`vibrancy()` 与 `lens()` 仍在跑，`drawBackdrop` 仍要分配/录制 surface 层 |
| T2 | 现状（`GLASS_BLUR = true`，`blurDp` 2~6） | — |

```bash
# 每个档位跑同一段：主页滚动 10 秒，取帧耗时分布
adb shell dumpsys gfxinfo dev.cao.finch reset
# (手动滚动 10 秒)
adb shell dumpsys gfxinfo dev.cao.finch framestats | head -60
# 关注三列：Draw / Prepare / GPU，分别对应 drawBackdrop 的 shader 提交、layer 录制、GPU 执行
```

**判据**（回答 lead 的两个子问题）：

1. **`blurDp = 2f` 是否仍有离屏开销？**
   **是。** 模糊半径不影响"是否离屏"——`RenderEffect.createBlurEffect` 一旦挂上，该 surface 就必须先渲染到纹理再采样。半径只影响采样核大小（GPU 端）。所以 `Tile` 档的 `blurDp = 2f` 与 `Chrome` 档的 `6f` 在"多一次离屏 pass"这件事上**等价**，区别只在 GPU 的 ALU 时间（2dp 核小，几乎免费；真正的成本是那次 texture round-trip）。
2. **`GLASS_BLUR = false` 是否真的零成本？**
   **不是。** 只有 `LocalGlassEnabled = false`（`Glass.kt:119`）才真正零成本——那条路径 `return` 在 `drawBackdrop` 之前。`GLASS_BLUR` 只是省掉 blur 一个 effect，`vibrancy()` + `lens()` + `drawBackdrop` 的层分配/录制全都在。**这个常量的命名是误导性的**（见 D2）。如果目标是"低端机性能逃生阀"，逃生阀应该是 `LocalGlassEnabled`，而它已经存在了。

**一个 LazyColumn 里 5~10 张 `GlassSurface(Tile)` 滚动的合成成本结论**：

- **采样源**是共享的：`backgroundBackdrop` 全 App 只有一份（`FinchAppScaffold.kt:89`），**不会**每个 item 重新采样/重录背景（lead 问题 2 的答案：**可以共享，且已经共享了**）。
- **但每个 item 各跑一遍 `drawBackdrop`**：每张 Tile = 1 次 surface 层渲染 + vibrancy + blur + lens 三个 effect 的 shader pass。**成本是线性 × 可见 item 数**，与采样源是否共享无关。
- 因此 10 张 Tile ≈ **10×(1 次离屏 + 3 个 shader)**。这是"玻璃即成本"的物理下限，改不掉，只能减少张数或降档。
- **Tile 档不含 `exportedBackdrop`**（`Glass.kt:148-149` 明确排除），所以列表行不会多录一层——这也正是 A1 的问题所在：`GameRowCard` 用了 `Card`，破例了。

### 5.3 建立 Macrobenchmark 基线（建议本轮就做，收益持续）

```gradle
// settings.gradle.kts
include(":benchmark")
// benchmark/build.gradle.kts —— com.android.test + targetProjectPath = ":app"
```

`FrameTimingMetric` + `StartupTimingMetric` 两个测试就够：

```kotlin
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun homeScroll() = rule.measureRepeated(
        packageName = "dev.cao.finch",
        metrics = listOf(FrameTimingMetric()),
        iterations = 10,
        startupMode = StartupMode.WARM,
        setupBlock = { pressHome(); startActivityAndWait() },
    ) {
        val list = device.findObject(By.res("home_list"))   // 需要 testTag
        repeat(6) { list.fling(Direction.DOWN); device.waitForIdle() }
    }
}
```

**要看的指标（按诊断价值排序）**：

| 指标 | 来源 | 判定线 | 对应的本报告问题 |
|---|---|---|---|
| `frameDurationCpuMs` P90 | FrameTimingMetric | < 16.6ms（60Hz）/ < 8.3ms（120Hz）；**P90 > 2×P50 说明有毛刺** | A1、A2、B1 |
| `frameOverrunMs` | FrameTimingMetric | 应 < 0；> 16ms 即肉眼可见卡顿 | A4（翻期时）、B8 |
| `Recompositions`（单 Composable） | **Layout Inspector** 的 recomposition counts，非 benchmark | 滚动中 `GameRowCard` 应为 0（已稳定） | B2、C3、B5 |
| `Layers`（单 Composable） | Layout Inspector → Show Layers | `GameRowCard` ≤ 2 | A1、B1 |
| 首帧 `timeToInitialDisplayMs` | StartupTimingMetric | 冷启 < 800ms | 玻璃初始化、主题 |
| `dumpsys gfxinfo · Janky frames` | adb | < 5% | 综合 |

**注意**：Macrobenchmark 需要 `debuggable=false` 的 build + `testTag`。当前代码 **0 个 `testTag`**，列表也没有 `Modifier.semantics` 标识——这是接入 benchmark 的前置工作（约 10 个 `testTag`），也顺带解决 C6 的一半。

### 5.4 每次 UI 改动的验收流程（最小可行）

```bash
# 1. 编译 + 单测（现有 23 个纯函数测试，不能红）
./gradlew :app:assembleDebug :app:testDebugUnitTest
# 2. 手工三件套（每项 1 分钟）
#    a) 主页滚动 3 屏 → 看有没有掉帧（GPU rendering profile 的绿线不超过 16ms 红标）
#    b) 设置里关玻璃 → 全 tab 走一遍 → 对比度/层级不能塌（§3.1 要求双路验证）
#    c) 系统字号调 1.3x 与 2.0x → 走查 hero / 商店卡 / 统计 hero（C5 的现场）
# 3. Layout Inspector 抽查 layer 数与重组数（针对本次改动涉及的 Composable）
```

---

## 六、逐项答复 team-lead 指定的 5 个问题

| # | 问题 | 结论 |
|---|---|---|
| Q1 | `GlassStyle` 四档都走 `drawBackdrop` + `blur()` 离屏采样，10 张 Tile 滚动成本？`blurDp=2f` 还有离屏开销吗？`GLASS_BLUR` 关掉是否零成本？ | 见 §5.2。要点：**采样源已共享**（不是每 item 重采背景）；但**每 item 各跑一遍 surface pass**，成本线性；**`blurDp=2f` 仍有完整离屏 round-trip**（半径只影响 GPU 核大小）；**`GLASS_BLUR=false` 不是零成本**，只有 `LocalGlassEnabled=false` 才是（`Glass.kt:119` 提前 return）。`GLASS_BLUR` 是 `const val`，名为开关实为死代码（D2） |
| Q2 | `Containers.kt` 里 `GlassSurface` 是否对每个 item 重新采样背景？能否共享一次采样？ | **已经是共享的**：唯一采样源 `backgroundBackdrop` 在 `FinchAppScaffold.kt:89` 建一次、`:93` 下发，所有 `GlassSurface` 默认取 `LocalFinchBackdrop.current`（`Glass.kt:112`）。**不要**再优化这一层。真正的重复在别处：`Card`/`Chrome` 档会为每张卡**新增**一张导出层（`Glass.kt:150`），而主页列表行用了 Card 却没人消费（A1） |
| Q3 | `UpcomingScreen.kt:368-419` 的 `widthIn(min=92,max=108)*rightScale` + `TextAutoSize.StepBased(13..22sp)` 在 fontScale 2.0 下会不会每帧重测？ | **不会每帧重测**——`TextAutoSize` 的拟合发生在**布局相位**，一次 `measure` 内迭代 3~5 次 `TextLayoutResult`，不是逐帧。真问题是结果荒诞：`rightScale` 封顶 1.45 而 sp 被 fontScale 线性放大到 2.0，22sp→44px，必然放不下 → `StepBased` 一路缩到 13sp，**大字号用户看到的价格比标准字号更小**。这是 C5，建议 2.0x 真机走查 |
| Q4 | `FinchAppScaffold.kt:115-144` 切 tab 丢状态（无 `SaveableStateHolder`），改法与风险？ | 认定为 **P0（A5）**：不只是技术债——`StatsScreen` 的选中月份（`:106`）用的是 `remember`，切 tab 回来会回到当月，用户可感。改法与风险见 §四 重构 ③。补一条 lead 可能没注意的：**ViewModel 不受影响**（`viewModel()` 无 NavHost 时作用域是 Activity），所以 `UpcomingViewModel` 的数据还在，丢的纯是 `remember` 的 UI 态 |
| Q5 | `Tokens.kt:84-128` `finchClickable` 用 `composed{}` + `collectIsPressedAsState`，上百 item 的成本？ | 每个可点 item = **1 个子组合节点 + 1 个 `MutableInteractionSource` + 1 个 `Float` 动画 + 1 个常驻 `graphicsLayer`（即使 scale 恒为 1）**。`composed` 的开销在**首次组合**（每 item 一次子组合，约几十 µs 级），不在重组——所以"上百 item"的真实代价是**列表首次铺开/大跳动时的组合时间**，约 100×0.05ms ≈ 5ms 量级，不会导致掉帧但会在冷启首屏可感。**收益更大的是那个无条件 `graphicsLayer`**（`Tokens.kt:101-104`）：稳态滚动时每 item 都占一个 RenderNode。改 `Modifier.Node`（B2）能一并解决；如果不想动 API，最小改动是 `graphicsLayer` 只在 `scale != 1f` 时才挂（`if (scale != 1f) graphicsLayer{…} else this`）——两行，立刻省掉全部稳态 layer |

---

## 附录：可复现命令汇总

```bash
cd app/src/main/java/dev/cao/finch/ui

# 令牌统计
grep -rnoE "[0-9]+(\.[0-9]+)?\.dp" --include=*.kt . | wc -l                       # 226
grep -rno  "Color(0x"              --include=*.kt . | wc -l                       # 209
grep -rno  "spring("               --include=*.kt . | wc -l                       # 16
grep -rno  "tween("                --include=*.kt . | wc -l                       # 28
grep -rnoE "[0-9]+(\.[0-9]+)?\.sp" --include=*.kt . | wc -l                       # 45
grep -rnoE "Spacer\(Modifier\.(width|height)\([0-9.]+\.dp\)|spacedBy\([0-9.]+\.dp|padding\([a-zA-Z]* = [0-9.]+\.dp)" --include=*.kt . | wc -l   # 45 = 可收敛的间距字面量

# 玻璃链路
grep -rn "GlassStyle\.\(Card\|Hero\|Chrome\|Tile\)" --include=*.kt .
grep -rn "LocalFinchBackdrop" --include=*.kt .            # 下发点应只有 FinchAppScaffold:93
grep -rn "rememberLayerBackdrop\|rememberCombinedBackdrop\|layerBackdrop" --include=*.kt .

# 列表与可点
grep -rn "items(\|itemsIndexed(\|item(" --include=*.kt . | grep -v "key"
grep -rn "finchClickable(" --include=*.kt . | wc -l       # 15
grep -rn "composed {" --include=*.kt .                    # Reveal.kt:39 + Tokens.kt:92 + Motion.kt:55(死代码)

# 可测试性
grep -rn "@Preview" ../../../../../main/java | wc -l      # 0
ls ../../../../../test/java/dev/cao/finch/ui              # 只有 StatsRhythmTest / YearReportDataTest

# 死代码
grep -rn "springEnterSpec\|springPageSpec\|springPressSpec\|pressScale\|emphasizedTween" --include=*.kt . | grep -v "Motion.kt"
```

> 行号基于工作区当前状态（v0.29.5）。修改后行号会漂移，请以"文件 + 函数名"定位。
