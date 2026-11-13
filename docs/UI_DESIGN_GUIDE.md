# Finch UI 设计指导

| 项目 | 内容 |
|---|---|
| 应用 | Finch（`dev.cao.finch`）游戏库 / 时长追踪 |
| 文档定位 | 设计系统的**使用规范**：沉淀已形成的设计口径，供新页面、新组件、重构时对照执行 |
| 配套文档 | [UI_REVIEW_2026-11-12.md](UI_REVIEW_2026-11-12.md)（问题清单，14 项中 13 项已落地，唯一剩余 C3 骨架屏）、[CODE_REVIEW_2026-11-12.md](CODE_REVIEW_2026-11-12.md)（性能 / 安全，与本文互不重叠） |
| 最近更新 | 2026-11-12 |

> 本文所有规则都能在 `app/src/main/java/dev/cao/finch/ui/` 下找到对应实现。
> 规则分两级：**必须**（违反即技术债）与**建议**（偏离需在 PR 里说明理由）。

---

## 一、五条设计原则

1. **内容优先，玻璃退后。** 玻璃是氛围层，不是视觉主角。任何一屏的纵向预算里，控件区（筛选 / 搜索 / 分段）不应超过 ~100dp，列表内容才是主任务（审查 B1 的教训：曾占 ~260dp，首屏只剩 1~2 张卡）。
2. **背景必须静态。** 氛围背景只画一次，绝不逐帧重录——`layerBackdrop` 每帧重新采样，是全 App 最大的帧成本来源（见 §4）。
3. **令牌唯一口径。** 间距、圆角、动效、颜色只从 `FinchSpace` / `FinchShapes` / `FinchMotion` / `FinchColorScheme` 取，禁止散写字面量。发现一个就顺手收进令牌。
4. **单手可达。** 底部是高频操作区；点击命中区 ≥ 48dp；删除等危险操作远离拇指热区并需二次确认。
5. **三态齐全。** 列表页必有 加载 / 空 / 错 三态（`FinchLoading` / `FinchEmptyState` / 带重试的空态），不允许整页空白转圈常驻（C3 正在修）。

---

## 二、设计令牌

### 2.1 间距 —— `ui/design/Tokens.kt` 的 `FinchSpace`

| Token | 值 | 用途 |
|---|---|---|
| `xxs / xs / sm / md / lg / xl / xxl / xxxl` | 2 / 4 / 8 / 12 / 16 / 20 / 24 / 32 dp | 通用间距档位，**默认用 md(12) 作卡片间距、sm(8) 作组内间距** |
| `screen` | 18 dp | 页面左右边距（所有 tab 统一） |
| `bottomBarClearance` | 132 dp | 列表底部留高（让开底栏） |
| `fabOffset` | bottomBarClearance − 24 | FAB 距底距离 |
| `snackbarClearance` | bottomBarClearance + 40 | Snackbar 距底距离 |
| `fabSize` | 56 dp | FAB 尺寸 |

**必须**：
- 页边距写 `FinchSpace.screen`，不写 16.dp / 18.dp 字面量。
- 列表 contentPadding 的 bottom 一律用 `bottomBarClearance` / `snackbarClearance` 派生（审查 C4 的教训：132/172/108/56 四处散落，改底栏高度必踩坑；已 token 化）。
- 新增「全局性偏移」时加进 `FinchSpace` 并由 `bottomBarClearance` 派生，不在调用点算。

**例外白名单**（代码中允许存在的字面量）：卡片内部 2dp 微调（文字基线对齐）、角标 15dp、封面缩略 96×72 等**内容自身尺寸**。判断是否该收进令牌的标准：*换主题/改底栏时它该不该变？该变 → 令牌。*

### 2.2 动效 —— `FinchMotion`

| Token | 阻尼/刚度 | 时长 | 用途 |
|---|---|---|---|
| `press` | 0.78 / 900 | — | 按压缩放（`finchClickable` 内置，scale 0.97） |
| `enter` | 0.88 / 480 | — | 卡片 / 区块入场（`Modifier.finchEnter()`） |
| `page` | 0.9 / 360 | — | 页面级滑动（pageOffset） |
| `settle` | NoBouncy / MediumLow | — | 需要稳定落位的滑动 |
| `bouncy` | 0.6 / 260 | — | 俏皮回弹，**只在刻意强调时用** |

