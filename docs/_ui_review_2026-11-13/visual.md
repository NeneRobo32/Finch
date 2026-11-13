# Finch 视觉设计审查 —— 色彩系统 / 排版 / 材质 / 组件视觉

| 项目 | 内容 |
|---|---|
| 应用 | Finch（`dev.cao.finch`） |
| 版本 | v0.29.5 / versionCode 103 |
| 审查日期 | 2026-11-13 |
| 审查范围 | `ui/theme/*`（Type / AppThemes / Shapes）、`ui/design/*`（Tokens / Glass / Containers / Controls / FinchAurora / FinchBottomBar）、`HomeScreen + HomeCards`、`UpcomingScreen（含 StoreEntryCard）`、`WishlistPane`、`StatsScreen + StatsCards`、`GameDetailScreen + GameDetailCards`、`HistoryScreen`、`liquid/LiquidBottomTabs` |
| 方式 | 逐文件静态阅读 + 对比度/字号/alpha 实算（sRGB 相对亮度公式；`Color.lerp` 为 Oklab 插值，已单列说明），非抽样 |
| 基线 | 已读 `UI_DESIGN_GUIDE.md`、`UI_REVIEW_2026-11-12.md`、同批 `ux.md`（20 条）。**本文只讲视觉语言**，与 ux 的架构/可用性条目零重叠，重叠处只作交叉引用 |
| 截图 | `build-tmp/screens/*`（2026-10-20，约 v0.27）仅用于建立调性认知；布局事实一律以源码为准 |
| 约束 | 只读审查，未修改 `app/` 下任何源码 |

---

## 0. 一句话总体判断

**视觉语言是成立的**——「静态极光底 + 四档玻璃 + 蓝紫 hero + 超大数字」这套组合有辨识度，令牌化程度（`FinchSpace` / `FinchShapes` / `FinchMotion` / `FinchColorScheme`）也远高于同类个人项目；**最强的一环是「数据展示层」**（hero 大数字、hero 卡、`FinchStatTile` 的自适应数字），**最弱的一环是「语义色的单一职责」**——`tertiary` 一个人兼了 5 个含义、`primary` 在白字下只有 3.70:1（另两套主题 5.6 / 7.2:1，唯独默认主题不达标）、平台色直接借用主题语义色，于是**同一屏里出现了 5~7 种不同的蓝和 3 种不同的紫**。

一句话概括病因：**审美方向没错，是同一套语言被局部改写了 6 次**（hero 渐变 4 种实现、列表卡 3 种档位、页头图标 2 种形态、「计时中」3 种视觉、金属色 2 套、圆角 9 档）。

> **与 `ux.md` 的分工**：ux 已记录 ①选中态 `#3D7FFF`+白字 3.7:1（P1-3）②`FinchSegmented`/`FinchButton(compact)` 大字号裁切（P1-4）③商店卡右列在大字号下吃掉宽度（P1-5）④页头副标题折行（P2-8）。**本文不重复这 4 条**，只在需要时引用；本文新增的是：中文排版的负字距落点审计、三色层级的饱和度归属、数据色阶断裂、双轨材质 alpha 落差、以及商店卡「双焦点」这一纯视觉判断。

---

## 1. 视觉体系盘点（全部为代码真实值）

### 1.1 强调色：声明 1 个蓝，屏幕上有 5~7 个

| # | 色值 | 来源 | 用在哪 | 是否 token |
|---|---|---|---|---|
| 1 | `#3D7FFF` | `theme/AppThemes.kt:124` | 选中胶囊底、分段高亮起点、FAB、底栏选中、进度条起点、热力图基色 | ✅ `primary` |
| 2 | `#3461B9`（≈`#3262C0`） | `AppThemes.kt:401` `lerp(primary,onSurface,0.28)` | 11~14sp 强调文字、`FinchBadge` | ✅ `accentText` |
| 3 | `#506FC9` | `design/Controls.kt:636` `lerp(primary,tertiary,0.4f)` | FAB 渐变终点 | ❌ 无 token |
| 4 | `#4B73D6` | `Controls.kt:357` `lerp(primary,tertiary,0.3f)` | `FinchSegmented` 高亮块终点 | ❌ |
| 5 | `#526CC2` | `Controls.kt:548` `lerp(primary,tertiary,0.45f)` | `FinchProgressBar` 终点 | ❌ |
| 6 | `#8FB8FF` | `AppThemes.kt:194` `glowA` | 全屏氛围光斑 | ✅ `ambient` |
| 7 | 渲染值≠`#3D7FFF` | `Controls.kt:176-193`（`LiquidButton` `tint=primary`，注释写"用主题色 tint（Hue + 75% 叠色）"） | 所有主按钮实底 | ❌ 不可预测 |

→ **同一个渐变搭档系数被写成 0.3 / 0.4 / 0.45 三个值**（三处、三个文件），直接导致同屏出现 3 个"相邻但不相等"的蓝。

### 1.2 语义色实际承载的含义（这是最该先修的一张表）

| token | 声明用途（`AppThemes.kt:96-104` 注释） | 实际用途 | 冲突 |
|---|---|---|---|
| `primary` | 交互主色 | ①交互 ②数据基色 ③平台色=PC（`HistoryScreen.kt:285`）④选中态 | ①②混用，数据与交互不可分 |
| `tertiary` | 「暂停 / 次要态」 | ①暂停（`HomeCards.kt:268`、`HistoryScreen.kt:212`）②热力图最高档（`StatsCards.kt:515`）③周/月条峰值（`:586`、`:727`）④平台色=Switch（`HistoryScreen.kt:286`）⑤三条渐变的搭档色 | **5 个含义**，其中②③④互相矛盾 |
| `secondary` | 次要色 | 平台色=PS（`HistoryScreen.kt:287`），`#4F616E` 与正文 `onSurfaceVariant` 几乎同色 | 平台识别度≈0 |
| `error` | 危险操作 | ①危险操作 ②「计时中」徽章（`HistoryScreen.kt:212`）| 可接受，但与 `HomeCards.kt:268` 的硬编码红不一致 |