**必须**：
- 按压反馈一律走 `finchClickable`（ripple + 0.97 缩放），不自绘 `scaleClickable`。
- 列表卡片入场走 `finchEnter()`；滚动新入视野的项 `order = 0` 不错开（依次出现再加延迟像卡顿，见 `GameRowCard` 注释）。
- **禁止 `rememberInfiniteTransition`**（全项目目前 0 处，保持）。
- `liquid/` 包内的自定义 spring/tween 是 Liquid 控件**专属手感**，放行不动。

**建议**：`dampingRatio` 只允许上表五档；新增档位先加进 `FinchMotion` 再引用。

**按压反馈三要素**（2026-11-13 第二次修订，先例见 `ui/liquid/LiquidButton.kt`）：
- **提亮**：按下时玻璃表面随 pressProgress 提亮（表面叠白 ~0.14α）。短暂点击也必须可感知——「只有长按才有反馈」一律视为缺陷。
- **形变**：Liquid 组件按指针位置做胶囊形变/位移（保持上游配方，不额外加缩放）。
- **回弹**：松开时按下进度与位置以弹簧回弹。回弹是反馈的一部分，只亮不回弹同样算残缺。
- **实现红线（已两次踩坑，务必照做）**：
  1. **按下进度必须由 `interactionSource` 驱动**（`collectIsPressedAsState` → `animateFloatAsState`），把同一个 source 传给 `clickable`。按下=Press、抬手=Release、滑出=Cancel 全由 Clickable 的交互状态机保证，**不依赖任何指针事件是否被消费**。
  2. **禁止用自绘手势推断「按下/松开」**。`inspectDragGestures` 一类自绘循环在同链存在 `clickable` 时，结束判定会被消费状态影响：判「未消费的 up」→ 点击收不到 onDragEnd（无高光）；只判 `!pressed` → 抬起与取消又分不清，按下态会卡在屏幕上（「按下去就僵住」）。两次故障同源。
  3. 指针位移可以继续在 `PointerEventPass.Initial` 阶段**只读**跟踪（只喂液形拉伸，不参与结束判定）。
- **手感**：按下起亮要快（刚度 ≥900，约 60ms 到位），回落略带回弹（dampingRatio 0.7 左右）。用慢弹簧做按下，短按会「看起来没反应」。

> ⚠️ **死代码提醒**：`ui/Motion.kt`（包名 `ui.theme`）的 `springEnterSpec / springPageSpec / springPressSpec / pressScale / emphasizedTween` 已无任何调用方，且阻尼口径（MediumBouncy 等）与 `FinchMotion` 不一致。可整文件删除，勿再引用。

### 2.3 字体 —— `ui/theme/Type.kt` 的 `FinchTypography`

M3 全档位微调：display 57/45/38sp（负字距）、headline 32/27/23、title 20/16/14、body 16/14/12、label 14/12/11。

**必须**：
- 字号只引用 `MaterialTheme.typography.*`，不写 `fontSize = 13.sp`（如需新档位，加进 `Type.kt`）。
- 中文不引自定义字体（保持系统字体，不打包字重）。
- **大字号适配是三选一，按场景**（审查 A1 的核心规则）：
  | 场景 | 手段 | 先例 |
  |---|---|---|
  | 文字主导的容器 | `heightIn(min = …)` 让高度随内容，或 fontScale 缩放定高（上限 ≤1.45x） | `MonthHeroCard`、`HeroTotalCard` |
  | 数字会被数据撑爆 | `TextAutoSize` 自动缩字 | `FinchStatTile`（10–21sp） |
  | 横向空间 | 允许折行 / `FlowRow`，宽度也随 fontScale | `UpcomingCard` 右列（`rightScale`） |
- 文本最大行数配 `TextOverflow.Ellipsis` 时，确认截断不丢关键信息。

### 2.4 形状 —— `ui/theme/Shapes.kt` 的 `FinchShapes`