### 1.3 卡片/materials：glass 与 solid 双轨 alpha 差 4 倍

| 档位 | glass 有效底 alpha（`glass.tint.alpha × tintScale`） | solid 底 alpha（`Glass.kt:260-266`） | 差值 |
|---|---|---|---|
| `Chrome` | `0.66 × 0.30 = 0.198` | `0.70` | 3.5× |
| `Card` | `0.66 × 0.22 = 0.145` | `0.58` | **4.0×** |
| `Tile` | `0.66 × 0.18 = 0.119` | `0.50` | 4.2× |
| `Hero` | `0.66 × 0.12 = 0.079` | `0.42` | 5.3× |
| 附加 | sheen 顶部白 `0.22/0.18/0.14/0.10`（`Glass.kt:156-162`） | 无 | — |
| 描边 | `edge 0.70 × edgeAlpha 0.52 = 0.364`（Card） | `outline @ edgeAlpha×0.7` | 不同色源 |

其它 surface（同一套 UI 里共 10 个不同 alpha）：`InsetSurface` 非玻璃 0.55/禁用 0.30（`Controls.kt:124`）、Dialog `0.98→0.97`（`Containers.kt:395`）、Sheet `0.99→0.98`（`:494`）、TextField 聚焦 0.18 / 常态 0.14（`Controls.kt:428-433`）、底栏 0.16 / 深色 0.10（`FinchBottomBar.kt:73`）、实心底栏 0.92 / 0.86（`:108`）。

### 1.4 hero / 渐变：4 种实现，2 种明度语言

| # | 场景 | 实现 | 证据 |
|---|---|---|---|
| A | 主页 hero **有**封面 | 封面图 + 竖向黑罩 `0.05 / 0.18 / 0.82` | `HomeCards.kt:110-120` |
| B | 主页 hero **无**记录 | `primary→tertiary` 线性渐变 + 两团白径向光（0.22 / 0.16） | `HomeCards.kt:162-181` |
| C | 统计页 hero | 无渐变，走 `GlassStyle.Hero` 让背景透上来 | `StatsCards.kt:95-102` |
| D | Dialog / Sheet「模拟玻璃」 | `surface 0.98 → surfaceVariant 0.97` | `Containers.kt:393-397`、`:492-496` |

→ A 的观感是**灰黑**（黑罩占卡片下部 55% 面积），B 的观感是**品牌蓝紫**。同一个组件，有数据时最不像 Finch，没数据时最像 Finch。这是截图 `01_home.png` 里 hero 读起来像"一张灰卡"的根因。

### 1.5 字号阶梯（`theme/Type.kt`）与**实际调用点**

| 档位 | 字号/行高/字距 | 字距占 em | 调用点 | 内容语种 |
|---|---|---|---|---|
| displayLarge | 57 / 60 / **−1.6** | −2.81% | **0 处** | — |
| displayMedium | 45 / 50 / **−1.2** | −2.67% | `StatsCards.kt:126` | 数字 |
| displaySmall | 38 / 44 / **−0.8** | −2.11% | `HomeCards.kt:145`、`GameDetailCards.kt:179` | 数字 |
| headlineLarge | 32 / 38 / −0.5 | −1.56% | **0 处** | — |
| headlineMedium | 27 / 33 / −0.3 | −1.11% | `Containers.kt:164`（页头）、`GameDetailCards.kt:133`（详情标题） | **中文** |
| headlineSmall | 23 / 29 / −0.2 | −0.87% | `UpcomingScreen.kt:582`（价格=数字）、`PlatinumCelebration.kt:115`（中文） | 混合 |
| titleLarge | 20 / 26 / −0.1 | −0.50% | 7 处（中文标题为主） | 中文 |
| titleMedium → labelSmall | 16/22 → 11/15 | 0 ~ +0.25sp | 全站 | 中文为主 |

### 1.6 圆角：名义 5 档，**实际 9 档**

`FinchShapes`（`theme/Shapes.kt:24-38`）：hero 32 / card 24 / sheet 30(上) / inset 20 / tile 18 / field 16 / thumb 12 / pill。
裸写额外 4 档（`grep RoundedCornerShape(` 共 8 处）：

| 值 | 位置 |
|---|---|
| 30dp（**反向**，bottom 两角） | `GameDetailCards.kt:79` 详情封面 |
| 8dp | `report/BacklogFunnelCard.kt:167` |
| 6dp | `StatsCards.kt:439` 月热力格 |
| 3dp | `StatsCards.kt:493` 年热力格 |
| 2dp | `StatsCards.kt:635` 高峰时段柱 |
| `percent(50)` | `FinchBottomBar.kt:102`（等价 pill，但语法不同源） |

→ 热力图三档半径（6/3/2）与卡片的连续曲率圆角不是同一套语言，视觉上"格子是方的、卡是软的"，这是数据卡与内容卡读起来像两套系统的原因之一。

### 1.7 硬编码色清单（`grep "Color(0x"` 排除主题与背景）

| 色值 | 位置 | 语义 | 备注 |
|---|---|---|---|
| `#F5B301` | `Controls.kt:597`、`HomeCards.kt:337`、`gamedetail/ConfettiOverlay.kt:18` | 星级金 | 同一语义 3 处定义 |
| `#E8B63C` / `#A8B0BD` / `#C08A5E` / `#D7DCE2` | `StatsCards.kt:70-73` | 排行 1/2/3/其他 | 套装 ① |
| `#E6B422` / `#AAB2BD` / `#B0722E` / `#9CB6D1` | `GameDetailCards.kt:428-431` | 奖杯 金/银/铜/白金 | 套装 ②（与①同语义不同值） |
| `#FF6B81` | `GameDetailCards.kt:401` | 收藏红心 | 不在任何主题色板 |
| `#E5484D` | `HomeCards.kt:268` | 「计时」徽章底 | 不在任何主题色板，且与 `error #CF202F` 不同 |

---

## 2. 三个专项判断（按 team-lead 指定）

### 2.1 中文负字距：担心方向对，但**当前代码没踩到**；真正的问题在别处

我核了 `FinchTypography` 全部 6 个负字距档位的**每一个调用点**（见 §1.5）：

- **带最强负字距的三档（57 / 45 / 38sp，−2.8% / −2.7% / −2.1%）只用在数字上**：`displayMedium` 唯一调用点是"本月总时长 20h 0m"（`StatsCards.kt:126`），`displaySmall` 两个调用点是 hero 数字（`HomeCards.kt:145`）和计时器 `00:00:00`（`GameDetailCards.kt:179`）。拉丁数字在负字距下是**变好看**的（数字等宽块收紧后整块更像一个数），这正是 Apple / Google 大数字的做法。
- **落在中文上的只有两处，且都极轻**：`headlineMedium` −0.3sp = **−1.11% em**（页头「游戏库」「发售日程」「记录」「总结」，`Containers.kt:164`）、`headlineSmall` 的中文用法 −0.2sp = −0.87%。
- **两个最激进的档位是死代码**：`displayLarge`(57) 与 `headlineLarge`(32) **全项目 0 调用点**。

量化一下"多少才算挤"：CJK 全角字面 1em，字面自身左右各留约 1.5~3% em 的侧边距。−2.8% 的负字距会把这层留白吃干（笔画贴到 0~1px），但 −1.1% 只吃掉三分之一，肉眼几乎无感。所以现在的中文标题**没有肉眼可辨的黏连**。

**结论与改法（不是简单归零，而是分语种拆样式）：**

| 档位 | 现状 | 改成 | 理由 |
|---|---|---|---|
| `headlineMedium` 27/33/−0.3 | 中文标题 | **字距 0，行高 33→35**（1.22→1.30） | 27sp 中文单行标题，负字距无收益；行高 33/27=1.22 对中文偏紧 |
| `headlineSmall` 23/29/−0.2 | 数字价 + 中文混用 | 拆两个：`FinchNumber.price`（保留 −0.2 + tnum）/ 标题用 `titleLarge` | 一个档位两种语种，是隐患本身 |
| `displayMedium` / `displaySmall` | 纯数字 | **保留负字距**，但增设 `FinchNumber` 样式统一挂 `tnum` | 数字该收，别动 |
| `displayLarge` 57 / `headlineLarge` 32 | 0 调用 | **删掉**（或注释标「仅拉丁数字」） | 留着就是等下一次有人拿它排中文大标题 |
| 未来中文 display（若有） | — | 字距 0、行高 ≥1.2、**Bold 改 SemiBold** | 57sp 中文用 Bold 笔画会糊成块，这是比字距更明显的问题 |

一行总结给团队：**"负字距"不用改，因为它现在只服务数字；要改的是「把数字样式从 CJK 标题样式里拆出去」，外加删掉两个没人用的大字档。** 否则下一个人写「年度报告」大标题时，一定会踩。

### 2.2 玻璃 / 极光 / hero 三者谁该降饱和

先看三者的**饱和度与面积占比**（这是判断"抢层级"的正确指标）：

| 元素 | 面积 | 色彩浓度 | 结论 |
|---|---|---|---|
| 极光背景 | 100% | 3 团光斑 `#8FB8FF / #C3B2FF / #8FE6FF`，alpha `0.55 / 0.38 / 0.40 × intensity 0.95`（`FinchAurora.kt:37-39`），半径 `0.85w / 0.95w / 1.0w`（几乎都是覆盖半屏以上） | **最饱和、最大面积** |
| 玻璃卡 | 列表 60~80% | 底 alpha 仅 `0.145` 白 + sheen 0.18，**自身色相 = 无**（完全透过背景） | 中景缺失 |
| hero | 25% | 有封面时是黑罩 0.82（无彩），无封面时满饱和 `primary→tertiary` | 时强时弱 |

**判断：抢同一个层级的是「背景」和「hero」，玻璃卡反而是受害者。** 现在的画面读起来是「一张全屏彩纸 + 一堆白条 + 一块彩色/黑色的海报」——缺的是**中景**。用户感知到的"有点廉价"，本质是"所有东西都在同一层水里漂"：卡片没有自己的底色身份，它只是"背景提亮后的样子"，所以背景上的光斑变化会直接变成"这张卡比那张卡更蓝"。

**谁该降：降背景，不降 hero；同时给卡片补一层中性底（这是最反直觉的一条，但它是把层级立起来的关键）。**

| 动作 | 具体值 | 文件 |
|---|---|---|
| ① 光斑降饱和 + 收面积 | `glowA #8FB8FF→#A9C6FF`、`glowB #C3B2FF→#D2C7FF`、`glowC #8FE6FF→#AEEFFF`；`intensity 0.95→0.70`；半径 `0.85w→0.62w` / `0.95w→0.70w` / `1.0w→0.78w` | `theme/AppThemes.kt:192-198` + `design/FinchAurora.kt:37-39` |
| ② 卡片给中性底（补中景） | `GlassStyle.Card` 的 `tint` 从 `White@0.66`（`AppThemes.kt:162`）改为 `surface #F5F7FB@0.72`；`tintScale 0.22→0.38` → 有效 alpha `0.27` | `AppThemes.kt:161-167` + `design/Glass.kt:74-84` |
| ③ hero 保住饱和度 | 有封面时黑罩 `0.05/0.18/0.82` → `0.12/0.30/0.62`，去掉"上半灰下半黑"两段感 | `HomeCards.kt:114-119` |

预期视觉差异：卡片从"背景的提亮版"变成"压在背景上的一张纸"（有独立色相、边缘更硬），背景光只从卡片间隙透出来 → 中景建立，玻璃的折射边缘才有东西可折射；hero 因为周围降饱和而更突出，不需要加饱和。