连续曲率圆角（kyant 曲线）：`hero` 32 / `card` 24 / `sheet` 30-top / `tile` 18 / `field` 16 / `thumb` 12 / `pill` / `inset` 20。

**必须**：
- 禁止裸 `RoundedCornerShape(x.dp)`——全项目现有 5 处（`GameDetailCards.kt`×1、`StatsCards.kt`×3、`report/BacklogFunnelCard.kt`×1、`FinchBottomBar.kt` 的 `SolidBottomBar`×1）是存量，新代码不许新增；动到相关文件时顺手迁移。
- 组件与形状对应：大卡片 `card`、hero `hero`、缩略图 `thumb`、输入框 `field`、小图块 `tile`、标签/按钮 `pill`、底部弹层 `sheet`。

### 2.5 颜色 —— `ui/theme/AppThemes.kt`

三套风格主题：`IRIDESCENT`（琉璃玻璃，默认）/ `MIDNIGHT`（暮色玻璃）/ `PAPER`（经典纸感）。每套 = `ColorScheme` + `GlassPalette`（tint/highlight/shadow/edge/overlay）+ `ScenePalette`（divider/cardBorder/switchActive/switchThumb/numericGlow/monthGlow）+ `AmbientPalette`（氛围光）。

**必须**：
- 颜色从 `MaterialTheme.colorScheme` 或 `finchBundle()` 取，禁止 `Color(0xFF…)` 进 UI 代码（`StatsCards.kt` 的金银铜排名色是**内容语义色**白名单）。
- **小号强调文字用 `accentText`**：浅色 = `lerp(primary, onSurface, 0.28)`（~8:1）、深色 = `primary`（~9:1）。`FinchBadge` 与所有 ≤11sp 的强调文字必须走它，不许直接 `primary`（审查 A2：#3D7FFF 在浅底上仅 3.4:1，WCAG AA 不达标）。
- 语义色用途：`error` 只给危险操作；`tertiary` 表暂停/次要态；正向涨跌用 `primary` 系，不用绿。

---

## 三、玻璃系统（`ui/design/Glass.kt`）

### 3.1 四档 GlassStyle 怎么选

| 档位 | 用途 | 视觉 |
|---|---|---|
| `Chrome` | 底栏、Snackbar、悬浮控件 | 最透、折射最强 |
| `Card` | 列表卡片、行容器（默认档） | 均衡 |
| `Tile` | 卡片内嵌小块、分段控件底 | 更实 |
| `Hero` | 顶部大 hero 卡（底色最淡） | 让氛围背景透上来 |

**必须**：
- 卡片容器默认 `GlassSurface(style = GlassStyle.Card, shape = FinchShapes.card)`；不要自绘 `Box + background(Color)` 当卡片（PAPER 主题下会和玻璃体系打架）。
- **四档参数（tintScale/blurDp/refraction/sheen/edgeAlpha）、`drawBackdrop` 的 vibrancy/blur/lens 配方、`MAX_GLASS_DEPTH = 1` 全部冻结**，任何优化需求先讨论（已写进两份审查报告的「不动的部分」）。
- 卡内控件**只允许一层玻璃**（深度 ≤1）：`MAX_GLASS_DEPTH` 只导出卡片层，卡内再放 `GlassSurface` 直接违反。卡内嵌块用 `Tile` 档或实色。
- Dialog / BottomSheet 是独立窗口采不到 App 背景：用**高不透明度渐变**模拟玻璃（`Containers.kt` 已封装），不要尝试跨窗口采样。
- `LocalGlassEnabled` = false 时自动走 `SolidPanel` 零采样降级：新组件必须双路都验证，尤其**文字对比度**（Solid 底色更深/更浅都可能翻车）。

### 3.2 采样源（`FinchAppScaffold`）

三层 backdrop：**`backgroundBackdrop`**（纯背景）→ 内容层 → **`contentBackdrop`**（内容）→ **`glassBackdrop`**（组合，专供底栏）。

**必须**：玻璃表面只采 scaffold 提供的源；新增全屏层时想清楚它属于哪一层，别把内容层画进背景源。