### 2.3 商店卡（`UpcomingScreen.kt:325-445`，v0.29 新版）：层级成立，但**双焦点**了

**信息层级本身是成立的**：价格 > 标题 > 元信息，这个顺序对"买不买"的决策正确，`PriceColumn` 用 22sp（`UpcomingScreen.kt:582`）把"多少钱"抬到第一眼，是对的，不用改。

**但视觉上有个结构问题：左封面 62×82 与右列（徽章 21 + 现价 22 + 划线 15 ≈ 82dp）几乎等高**，中间一列文字被两块等高色块夹住 → 眼睛读到的不是"缩略图 + 内容 + 操作"，而是**「两个方块 + 一条缝」**。上一轮把"太空"归因为中间列写死高度（`:318-322` 注释），那只解决了一半；剩下一半是**左右两侧各自形成一个视觉重心**。

三个具体问题（都在正常字号下，不涉及 ux 的大字号条目）：

| 问题 | 证据 | 量化 | 改法 |
|---|---|---|---|
| **双焦点** | 封面 `:360` 62×82；右列 `:415-442` 内容高 ≈ 21+2+22+4+36 = 85dp | 两侧高度 82 vs 85，差 3dp，视觉上"等高" | 右列去掉 `FinchIconButton` 的 40dp 视觉圆（ghost 后仍有 48dp 命中），星标改 20dp 线性图标 + 命中区外扩 → 右列降到 ~60dp，与封面形成 82 > 60 的明确主次 |
| **字号跳跃过大** | 11sp（`labelSmall` 徽章/原价）→ 16sp（`titleMedium`）→ 22sp（`headlineSmall`） | 22/11 = **2.0×**，且中间缺 18~20sp 一级 | 标题 `titleMedium`→`titleLarge`(20sp)，价格 22→20sp：跨度从 2.0× 收到 1.8×，标题在玻璃上更"立"（`StoreEntryCard` 注释 `:380-382` 已意识到这点，但只升了行高没升字号） |
| **价格实际会缩到比标题还小** | `:588-592` `autoSize min 13sp`；`HK$1,388.00` 在 96dp 内约需 13~16sp | 缩到 13sp 时 **13 < 16（标题）**，主次反转；且 13sp 的 `primary` 只有 3.4:1 | autoSize 下限提到 16sp，超长时允许折行；或数字色改 `accentText`（见 V11 详情） |

---

## 3. 问题清单

严重度：**P0** = 视觉上明显露馅 / 信息不可读；**P1** = 不一致或层级混乱；**P2** = 打磨项。
（ux.md 已覆盖的 4 条不在表内，见 §0 分工说明。）

| # | 标题 | 严重度 | 证据 | 用户视角影响 | 改法 |
|---|---|---|---|---|---|
| **V1** | 排行榜奖牌上的白字几乎读不出（1.80:1） | **P0** | `StatsCards.kt:70-73` 色值、`:343-350` 白字 12sp | 总结页「游戏排行」前三名的名次数字在金色上发白糊掉，第一名最严重 | 见 3.1 |
| **V2** | 详情页悬浮顶栏滚动后白图标 ≈2.0:1 | **P0** | `GameDetailCards.kt:381-424`（黑 0.28 圆底，无 backdrop 无 scrim）、`GameDetailScreen.kt:275-279`（顶栏不随内容滚动） | 往下滚到「状态与评分」时，返回/收藏按钮压在白色玻璃卡上，白图标几乎看不见 | 见 3.2 |
| **V3** | 缺中景：背景满饱和 + 卡片零色相 → 层级断层 | **P0** | `FinchAurora.kt:37-39`、`AppThemes.kt:192-198`、`Glass.kt:154` | 整体"有点廉价"，卡片像背景的提亮版，同一列表里每张卡底色随背后光斑变化 | 见 §2.2 表 |
| **V4** | `tertiary` 一人 5 职 + 热力图色阶断裂 | **P1** | `AppThemes.kt:130`；`StatsCards.kt:507-517`（最高档=`tertiary`）、`:586`、`:637`；`HistoryScreen.kt:286`；`HomeCards.kt:268` | 热力图蓝紫混排，读成"两种/三类数据"而非"一个量的深浅"；Switch 平台色、暂停色、峰值色是同一个紫 | 见 3.3 |
| **V5** | hero 四种实现，有数据=灰黑、无数据=品牌蓝紫 | **P1** | `HomeCards.kt:110-120` vs `:162-181`；`StatsCards.kt:95-102`；`Containers.kt:393` | 主页第一屏的主视觉在"有记录"和"没记录"两种状态下是两种明度语言，用户看不出这是同一个组件 | 统一为：`GlassStyle.Hero` 玻璃底 + 底部 0.62 暗化（B 状态也加同款暗化），品牌色只出现在无封面时的渐变里且与统计页 hero 同源 |
| **V6** | 同屏 5~7 种蓝；渐变搭档系数 0.3/0.4/0.45 三处散写 | **P1** | `Controls.kt:357`、`:548`、`:636`；`AppThemes.kt:124/194/401` | 主页一屏内 FAB、分段高亮、选中胶囊、底栏选中、hero 是 5 个"相邻但不相等"的蓝，看起来像没统一过 | `AppThemes.kt` 新增 `accentGradientEnd = lerp(primary, tertiary, 0.34f)` 单一 token，三处引用；`glowA` 与 `primary` 拉开明度差（见 V3） |
| **V7** | 玻璃/实色双轨 alpha 差 4 倍；实色路径引用了未定义 token | **P1** | `Glass.kt:154` vs `:258-271`；`AppThemes.kt:123-141`（未定义 `surfaceContainer*`） | 用户关掉「全面玻璃化」后，卡片从 0.145 跳到 0.58（4×），整个明度系统被换掉；且 `surfaceContainerLow/High` 取的是 M3 基线默认值（`#F7F2FA`/`#ECE6F0`，带紫调），与主题中性底 `#F5F7FB` 不是一个色系 | `SolidPanel` 的 `surfaceContainerLow/High` 换成主题已定义的 `surface` / `surfaceVariant`；Card 档 solid alpha 0.58→0.46（与 glass 0.145 + sheen 0.18 的观感对齐），逐档验证一遍 |
| **V8** | 列表卡三套档位 | **P1** | `HomeCards.kt:228-229`（`Card`+24dp）、`HistoryScreen.kt:175-176`（`Tile`+18dp）、`UpcomingScreen.kt:346-347`（`Tile`+**24dp**）、`WishlistPane.kt:193-194`（`Tile`+24dp） | 三个 tab 的列表卡厚度/描边/圆角都不同，切 tab 时"卡片"这个词的视觉定义会变 | 定档：行卡（主页/记录）统一 `Card` + `card(24)`；辅助卡（愿望单汇总）用 `Tile` + `tile(18)`；`StoreEntryCard` 的 `Tile` 改 `Card`（它与主页行卡同级） |
| **V9** | 页头图标按钮两套形态，主页一行内还混用 | **P1** | 主页 `HomeScreen.kt:391-403`（搜索/排序 `ghost=true` 无底）vs `:457-462`（筛选默认玻璃圆底）；日程 `UpcomingScreen.kt:122-155`（三个全玻璃圆底） | 主页控件区同一屏里两个裸图标 + 一个玻璃圆底图标，看起来像"筛选是个特殊功能"；主页与日程的页头图标语言相反 | 统一为 `ghost`（页头动作一律无底，只留 48dp 命中 + 涟漪），玻璃圆底只用于"页头之外的独立操作"。一处改完三页一致 |
| **V10** | 同一状态「计时中」三种视觉，其中一种是硬编码红且不达标 | **P1** | `HomeCards.kt:262-277`（`#E5484D` 白字 11sp = **3.91:1**）、`HistoryScreen.kt:208-214`（`error` 淡底徽章）、`GameDetailCards.kt:175`（`titleExtra` primary 文字） | 同一件事在三个页面长得完全不一样；主页那个红底白字在强光下读不清 | 统一走 `FinchBadge`：`计时中` = `error` 系（淡底 + `error` 文字，对比 5.3:1）、`已暂停` = `tertiary` 系；`#E5484D` 删除 |
| **V11** | 商店卡双焦点 + 价格 autoSize 缩到 13sp 后主次反转（v0.29 新卡） | **P1** | `UpcomingScreen.kt:360`（62×82）、`:414-442`（右列 ≈85dp）、`:580-593`（22sp→13sp） | 长价格（`HK$1,388.00`）被缩到 13~16sp，比标题（16sp）还小，"最该被一眼看到的信息"反而最弱 | 见 §2.3 三条改法；关键一条：`autoSize` 下限 13→16sp，且 `color` 在缩小后改 `accentText`（`scheme.primary` 在 <18.66sp 不满足 AA） |
| **V12** | 数字没有等宽（tabular）声明，右列宽度随内容抖动（推断） | **P1** | 全项目 `grep fontFeatureSettings/tnum` = **0 命中**；`Type.kt:15` 明确不打包字体 → 数字宽度由 OEM 字体决定 | 详情页计时器每秒跳动时整行可能横向抖（MiSans/OPPO Sans 等比例数字）；价格从 `HK$298` 变 `HK$1,388` 时右列左边缘跳动 | 新增 `FinchNumber` 样式：`fontFeatureSettings = "tnum"`；所有计时/时长/价格/百分比文本套用它。成本极低，收益是"数据不抖" |
| **V13** | 奖牌金属色两套（同语义不同值） | **P2** | `StatsCards.kt:70-72`（金 `#E8B63C`/银 `#A8B0BD`/铜 `#C08A5E`）vs `GameDetailCards.kt:429-431`（`#E6B422`/`#AAB2BD`/`#B0722E`） | 排行徽章与 PSN 奖杯是同一套"金/银/铜"心智，却有两组色，肉眼几乎同色但永远差一点 | 抽 `MedalPalette(gold, silver, bronze, platinum)` 进 `AppThemes.kt`，两处都引用；星级金 `#F5B301` 也收进同一族（→ `medalGold`） |
| **V14** | 死 token：`ScenePalette` 36 个色值 + 两个大字档 | **P2** | `AppThemes.kt:96-104` 定义 6 字段 × 6 主题变体；`grep` 消费方 **0**（`Theme.kt:171/199/226` 只提供不读取）；`scenePalette()` 无调用；`displayLarge`/`headlineLarge` 0 调用 | 不直接影响用户，但让"色板已冻结"这句话失真——改主题时这 36 个值要维护却没人用 | 删 `ScenePalette` 与 `scenePalette()`，或把其中真需要的（`divider`）并入 `FinchColorScheme`；`displayLarge`/`headlineLarge` 同批删 |
| **V15** | 圆角实际 9 档，数据卡与内容卡不同语言 | **P2** | `theme/Shapes.kt:24-38`（5 档）+ `GameDetailCards.kt:79`(30 反向)、`StatsCards.kt:439`(6)/`:493`(3)/`:635`(2)、`BacklogFunnelCard.kt:167`(8) | 热力图的方格是 2~6dp 硬圆角，卡片是连续曲率 24dp，"数据"和"内容"读起来是两套系统 | `FinchShapes` 增 `dataCell: 4dp`（连续曲率），热力图/柱状图统一用它；`sheetBottom = 30dp（下两角）` 收进 `FinchShapes` 替换 `GameDetailCards.kt:79` |
| **V16** | 「正在打折」分组标题的日期是**第一条**的截止日，且与卡内文案重复（v0.29） | **P2** | `UpcomingScreen.kt:272-277`（`items.firstNotNullOfOrNull { it.saleEndsAt }`）+ `:400-408` + `:650-659` | 分组标题写「正在打折 · 至11月20日」，读起来像"这组都是到11月20日"；实际每款截止日不同，卡片里又各写一遍「优惠至11月20日（还剩7天）」 | 分组 trailing 改为条数「N 款」（与其它分组一致）；截止日只在卡内出现，文案压到 `≤2` 天时才用 `error` 强调 |
| **V17** | 折扣截止文案在 130dp 列内必然截断（v0.29） | **P2** | `UpcomingScreen.kt:650-659` 产出「优惠至11月20日（还剩7天）」共 14 全角字符 ≈ 168dp；可用宽 ≈ 130dp（见 `:349-409` 三列分配），且 `maxLines=1` + `Ellipsis` | 用户看到「优惠至11月20日（还剩…」，最关键的天数被省略号吃掉 | 文案改两段：「11月20日 止」+ 单独 `FinchBadge("剩7天")`；或把 `saleEnd` 移到元信息行与平台并列 |
| **V18** | 愿望单返回箭头光学左边缘比标题网格线右偏 13dp | **P2** | `WishlistPane.kt:91-96`（`FinchIconButton` = `minimumInteractiveComponentSize()` 48dp 布局 + 40dp 视觉 + 22dp 图标，`Controls.kt:244-248`）；标题在 `:97-107` 的 18dp 网格 | 返回箭头看着比「愿望单」标题缩进，网格线在页头断了一处（ux 的 C1 只修了"标题被推歪"，没修箭头的视觉对齐） | 给 `FinchIconButton` 加 `opticalInset: Dp = 0.dp` 参数，页头场景传 `-13.dp` 的 `offset(x = …)`，或在 `WishlistPane` 外层加 `Modifier.offset(x = (-13).dp)` |