---

## 四、背景系统（`FinchBackdrop.kt` / `FinchAurora.kt`）

三种氛围背景：
- **AURORA**：三团光斑 + 620 颗粒，固定种子 `Random(20261005)`
- **PS5**：粒子星空 260 颗，固定种子 `Random(20261103)`，静态一帧
- **CUSTOM**：用户图片 + 主题压色

**必须**：
- 背景**只画一次、永不逐帧重录**。需要动画（入场淡入等）改透明度，不改采样。
- 随机种子**固定**，保证每次进入同一主题视觉一致；改种子 = 改主题观感，属设计决策。
- 新背景类型必须走 `FinchBackdrop` 调度，不私下 `Canvas(drawBehind)` 画全屏效果。

---

## 五、组件速查（`ui/design/`）

| 需求 | 用 | 规格要点 |
|---|---|---|
| 页面级容器 | `FinchCard` / `GlassSurface` | Card 档 + card 圆角 |
| 页头 | `FinchHeader` | 标题 + labelMedium 副标题 + actions 图标区；**返回键单独一行**，不与标题同 Row（审查 C1：同 Row 会推歪标题 44dp） |
| 分组标题 | `FinchSectionTitle` | onSurface + SemiBold，计数用 `FinchBadge` |
| 统计数字 | `FinchStatTile` | TextAutoSize 10–21sp，长数据不炸 |
| 行容器 | `FinchListRow` | 统一行高与内边距 |
| 按钮 | `FinchButton` | Primary/Danger → LiquidButton 48dp（compact 36），按下给足提亮+形变+回弹（§2.2 三要素）；Glass → InsetSurface；Ghost → 纯文字 |
| 图标按钮 | `FinchIconButton` | 40dp 视觉 + `minimumInteractiveComponentSize` 48dp 命中区 |
| 选择 | `FinchChip` / `FinchSegmented` / `FinchCheckbox` / `FinchSwitch` | Chip 高约 44dp；Segmented 46dp 滑动高亮 |
| 输入 | `FinchTextField` | field16；glassOn 时透明底「刻在玻璃上」 |
| 滑杆 | `FinchSlider` | LiquidSlider |
| 反馈 | `FinchDialog`(maxWidth 460) / `FinchConfirmDialog` / `FinchSheet` / `FinchSnackbarHost`(pill, Chrome) / `FinchDropdownMenu` | 危险操作必须 ConfirmDialog |
| 状态 | `FinchLoading` / `FinchEmptyState` | 空态必须有 icon + 标题 + 一句指引 + 可选 action |
| 评分 | `FinchStars` | 30dp 星 + 8dp 内衬 + 48dp 命中 |
| 徽标 | `FinchBadge` | accentText，≤11sp |
| 折叠分组 | `FinchCollapsibleGroup` | 「已发售」这类历史信息默认收起（审查 B3 的落地形态） |
| 底栏 | `FinchBottomBar` | LiquidBottomTabs；glassOff → `SolidBottomBar` 64dp 同款布局 |

**必须**：新交互元素先查上表有没有；确实没有再新造，且新组件按 `Controls.kt` / `Containers.kt` 的写法补双路（玻璃 / Solid）与 48dp 命中。

---

## 六、页面模式

### 6.1 首屏预算（信息架构的硬约束）

1080×2400 机型可用高度约 700dp。从上往下：页头 ~58dp → hero ~196dp → **控件区 ≤100dp** → 列表。筛选多于两类就收进 `Tune` 图标 + `FinchSheet`（角标显示生效数），搜索收进页头放大镜按需展开（主页、日程页已是此模式）。

### 6.2 列表三态

| 态 | 做法 |
|---|---|
| 加载 | **首进**骨架（C3 待做：3 张同高灰块）；**切换/刷新**保留旧列表 + 顶部 2dp 进度条 |
| 空 | `FinchEmptyState` + 行动指引（「添加游戏」按钮） |
| 错 | 空态变体 + 「重试」action |

### 6.3 一致性