**合计 18 条：P0 × 3、P1 × 9、P2 × 6；其中 V9 / V11 / V16 / V17 直接针对 v0.29.x 的主页控件区、商店卡与打折分组。**

### 3.1 V1 详情：奖牌白字对比度实算

| 徽章底 | 相对亮度 L | **白字对比度** | 深字 `#241A05` 对比度 | 12sp Bold 需 4.5:1 |
|---|---|---|---|---|
| `RankGold #E8B63C` | 0.533 | **1.80:1** | 9.60:1 | 白 ❌ / 深 ✅ |
| `RankSilver #A8B0BD` | 0.430 | **2.19:1** | 7.90:1 | 白 ❌ / 深 ✅ |
| `RankBronze #C08A5E` | 0.302 | **2.98:1** | 5.79:1 | 白 ❌ / 深 ✅ |
| `RankNeutral #D7DCE2` | — | 用 `onSurface`（`StatsCards.kt:350`）→ 13:1 | — | ✅ |

讽刺的是：**第 4 名以后用的是深色字，反而最清楚**。改法（不用改配色，只改文字色）：

```kotlin
// StatsCards.kt:346-351
color = if (i < 3) Color(0xFF241A05)   // 金 9.6:1 / 银 7.9:1 / 铜 5.8:1
        else MaterialTheme.colorScheme.onSurface
```
若坚持白字，金属色必须压深：金 `#7A5200`（6.9:1）/ 银 `#5C6572`（5.9:1）/ 铜 `#6E3B12`（9.1:1）——但那样"金"就不再像金子了。**推荐深字方案**，金属底保持明亮。

### 3.2 V2 详情：悬浮顶栏的对比度塌陷

`DetailTopBar` 是 `Box` 里的固定层（`GameDetailScreen.kt:275-279`），不随 `verticalScroll` 移动；它的底是 `Color.Black.copy(alpha = 0.28f)`（`GameDetailCards.kt:389`、`:419`），图标 `Color.White`。

滚动到内容区后，它压在 `GlassStyle.Card`（≈白）上：`0.72 × #FFFFFF` → 背景亮度 0.473 → **白图标对比度 ≈ 2.0:1**（图标需 ≥3:1）。

改法（二选一，推荐 ①）：

```kotlin
// ① 顶栏随滚动变实：滚动超过封面高度后切到 Chrome 玻璃
val scrolled = remember { derivedStateOf { scrollState.value > coverHeightPx } }
CoverCircleButton(
    modifier = Modifier.background(
        if (scrolled) MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        else Color.Black.copy(alpha = 0.28f)
    ),
    tint = if (scrolled) MaterialTheme.colorScheme.onSurface else Color.White,
)
// ② 最低成本：黑底 alpha 0.28 → 0.55（白图标在 #707070 上 3.9:1），代价是封面上显得更重
```

### 3.3 V4 详情：数据色阶应该是"单色相多亮阶"，不是"蓝跳紫"

`heatColor()`（`StatsCards.kt:507-517`）的 5 档：`surfaceVariant 0.6` → `primary 0.25` → `0.45` → `0.7` → **`tertiary` 实色**。

问题：前 4 档是同一个蓝的不同透明度（这是正确的数据可视化做法），第 5 档突然跳到**另一个色相**（`#6C5677` 紫）。在截图 `03_stats.png` 里，热力图呈现为蓝格子与紫格子交替，**读起来像两个类别而不是一个量的深浅**——颜色在数据图里是"量"的编码，跨色相就等于换了单位。

改法：

```kotlin
// AppThemes.kt：新增数据色阶，与交互色解耦
val dataScale: List<Color> get() = listOf(
    scheme.primary.copy(alpha = 0.18f),
    scheme.primary.copy(alpha = 0.38f),
    scheme.primary.copy(alpha = 0.58f),
    scheme.primary.copy(alpha = 0.80f),
    lerp(scheme.primary, scheme.onSurface, 0.18f),   // 最高档：同色相聚拢，压深而非换色
)
```
同时 `RhythmCard` 的峰值（`:586`、`:637`）改用 `dataScale.last()`；`tertiary` 只保留「暂停」一个含义。`platformColor`（`HistoryScreen.kt:284-289`）单独建 `PlatformPalette`：PC=蓝、Switch=**青绿** `#2AA9A0`、PS=**靛** `#4B5BD6`、Multi=中性灰——平台是**分类**，用与主题语义无关的固定色才对。