- 所有列表页支持下拉刷新（主页、日程页已齐；新列表页默认加 `PullToRefreshBox`）。
- 数据源 chips 横向滚动一行；分组顺序按「对用户接下来最有用」排，过期的（已发售）沉底或默认折叠。
- 页面入口（如愿望单）放页头图标 + 计数角标，**不放在列表 item 0**（会滚走，审查 B5）。
- 删除等危险操作：可见入口（卡片「更多」菜单）+ 长按快捷 + `FinchConfirmDialog`，三选二即可（D2 落地形态）。

---

## 七、反模式清单（见即改）

| # | 反模式 | 正确做法 |
|---|---|---|
| 1 | 裸 `RoundedCornerShape(x.dp)` | `FinchShapes.*` |
| 2 | 散写 `NN.dp` / `NN.sp`（间距、字号、圆角） | 对应令牌 |
| 3 | 直接 `primary` 做 ≤11sp 文字色 | `finchBundle().accentText` |
| 4 | 40dp 以下裸点击区 | `minimumInteractiveComponentSize()` |
| 5 | 卡内套玻璃（深度 >1） | Tile 档 / 实色 |
| 6 | 自绘实色 Box 当卡片 | `GlassSurface` / `FinchCard` |
| 7 | 背景逐帧动画 / 无限循环动画 | 一次性静态 + 透明度动画 |
| 8 | `if (数据.size > N) 显示搜索` 这类数量阈值显隐 | 入口恒定（图标形态），展开才占位 |
| 9 | 整页 `FinchLoading` 顶掉内容 | 骨架 / 保留旧内容 + 顶部进度 |
| 10 | 危险操作一键直达（无确认） | `FinchConfirmDialog` |
| 11 | 返回键与标题同 Row | 返回键独立一行（`WishlistPane` 模式） |
| 12 | 引用 `ui/Motion.kt` 的 legacy spring API | `FinchMotion.*`（该文件待删） |
| 13 | 按下反馈只有长按才可见／按下去僵住不回来 | 按下进度由 `interactionSource` 驱动，不自行推断手势结束（§2.2 按压三要素红线） |

---

## 八、新页面落地 Checklist

- [ ] 页边距 `FinchSpace.screen`，卡片间距 md，组内 sm
- [ ] 页头 `FinchHeader`，图标 actions；搜索/筛选按需展开；首屏控件区 ≤100dp
- [ ] 容器 `GlassSurface(Card)` + `FinchShapes.card`；组件选中 §五 速查表
- [ ] 三态齐全；下拉刷新；错误可重试
- [ ] 所有文字色过对比度：正文 onSurface / onSurfaceVariant ≥4.5:1，小字强调 accentText
- [ ] 所有可点区 ≥48dp 命中；危险操作带确认
- [ ] 入场 `finchEnter()`；按压 `finchClickable`；无无限动画
- [ ] 列表 bottom padding 用 `bottomBarClearance` 系令牌
- [ ] 大字号 1.3x 走查一遍（系统设置里直接调）
- [ ] glassOff（PAPER / 关玻璃）走查一遍：对比度、层级仍成立
- [ ] 中文文案过一遍：无歧义词（「待打折」→「未打折」这类）
- [ ] 引用令牌而非字面量；未动 §七 任何反模式

---

## 九、遗留与后续（截至 2026-11-12）

| 项 | 状态 | 说明 |
|---|---|---|
| C3 骨架屏 | **唯一未修** | `UpcomingScreen.kt` Loading 仍是整页 `FinchLoading`；计划：首进 3 张 120dp 灰块骨架，切换源保留旧列表 + 顶部 2dp 进度条 |
| A1 上限 | 折中已修 | hero 定高随 fontScale 缩放、上限 1.45x；**>1.45x 仍截断**。更彻底的做法是 `heightIn(min)` 或 `FinchStatTile` 式 TextAutoSize，改时一并评估 |
| `ui/Motion.kt` | 待删 | 死代码整文件可删（见 §2.2） |
| 裸 `RoundedCornerShape` ×5 | 存量 | 见 §2.4，触碰时迁移 |
| 统计页期数导航 | 已修 | 滑杆已删，改 ArrowBack 翻期；若需要远跳再加月份网格弹层（低优先） |