---

## 4. 视觉方向建议（3 选，可叠加）

### 方向 A —「中景补位」：给卡片自己的底色（改 2 个文件，风险中，收益最大）

- **原则**：背景是**氛围**（低饱和、大面积），卡片是**中景**（中性底、明确边缘），hero 是**主角**（唯一允许满饱和的地方）。当前三级里"中景"是空的，所以背景和 hero 在同一层抢。
- **改哪**：`theme/AppThemes.kt:161-174`（`glassLight.tint` 白→`surface@0.72`）、`design/Glass.kt:74-84`（`Card.tintScale 0.22→0.38`、`Tile 0.18→0.30`、`Hero 0.12→0.10`）、`design/FinchAurora.kt:37-39`（光斑半径与 intensity）。
- **预期视觉差异**：卡片从"背景的提亮版"变成"有独立色相的纸面"，玻璃边缘的折射有东西可折；背景光只在卡片缝隙里露出，形成"光从缝里透出来"的层次。整屏的"彩色面积"从 ~100% 降到 ~35%。
- **代价**：玻璃的"透明感"会弱一档（tint 更实），需要老板确认这是否可接受——这是本方向唯一的取舍点。

### 方向 B —「数据与交互分色」：`primary` 只做交互，数据走独立色阶（改 4 个文件，风险低）

- **原则**：一个颜色一个职责。`primary` = 可点/已选中；`dataScale` = 数量大小；`PlatformPalette` = 平台分类；`tertiary` = 暂停（唯一）。数据可视化不允许借语义色。
- **改哪**：`AppThemes.kt` 加 `dataScale` / `MedalPalette` / `PlatformPalette`；`StatsCards.kt:507-517`（`heatColor`）、`:586/:637`（峰值）、`:70-73`（奖牌）；`HistoryScreen.kt:284-289`（`platformColor`）；`UpcomingScreen.kt:463`（倒计时徽章改 `accentText`）。
- **预期视觉差异**：热力图从「蓝紫混排」变成一眼可读的单色深浅；Switch 不再是"主题紫"而是固定的青绿，换主题时平台色不漂移；总结页的色彩数量从 8 种降到 4 种。

### 方向 C —「数字优先的排版系统」（改 1 个新文件 + 12 处调用，风险低）

- **原则**：这个 App 的主角是数字（时长/价格/百分比/名次）。数字要有自己的样式集：等宽、负字距、右对齐、单位自动降一档。
- **改哪**：新建 `theme/Numbers.kt` 定义 `FinchNumber.hero(45sp) / big(28sp) / row(16sp) / unit(11sp)`，统一 `fontFeatureSettings = "tnum"` + `letterSpacing = (-0.02).em`；替换 `HomeCards.kt:143-155`（hero 数字 + 单位）、`StatsCards.kt:124-131/310-322`、`UpcomingScreen.kt:580-603`、`GameDetailCards.kt:177-182`、`FinchStatTile`（`Containers.kt:238-244`）。
- **预期视觉差异**：所有时长/价格的位数对齐（`14h 0m` 与 `200h 0m` 右对齐时不跳动）；计时器每秒刷新不再横向抖；单位（`h`/`m`/`元`）统一降一档，字号跳跃从 2.0× 收到 1.6×。

**建议顺序**：B（纯 token，零风险，先做）→ C（新组件，收益立刻可见）→ A（需要一次视觉评审）。

---

## 5. 可直接补进 `UI_DESIGN_GUIDE.md` 的条款

> 建议插在 §2.5「颜色」之后新增 §2.6「色彩层级」、§2.7「数字排版」，并在 §5 组件速查补 `FinchNumber` 一行。

### 2.6 色彩层级（新增）

- **必须**：一个颜色一个职责。`primary` 只用于**交互**（可点、已选中、主操作）；`dataScale` 只用于**数量大小**；`PlatformPalette` 只用于**平台分类**；`tertiary` 只表示**暂停**。四者不得互相借用。
- **必须**：实色底 + 白字必须先算对比度。当前 `IRIDESCENT` 的 `primary #3D7FFF` + 白 = **3.70:1**（其余两套主题 5.6 / 7.2:1 达标）。小面积实色控件（`FinchChip` 选中、`FinchSegmented`、`FinchButton`）在浅色主题下必须用压深的实色，例如 `#2B63E0`（白字 **5.30:1**，保住色相）或 `lerp(primary, Black, 0.22)` ≈ `#2F63C7`（≈5.1:1，ux P1-3 的方案）；大面积渐变仍可用 `primary`。
  > ⚠️ **与 ux P1-3 合并成同一个 token**（建议名 `onPrimaryStrong`），不要一个 PR 建两个名字。
- **必须**：渐变搭档只有一个系数 —— `accentGradientEnd = lerp(primary, tertiary, 0.34f)`。当前 `Controls.kt` 里 0.3 / 0.4 / 0.45 三个值并存，一律改引用该 token。
- **必须**：金属/奖牌色只允许一套 `MedalPalette(gold, silver, bronze, platinum)`；星级金 `#F5B301` 收进同族。**奖牌底上禁止白字**（金底白字 1.88:1），一律压深棕 `#241A05` 或深灰。
- **必须**：数据色阶 = 同色相多亮阶（`dataScale` 5 档），**禁止**用 `tertiary`/`error` 等语义色表示数值大小。热力图、周/月条、高峰柱共用同一色阶。
- **建议**：新增颜色前先问「它是不是某个已有 token 的假名」——项目里已经有 3 处「同语义两套色」（金属、红、蓝）。

### 2.7 数字与排版（新增）

- **必须**：所有数字文本（时长、价格、百分比、名次、次数）走 `FinchNumber`，带 `fontFeatureSettings = "tnum"`。**理由**：`Type.kt` 不打包字体，数字宽度由 OEM 字体决定，不声明 tnum 时计时器与价格会抖动。
- **必须**：数字与单位的字号比 = **2.6 : 1**（如 45sp 数字 + 17sp 单位 / 28 + 11 / 16 + 11），单位与数字基线对齐、颜色降一档（`onSurfaceVariant`）。
- **必须**：同一行的数字列必须等宽或定宽右对齐（`Modifier.width(62.dp) + TextAlign.End`，先例 `StatsCards.kt:597`）。
- **必须**：**中文标题不用负字距**。`headlineMedium` 字距归零、行高 33→35；`titleLarge` 保持 0。负字距只属于拉丁数字（`FinchNumber`）。
- **必须**：中文大标题（≥32sp）字重封顶 **SemiBold**；`Bold` 在 32sp 以上中文会糊成块。
- **建议**：删除 `displayLarge` / `headlineLarge` 两个 0 调用档位，避免下一个人拿它排中文。

### 2.8 卡片档位（补 §2.4 形状表）

- **必须**：卡片只有三档，且「档位 = 玻璃 style + 圆角」一一绑定，不得交叉：

  | 档 | 用途 | GlassStyle | 圆角 | 高度 |
  |---|---|---|---|---|
  | 行卡 | 主页游戏行、商店条目、记录会话 | `Card` | `card`(24) | 96 / 98 / 72 |
  | 辅助卡 | 愿望单汇总、卡内嵌块、`FinchStatTile` | `Tile` | `tile`(18) | 56~72 |
  | hero | 主页/统计页大数字 | `Hero` | `hero`(32) | 196 / 188 |

  当前 `StoreEntryCard` 是 `Tile + card(24)`、`HistoryScreen` 是 `Tile + tile(18)`、`GameRowCard` 是 `Card + card(24)`——三种组合，全部按上表归位。
- **必须**：`SolidPanel`（关玻璃）的 alpha 映射写进规范并与 glass 档对齐：`Card 0.46 / Tile 0.38 / Hero 0.30 / Chrome 0.60`。当前 `Card` 是 0.58 对 glass 的 0.145（**4 倍落差**），开关一按整个明度系统被换掉。
- **必须**：`SolidPanel` 只允许用主题已定义的 `surface` / `surfaceVariant`，**禁止** `surfaceContainer*`（M3 基线默认值带紫调，与 `#F5F7FB` 不是一个色系，`Glass.kt:259/269` 是存量）。

### 2.9 页头与控件区（补 §6.1）

- **必须**：页头 action 一律 `ghost`（无底 + 48dp 命中），玻璃圆底只用于页头之外的独立操作。当前主页 2 ghost + 1 玻璃混排（`HomeScreen.kt:391/457`），日程 3 个全玻璃（`UpcomingScreen.kt:122-155`）。
- **必须**：页头 action ≤ 2 个；第 3 个入口收进 `FinchDropdownMenu` 或图标组。日程页 3 个按钮占 144dp，把副标题压到 180dp 折行（ux P2-8 已记）。
- **建议**：`FinchHeader.subtitle` 加 `maxLines = 1`；副标题不复述下方控件已有的信息（数据源、筛选状态）。

### 2.10 反模式追加（补 §七）

| # | 反模式 | 正确做法 |
|---|---|---|
| 14 | 白字压在中亮金属/彩色底上（金/银/铜、`#E5484D`） | 压深色字，或先算 ≥4.5:1 |
| 15 | 用 `tertiary`/`error` 表示数量大小 | `dataScale` |
| 16 | 同一语义在多个文件各写一份色值（金属、红、星金） | 收进 `AppThemes.kt` 一族 token |
| 17 | `lerp(primary, tertiary, x)` 在调用点算渐变终点 | `accentGradientEnd` |
| 18 | 40dp 视觉按钮直接铺在黑透明底上当悬浮层（滚动后会压在浅色内容上） | 随滚动切换实底，或底 alpha ≥0.55 |

---

## 6. 明确不动（本报告任何改动都不得触碰）

- `design/Glass.kt` 的折射配方：`vibrancy/blur(blurDp)/lens(depthEffect, chromaticAberration)`、`GLASS_CHROMATIC`、`MAX_GLASS_DEPTH`、`Shadow/Highlight` 参数。**方向 A 只动 `tintScale` 与 `tint` 色源，不动效果链。**
- `design/FinchBackdrop.kt`、`design/FinchAurora.kt` 的**结构与种子**（`Random(20261005)`、620 颗粒）——方向 A 只调 `AppThemes` 里的光斑色值与 `FinchAurora.kt:37-39` 的三个半径/intensity 入参。
- `FinchAppScaffold` 三层 backdrop 组合、`FinchBottomBar` 的 Liquid 胶囊与选中滑块。
- `FinchShapes` 已有 5 档半径的数值（方向 B/V15 是**新增** `dataCell`，不改既有值）。
- `FinchMotion` 五档弹簧参数。

---

## 7. 附：需真机确认的两点

1. **玻璃叠加后的实际对比度**：`onSurfaceVariant #4F5866` 在 `#F5F7FB` 上静态值 ≈6.7:1（达标），但玻璃会采样氛围光，深色自定义背景图下可能掉到 4:1 附近。ux.md 附录 1 已提，这里补充：**最容易翻车的位置是 `FinchBadge`（0.14 alpha 底 + 1dp 0.2 alpha 描边）压在玻璃上**，建议用取色器在"深色自定义背景 + 浅色主题"下实测一次。
2. **数字抖动**：V12（`tnum`）是按"OEM 字体可能用比例数字"的推断提出的。请在目标机型（尤其小米/OPPO/华为）详情页观察计时器 `00:00:00` 每秒跳动是否横向抖动——若抖，则必然要加 `tnum`；若不抖，该条降为"防御性规范"。
