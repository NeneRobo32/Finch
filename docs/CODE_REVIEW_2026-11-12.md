# Finch 代码审查报告 —— 性能与安全隐患

| 项目 | 内容 |
|---|---|
| 应用 | Finch（`dev.cao.finch`）游戏时长追踪 |
| 版本 | v0.29.3 / versionCode 101 |
| 审查范围 | `app/src/main/java/dev/cao/finch/**`（124 个 Kotlin 文件，约 21,095 行）+ `AndroidManifest.xml` + `app/build.gradle.kts` + `res/xml/*` + 备份/迁移/Schema |
| 审查日期 | 2026-11-12 |
| 审查方式 | 全量静态阅读（数据层 / 网络层 / 同步引擎 / 备份恢复 / 计时服务 / Compose UI），非抽样 |

---

## 一、结论速览

整体工程质量高于同类个人项目：凭据走 Android Keystore 加密、22 条手写迁移链完整、备份带 schema 交叉校验、FileProvider 权限收窄到 `cache/share/`、无 TrustManager 覆盖、无 WebView、无明文 http 常量、DAO 聚合全部下推到 SQL。

**未发现高危（RCE / 任意文件读写 / 明文硬编码密钥）问题。** 共确认 4 项安全缺陷、4 项性能缺陷、4 项低优先级健壮性问题。

### 风险矩阵

| 编号 | 类型 | 标题 | 严重度 | 影响面 | 修复成本 |
|---|---|---|---|---|---|
| S1 | 安全 | WebDAV 允许 `http://`，Basic 认证明文出网 | 中 | 配置云备份的用户 | 低 |
| S2 | 安全 | `steamBaseUrl` 可填任意地址，API Key 随之外泄 | 中 | 手动填反代地址的用户 | 低 |
| S3 | 安全 | 备份恢复无大小上限（zip 膨胀 / 整包进内存） | 中低 | 导入备份 / 云端恢复 | 中 |
| S4 | 安全 | 自动备份 zip 未排除出系统云备份 | 低 | 开启自动备份 + 云备份 | 极低 |
| P1 | 性能 | `games` 表零索引，按名查询在循环中反复全表扫 | **高** | 每次同步 / 每次刷新日程 | 低 |
| P2 | 性能 | 下拉刷新串行跑三个数据源 | 中高 | 每次下拉刷新 | 低 |
| P3 | 性能 | 日程页切源并发拉取，旧结果覆盖新源 | 中 | 连续切换数据源 | 低 |
| P4 | 性能 | 计时服务每秒空转唤醒 | 中 | 计时全程续航 | 低 |
| C1 | 健壮性 | 恢复后 `Runtime.exit(0)` 跳过生命周期与 SP flush | 中低 | 备份恢复 | 中 |
| C2 | 健壮性 | `TimerService.onDestroy` 不清 `TimerServiceBridge` | 低 | 服务被单独销毁 | 低 |
| C3 | 安全 | Keystore 不可用时静默降级明文存储 | 低 | 极少数设备 | 低 |
| C4 | 性能 | `PsnStoreClient` 自建 Dispatcher 常驻线程池 | 极低 | — | 可选 |

---

## 二、安全问题

### S1 — WebDAV 允许 `http://`，Basic 认证与整包备份明文出网

**位置**：`data/WebDavClient.kt:39-48`（`normalizeBase`）、`ui/SettingsCloud.kt:66-72`（仅文字提示）

**代码证据**

```kotlin
fun normalizeBase(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    val withScheme = when {
        trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        trimmed.startsWith("http://", ignoreCase = true) -> trimmed   // ← 明文放行
        else -> "https://$trimmed"
    }
    return withScheme.trimEnd('/') + "/"
}
```

`authHeader()` 使用 HTTP Basic：

```kotlin
fun authHeader(username: String, password: String): String = Credentials.basic(username, password)
```

**影响**

1. 密码以 `Base64(user:password)` 形式随每个请求发出，`http://` 下等价明文，同网段可抓包还原。
2. 传输内容是**全量游玩历史数据库 zip**（游戏名、时长、备注、评分、感想），属于个人行为画像。
3. UI 只有一句「建议填 https 地址」的弱提示，没有任何阻断或确认；用户按「局域网自签端口」的直觉填 `http://192.168.x.x:5005/` 是最常见路径。

**触发条件**：用户把目录地址填成 `http://` 开头（或只填 IP、走自签端口后手动改协议）。

**修复方案**（推荐：私网放行 + 公网阻断 + 显式确认）

```kotlin
/**
 * 归一化目录地址：补协议 → 校验 scheme。
 * https 直接放行；http 仅在私网/localhost 下允许（局域网自签端口场景），
 * 公网 http 直接拒绝 —— Basic 认证在 http 下等于把密码写在信封上。
 */
fun normalizeBase(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    val withScheme = when {
        trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        trimmed.startsWith("http://", ignoreCase = true) -> trimmed
        else -> "https://$trimmed"
    }
    val normalized = withScheme.trimEnd('/') + "/"
    require(isTransportSafe(normalized)) {
        "公网地址必须用 https（Basic 认证在 http 下是明文），请改填 https:// 开头的地址"
    }
    return normalized
}

/** http 只允许 localhost / 私网段（RFC1918 + 链路本地） */
internal fun isTransportSafe(url: String): Boolean {
    if (!url.startsWith("http://", ignoreCase = true)) return true
    val host = runCatching {
        java.net.URI(url).host?.lowercase().orEmpty()
    }.getOrDefault("")
    if (host.isEmpty()) return false
    if (host == "localhost" || host == "127.0.0.1" || host == "::1") return true
    if (host.startsWith("192.168.") || host.startsWith("10.")) return true
    if (host.startsWith("172.")) {
        val second = host.split('.').getOrNull(1)?.toIntOrNull() ?: return false
        return second in 16..31
    }
    return false
}
```

`saveWebDavConfig()` 里 `require` 抛出的 `IllegalArgumentException` 已被现有 `cloudErrorText` 的兜底分支覆盖，改造成本极低。若要保留「用户坚持用公网 http」的自由，改为弹确认框并把结果作为 `Config.allowInsecure` 显式传入，禁止隐式放行。

**验证**：`testDebugUnitTest` 已覆盖 `normalizeBase` 的纯函数测试，补 3 个用例——`http://192.168.1.5/dav/` 通过、`http://dav.example.com/` 抛异常、`https://dav.example.com/` 通过。

---

### S2 — `steamBaseUrl` 可填任意地址，Steam API Key 随之发往任意 host

**位置**：`data/SettingsStore.kt:23-25`（无校验）、`ui/ImportViewModel.kt:70-78`（`saveSteamConfig` 只做 `ifBlank` 兜底）

**代码证据**

```kotlin
var steamBaseUrl: String
    get() = sp.getString(KEY_STEAM_BASE, DEFAULT_BASE) ?: DEFAULT_BASE
    set(value) = sp.edit().putString(KEY_STEAM_BASE, value.trim()).apply()   // ← 无 scheme 校验
```

```kotlin
fun saveSteamConfig(key: String, id: String, base: String) {
    settings.steamApiKey = key
    settings.steamId = id
    settings.steamBaseUrl = base.ifBlank { SettingsStore.DEFAULT_BASE }      // ← 只判空
    ...
}
```

**影响**

1. **明文泄露**：填 `http://<反代域名>` 时，`key=<Steam Web API Key>` 作为 query 参数明文出网，中间人可完整获取并冒用（读取库、拉时长，产生配额消耗）。
2. **钓鱼面**：该字段的语义是「Steam API 反代地址」，用户会主动从网上抄第三方地址填入。恶意地址可直接收割 Key 与 SteamID，形成「一次配置、长期偷取」。
3. Key 是长期凭据（Steam 后台不自动轮换），泄露后用户往往不会察觉。

**触发条件**：用户在导入页把「API 地址」改成任意值（默认 `https://api.steampowered.com` 是安全的）。

**修复方案**：保存时强制 https，非法值拒绝并提示；读取时对历史脏值做一次兜底。

```kotlin
/** 校验 Steam API 基址：必须是 https，且主机名非空（允许自建反代，但必须走 TLS） */
private fun normalizeSteamBase(raw: String): String {
    val trimmed = raw.trim().ifBlank { SettingsStore.DEFAULT_BASE }
        .trimEnd('/')
    val uri = runCatching { java.net.URI(trimmed) }.getOrNull()
        ?: throw IllegalArgumentException("API 地址格式不对，示例：https://api.steampowered.com")
    if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank()) {
        throw IllegalArgumentException("API 地址必须是 https（http 会让 Steam Key 明文出网）")
    }
    return trimmed
}
```

读取侧（防止老库里的 http 脏值继续生效）：

```kotlin
var steamBaseUrl: String
    get() = sp.getString(KEY_STEAM_BASE, DEFAULT_BASE)
        ?.takeIf { it.startsWith("https://", ignoreCase = true) }
        ?: DEFAULT_BASE
    set(value) = sp.edit().putString(KEY_STEAM_BASE, normalizeSteamBase(value)).apply()
```

**验证**：单测 `normalizeSteamBase`——`http://x.com` 抛异常、`https://x.com/` 去掉尾斜杠、空串回落默认值。

---

### S3 — 备份恢复无大小上限：zip 膨胀与整包读入内存

**位置**：`data/BackupManager.kt:265-286`（`unzipDb`）、`data/WebDavClient.kt:139-155`（`download`）

**代码证据**

```kotlin
ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
    while (true) {
        val entry = zip.nextEntry ?: break
        if (entry.name == DB_ENTRY) {
            out.outputStream().use { zip.copyTo(it) }   // ← 无字节上限
            found = true
        }
        ...
```

```kotlin
resp.body?.bytes() ?: throw IOException("云端返回空文件")   // ← 整包进堆
```

**影响**

1. **磁盘写爆**：手工构造的 zip 可声明几十 GB 的解压尺寸，`copyTo` 会把 `cacheDir` 写到系统报「存储空间不足」，可能连带影响同分区其他应用；解压失败时 `restored-finch.db` 残留在 cache。
2. **OOM**：`WebDavClient.download` 把整个 zip 读成 `ByteArray` 再 `tmp.writeBytes(bytes)`，堆上同时存在两份（约 2× zip 大小）。云端目录被换文件（网盘共享误操作 / 账号被盗）即崩溃。
3. 现有链路只校验「zip 里有 meta.json + SQLite 文件头 + 核心表齐全」，**校验发生在解压之后**，膨胀攻击在校验之前就已发生。

**修复方案**：先限尺寸再解压，下载改流式落盘。

```kotlin
/** 单份备份的尺寸上限：正常库 < 50MB，留 4 倍余量 */
private const val MAX_DB_BYTES = 200L * 1024 * 1024

// unzipDb：边读边累计，超限即中止
out.outputStream().use { os ->
    val buf = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val n = zip.read(buf)
        if (n < 0) break
        total += n
        if (total > MAX_DB_BYTES) throw IOException("备份文件异常（解压后超过 200MB），已中止")
        os.write(buf, 0, n)
    }
}
```

```kotlin
// WebDavClient.download → 返回临时文件，避免整包进内存
suspend fun downloadTo(config: Config, name: String, target: File): Long =
    runInterruptible(Dispatchers.IO) {
        // ... 同现有请求构造
        resp.body?.byteStream()?.use { input ->
            target.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_DB_BYTES) throw IOException("云端文件异常（超过 200MB），已中止")
                    output.write(buf, 0, n)
                }
                total
            }
        } ?: throw IOException("云端返回空文件")
    }
```

`ImportViewModel.restoreCloudBackup()` 相应改为「下载到 `cacheDir/cloud-finch.zip` → `BackupManager.restore(context, db, uri)`」，复用现有 SAF 恢复路径，顺带消掉 `restoreFromBytes` 这条重复链路。

**验证**：单测构造一个 `entry.size` 超限的 zip，断言抛 `IOException` 且 `cacheDir` 无残留文件。

---

### S4 — 自动备份 zip 未排除出系统云备份

**位置**：`res/xml/backup_rules.xml`、`res/xml/data_extraction_rules.xml`、`AndroidManifest.xml:12`（`allowBackup="true"`）

**现状**：只排除了 `sharedpref/finch_settings.xml`（凭据），但 `BackupManager.autoBackup()` 写入的 `filesDir/auto_backups/*.zip` 含**全量游玩历史**，会被 Android 云备份上传到用户 Google 账号，并随换机迁移复制到新设备。

```xml
<full-backup-content>
    <exclude domain="sharedpref" path="finch_settings.xml" />
    <!-- 缺 auto_backups -->
</full-backup-content>
```

**影响**：属于「用户可能不希望的个人数据出境」而非漏洞。App 已自带加密凭据隔离，但游玩历史没有同样待遇，标准不一致。

**修复方案**（两份规则文件同步补）

```xml
<exclude domain="file" path="auto_backups/" />
```

**验证**：`adb shell bmgr backupnow dev.cao.finch` 后检查备份集不含该目录；或直接审阅合并后的 manifest。

---

### S5 — 凭据加密失败时静默降级明文（C3，记录在案）

`data/SettingsStore.kt:180-192`：

```kotlin
val stored = try {
    CredentialCipher.encrypt(value)
} catch (e: Exception) {
    Log.w("SettingsStore", "凭据加密不可用，降级明文存储：$key", e)
    value                    // ← 明文落盘，用户无感
}
```

设计意图明确（保证不崩），但用户无从得知自己的 token 已变成明文。建议：降级时置一个 `credentials_plaintext_fallback = true` 标记，在设置页显示一次黄色提示「本机密钥库不可用，凭据以未加密形式保存」。属产品决策，非缺陷。

### 安全方面确认无问题的项（避免重复排查）

| 检查项 | 结论 |
|---|---|
| 硬编码密钥 | 无。`keystore.properties` 已被 `.gitignore` 排除，`git ls-files` 中无 `.jks` / 真实口令 |
| 组件暴露 | 仅 `MainActivity`（LAUNCHER）与 `FinchWidgetReceiver`（APPWIDGET_UPDATE）`exported=true`，均为系统必需；`TimerService` 与 `FileProvider` 均 `exported=false` |
| 凭据存储 | `CredentialCipher`：Android Keystore 不可导出的 AES/GCM 密钥，随机 IV，密文带 `v1:` 前缀，解密失败返回 null 而非崩溃 |
| TLS | 全局 `HttpClients.shared` 无 `TrustManager` / `HostnameVerifier` 覆盖，走系统默认校验；全仓仅 `WebDavClient` 一处出现 `http://` 字面量（见 S1） |
| 注入 | Room 全是编译期绑定参数（`@Query` 占位符），无字符串拼接 SQL；唯一拼接的是 `VACUUM INTO '<path>'`，已做 `'` 转义（`BackupManager.kt:122`） |
| 文件暴露 | `file_paths.xml` 只开 `cache-path/share/`，注释明确说明不开放 cache 根目录（避免 Coil 缓存外泄） |
| 日志泄露 | 所有 `Log.*` 均不含 token / key / password；PSN 只打耗时统计 |
| 备份内容 | zip 内仅 `finch.db` + `meta.json`，不含凭据；恢复前做 app 标识 + schema 版本 + 核心表 + `user_version` 四重校验 |
| 安装包 / 外部存储 | 无 APK 下载安装逻辑、无 `REQUEST_INSTALL_PACKAGES`、无外部存储读写 |

---

## 三、性能问题

### P1 — `games` 表零索引，按名查询在循环中反复全表扫（最高优先级）

**位置**：`data/FinchDaos.kt:45`（`allByName`）、`data/Game.kt:8`（实体无 `indices`）、schema `23.json`（`games → indices: []`）

**代码证据**

```kotlin
@Query("SELECT * FROM games WHERE name = :name COLLATE NOCASE ORDER BY id")
suspend fun allByName(name: String): List<Game>
```

`games` 表**没有任何索引**（已核对 `app/schemas/dev.cao.finch.data.FinchDatabase/23.json`：`games → indices: []`），因此上述查询每次都走全表扫描。而它在三个热路径里以「每 N 条记录一次」的频率被调用：

| 调用点 | 频率 | 后果 |
|---|---|---|
| `SyncEngine.kt:151` `runSteam` | 每款 Steam 游戏 1 次 | 500 款库 → 500 次全表扫，且**全部在同一个 `withTransaction` 写事务内**，事务持有时间被拉长，UI 侧 `Flow` 的 invalidation 推迟 |
| `SyncEngine.kt:367` `runPSN` | 每个 PSN 标题 1 次 | 同上；`runPSN` 另在 359-362 建了内存索引做二次认领，但主路径仍是逐条查库 |
| `UpcomingViewModel.kt:429` `publish` | 每条日程 1 次 | 一次刷新 300 条 → 300 次全表扫；`addToLibrary` 后还会再触发一次完整 `publish` |
| `GameRepository.kt:49` `findMergeable` | 每次入库 1 次 | 单次可接受 |

复杂度为 O(N×M)：N=日程/同步条目数，M=库内游戏数。1000 款游戏 + 500 款 Steam 库 ≈ 50 万行扫描，在低端机上表现为「同步卡在最后一步」或「日程页刷新转圈很久」。

**为什么不能直接加 Room 索引**

Room 的 `@Index("name")` 生成的是 **BINARY** 排序索引，而查询写的是 `= :name COLLATE NOCASE`，SQLite 无法用 BINARY 索引满足 NOCASE 比较 → 加了索引也不会被使用。而对 `@Entity` 手动声明 NOCASE 索引又与 Room 的 schema 校验冲突（Room 不支持在注解里表达 collation），会导致启动校验失败。

**推荐修复（不动 schema，零迁移风险）**：复用 `runPSN:359-362` 已验证过的「一次性读库 + 内存索引」模式。

```kotlin
/** 名字归一化索引：一次全表读入，替代循环里的 allByName（N 次全表扫 → 1 次） */
internal fun buildNameIndex(games: List<Game>): Map<String, List<Game>> =
    games.groupBy { it.name.lowercase() }

// SyncEngine.runSteam 里，进入 withTransaction 之前：
val nameIndex = buildNameIndex(gameDao.all())
db.withTransaction {
    for (g in games) {
        // 原：val candidates = gameDao.allByName(g.name)
        val candidates = nameIndex[g.name.lowercase()].orEmpty()
        ...
    }
}
```

注意两个口径细节，改动时不能丢：

1. 新插入的游戏必须同步加入索引，否则同一批次内同名条目会重复建档。
2. `allByName` 是 `COLLATE NOCASE`（走 SQLite 的 ASCII 折叠），`lowercase()` 是 Unicode 折叠。对中文标题无差异，对含 `Ä` / `É` 的西欧标题，改成 `lowercase()` 反而**匹配更宽**（更符合预期），但与 `runPSN` 现有 `matchKey()` 的口径需统一——建议抽成一个 `matchKey(name: String)` 供三处共用。

**长期方案（可选）**：DB 24 迁移里手写

```sql
CREATE INDEX IF NOT EXISTS index_games_name_nocase ON games(name COLLATE NOCASE);
```

同时把实体换成 `@Entity(indices = ...)` 不可行，需改为不导出 schema 校验或用 `@ColumnInfo(collate = ColumnInfo.NOCASE)` + Room 的 collate 支持（Room 2.4+ 支持 `@ColumnInfo(collate = NOCASE)`，会连带索引 collation 一致）。这条收益更大但风险更高，建议放在下一轮单独做迁移测试。

**验证**：`runSteam` 前后打点比较事务耗时（现有日志已有 `PsnStore` 耗时统计可参照）；或在单测里用 1000 条 `Game` + 500 条同步条目跑一次计时断言。

---

### P2 — 下拉刷新串行跑三个数据源，总耗时是三段之和

**位置**：`ui/FinchViewModel.kt:249-281`

**代码证据**

```kotlin
if (hasSteam) {
    try { val r = SyncEngine.runSteam(...) ; parts += "Steam +${r.sessionsAdded}条" }
    catch (e: Exception) { parts += "Steam ✗(${e.message?.take(60)})" }
}
if (hasSwitch) {
    try { val r = SyncEngine.runSwitch(...) ; parts += "Switch +${r.sessionsAdded}条" }
    catch (e: Exception) { parts += "Switch ✗(...)" }
}
if (hasPsn) {
    try { val r = SyncEngine.runPSN(...) ; parts += "PSN +${r.sessionsAdded}条" }
    catch (e: Exception) { parts += "PSN ✗(...)" }
}
```

**影响**：三个源彼此独立，串行执行时用户等待 = Steam（1~3s）+ Switch（2~6s，含多设备日报）+ PSN（5~15s，含目录 + 最多 40 次补发售日）。最坏 20s+ 全程转圈，中途任一源超时（`callTimeout` 30s）会把整轮拖到分钟级。用户感知为「下拉刷新很慢/像卡死」。

**修复方案**：三路 `async` 并发，各自 `runCatching` 收集结果，最后合并文案。DB 写入由 Room 的写事务串行化，并发安全（`withTransaction` 会排队），不会产生脏写。

```kotlin
val steamJob = if (hasSteam) async {
    runCatching {
        SyncEngine.runSteam(db, settings.steamApiKey, settings.steamId, settings.steamBaseUrl)
    } to "Steam"
} else null

val switchJob = if (hasSwitch) async {
    runCatching {
        SyncEngine.runSwitch(db, settings.switchSessionToken, settings.switchNaId)
    } to "Switch"
} else null

val psnJob = if (hasPsn) async {
    runCatching {
        val before = PlatinumWatch.snapshot(gameDao)
        val r = SyncEngine.runPSN(db, settings.psnRefreshToken) { t ->
            settings.psnRefreshToken = t.refreshToken.ifBlank { settings.psnRefreshToken }
            settings.psnRefreshExpiresAtMillis = t.refreshExpiresAtMillis
        }
        settings.psnRefreshToken = r.refreshTokenOut
        settings.psnRefreshExpiresAtMillis = r.refreshExpiresAtMillis
        PlatinumWatch.scan(gameDao, settings, before)
        r
    }
} else null

// 汇总：成功的报条数，失败的报 ✗（保留现有文案格式）
```

注意：`settings` 写入（PSN token 轮换）在并发下仍只在 PSN 分支发生，无交叉写；`PlatinumWatch.snapshot/scan` 也在 PSN 分支内闭合，安全。

**验证**：配置三个源后下拉刷新，对比改动前后的端到端耗时（PSN 单源耗时即为并发后的下限）。

---

### P3 — 日程页切源并发拉取，旧结果会覆盖新源

**位置**：`ui/UpcomingViewModel.kt:155-186`（`refresh`）、`:270-274`（`setSource`）、`:438-446`（`refreshDisplay`）

**代码证据**

```kotlin
fun refresh() {
    _state.value = LoadState.Loading
    val sel = _source.value
    viewModelScope.launch {                     // ← 每次新建协程，不持有 Job
        val r = withContext(Dispatchers.IO) { ... fetchPsnEntries() / GamerskyClient / EshopClient ... }
        if (r.isNotEmpty()) mergeAndPublish(r)  // ← 没有校验 sel 是否仍是当前源
        ...
    }
}
```

**影响**

1. **错源数据**：连点「PSN 港服 → 游民星空」两次，两个协程并发；若 PSN 的请求更慢后返回，`mergeAndPublish` 会用它覆盖状态。此时 `refreshDisplay` 按 `_source.value`（已是 GAMERSKY）过滤 PSN 数据 → `bySource` 为空 → 显示「没有符合条件的发售信息」。
2. **流量浪费**：PSN 单次拉取包含目录 2 次 + 最多 40 次 `metGetProductById`，重复拉两遍等于双倍请求，容易触发对端限流。
3. **状态错乱**：`LoadState.Failed` 与 `Ready` 交替写入，UI 可能先报错再突然出列表。

**修复方案**：持有 Job 并在新请求前取消 + 发布前校验源未变。

```kotlin
private var loadJob: Job? = null

fun refresh() {
    loadJob?.cancel()                              // 旧请求作废（协程内 IO 通过 runInterruptible 响应取消）
    val sel = _source.value
    _state.value = LoadState.Loading
    loadJob = viewModelScope.launch {
        val r = withContext(Dispatchers.IO) {
            try {
                when (sel) {
                    Source.PSN -> fetchPsnEntries()
                    Source.GAMERSKY -> fromGamersky(GamerskyClient.fetchRestOfYear())
                    Source.ESHOP -> fromEshop(EshopClient.fetchRecent())
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w("Upcoming", "${sel.label} ✗ ${friendMsg(e)}")
                emptyList()
            }
        }
        if (sel != _source.value) return@launch      // 源已切换，本次结果丢弃
        ...
    }
}
```

`refreshWishlist()` 里的 `fetchPsnEntries()` 也应挂同一个 Job 或独立 Job，避免与主刷新互踩。

**验证**：连续快速切源 5 次，断言最终列表与最后一次点击的源一致；日志中不应出现两次并发 PSN 拉取。

---

### P4 — 计时前台服务每秒空转唤醒

**位置**：`timer/TimerService.kt:304-330`（`startTicker`）

**代码证据**

```kotlin
private fun startTicker() {
    tickerJob?.cancel()
    tickerJob = scope.launch {
        var lastMinute = -1L
        while (isActive && runningSessionId > 0) {
            val minute = elapsedSeconds / 60
            if (minute != lastMinute) {
                lastMinute = minute
                TimerNotifications.update(...)          // ← 只在整分刷新
                FinchWidgetSync.update(this@TimerService)
            }
            delay(1000)                                 // ← 但每秒都醒一次
        }
    }
}
```

**影响**：通知本身用系统 `chronometer` 自动走秒（`setUsesChronometer`），代码只为「整分时刷新一次卡片/桌面小组件」。但循环固定 `delay(1000)`，一次 3 小时会话 = 10,800 次 CPU 唤醒，绝大多数是纯空转（99% 的循环不做事）。在低端机 + 前台服务常驻场景下，属于可感的续航与待机耗电成本。`FinchWidgetSync.update` 每分钟还会触发一次 Glance 组合，是必要的，但没必要每秒检查是否到分钟。

**修复方案**：按剩余时间对齐到下一整分。

```kotlin
while (isActive && runningSessionId > 0) {
    val now = System.currentTimeMillis()
    val pauseMs = if (pauseStartedAtMillis > 0) now - pauseStartedAtMillis else 0L
    val elapsedMs = if (startedAtMillis > 0) {
        (now - startedAtMillis - pauseAccumMs - pauseMs).coerceAtLeast(0L)
    } else 0L
    val minute = elapsedMs / 60_000
    if (minute != lastMinute) {
        lastMinute = minute
        try {
            TimerNotifications.update(...)
            FinchWidgetSync.update(this@TimerService)
        } catch (se: SecurityException) {
            Log.w("TimerService", "notify denied", se)
        }
    }
    // 睡到下一个整分边界（至少 1s，避免除零/空转）
    val toNextMinute = 60_000L - (elapsedMs % 60_000L)
    delay(toNextMinute.coerceIn(1_000L, 60_000L))
}
```

**边界**：暂停/恢复时 `pauseAccumMs` 会变，`ticker` 不重启也没关系——下一轮重新计算 `elapsedMs` 即为最新值；`pauseTimer`/`resumeTimer` 已各自立刻刷过一次通知，不需要 ticker 立刻响应。

**验证**：`adb shell dumpsys batterystats dev.cao.finch` 对比改动前后 30 分钟的 `Wake locks` / `CPU time`；`dumpsys alarm` 观察唤醒次数。

---

### P5 — `PsnStoreClient` 自建 Dispatcher（极低，可保留）

`data/PsnStoreClient.kt:73-77` 为把 `maxRequestsPerHost` 从默认 5 抬到 8，`newBuilder()` 后挂了一个独立 `Dispatcher`。副作用是该 client 常驻一套自己的线程池（默认核心 0、最大 64 的 `SynchronousQueue` 池，实际只会扩到 8 左右）。影响可忽略，且注释已说明动机（40 个补日期请求，5 并发会把总耗时拉到 3 倍）。**不建议改**，仅记录在案。

---

## 四、正确性 / 健壮性

### C1 — 恢复后 `Runtime.exit(0)` 跳过生命周期与 SharedPreferences flush

**位置**：`data/BackupManager.kt:288-295`

```kotlin
private fun restartApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    intent?.let {
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(it)
    }
    Runtime.getRuntime().exit(0)      // ← 硬退出
}
```

**影响**

1. `Runtime.exit(0)` 不触发生命周期回调（`onPause` / `onStop` / `onDestroy` 全部跳过），Compose 当前状态、WorkManager 未提交任务直接丢弃。
2. 更实际的风险：**`SharedPreferences.apply()` 是异步写**，队列可能未落盘就被杀。恢复流程前后会写 `settings`（如 `lastAutoBackupAt`、WebDAV 时间戳），极端时序下丢一次写入。
3. 进程退出码 0 在部分 ROM 上会被判为「异常退出」并弹「应用已停止」，用户误以为是崩溃。

**修复方案**：

```kotlin
private fun restartApp(context: Context) {
    // 先把关键 SP 同步落盘（apply 是异步的，exit 会丢）
    (context.applicationContext as? FinchApp)?.settings?.flush()
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    intent?.let { context.startActivity(it) }
    android.os.Process.killProcess(android.os.Process.myPid())   // 走正常死亡路径
}
```

`SettingsStore` 补一个 `fun flush()` 遍历未落盘的 key 用 `commit()`（或维护一个脏标记集合）。若不想引入脏标记，最简做法是在 restore 成功路径上不做任何 SP 写入，把这个隐患直接消除。

### C2 — `TimerService.onDestroy` 不清 `TimerServiceBridge`

**位置**：`timer/TimerService.kt:332-335`、`timer/TimerService.kt:339-357`

```kotlin
override fun onDestroy() {
    scope.cancel()
    super.onDestroy()          // ← Bridge 里的 runningGameId / startedAtMillis 留在上个值
}
```

正常停止路径（`stopTimer` → `teardownForeground`）已经重置了 Bridge，所以主路径无问题。风险在异常路径：服务被系统单独回收而进程仍存活时，`runningGameId` 仍指向旧游戏、`startedAtMillis` 仍 > 0，主页「正在玩」卡片与详情页秒表会继续自增，直到用户手动再操作一次。

**修复**：`onDestroy` 里按「内存态是否已结算」做条件重置，或统一无条件重置（进程若整体被杀则 Bridge 一起死，无副作用）。

### C3 — 见 S5（Keystore 降级明文）

### C4 — 备份校验的时序（信息性）

`BackupManager.validate()` 会**先解压**再校验文件头与核心表（`unzipDb` → 检查 `SQLite format 3` → 打开只读库存取 `sqlite_master`）。这个顺序本身合理（不然无法验证内容），但与 S3 叠加就是「先写盘、后判断」。修完 S3 的尺寸上限后该时序可接受，无需再改。

---

## 五、已做对、不要动的部分

避免后续重构把这些正确决策改坏，此处显式记录：

1. **凭据加密**：`CredentialCipher` 用 Android Keystore 生成不可导出 AES/GCM 密钥，随机 IV，密文带 `v1:` 前缀，解密失败返回 null 而非崩溃，旧明文惰性迁移。这是同类 App 里少见的正确做法。
2. **迁移链**：22 条手写 `Migration`，`ALL_MIGRATIONS` 单点定义供生产与迁移测试共用，注释里记录了每条迁移的历史原因（尤其是 15→16、18→19、21→22 三次表重建的 DDL 对齐）。
3. **备份校验**：meta `app` 标识 + `schemaVersion` + 核心表存在性 + `user_version` 交叉核对，四重校验能挡住「任意 SQLite 文件顶掉 finch.db」。
4. **一致快照**：导出走 `VACUUM INTO`，旧 SQLite 回退 `wal_checkpoint(FULL)` + 拷贝，路径已做 `'` 转义。
5. **列表重组优化**：`HistoryScreen.kt:126-130` 只让「进行中的行」订阅秒级状态，已完成的行传 `null`。注释里明确写了「之前每行都传 now，每秒整个列表一起重组」。这个模式在 `WishlistPane`、`HomeScreen` 里也保持一致。
6. **玻璃层**：`Glass.kt` 限定最多一层嵌套采样（`MAX_GLASS_DEPTH = 1`）、采样源静态化并锁帧，避免滚动时重录离屏层。
7. **聚合下推 SQL**：`observeDailyTotals` / `observeMonthlyTotals` / `observePlatformTotals` / `observeTopGamesWithCover` 全部在 SQL 内完成 GROUP BY，没有「捞出全表在 Kotlin 里求和」。唯一例外是 `observeSessionSpans`（高峰时段按小时摊分），但注释解释了「SQL 逐小时展开要递归 CTE，不值得」，且已限定时间窗，可接受。
8. **网络层收敛**：所有客户端共用一个 `HttpClients.shared`（connect 5s / read 10s / write 10s / **call 30s**），`callTimeout` 是防「慢滴响应挂住线程」的关键项，已设置。
9. **同步幂等**：`diffWriteSessions` 的基线推进逻辑（`writtenMin >= deltaMin` 才对齐官方累计，否则只推进已落账部分）修掉了旧版「写失败即永久丢时长」的缺陷，且有注释与版本号溯源。

---

## 六、修复计划建议

分三批，每批可独立验证、独立回滚。

### 批次 A —— 安全（零 schema 变更，风险最低）

| 项 | 改动文件 | 预估 |
|---|---|---|
| S1 https 强制 + 私网白名单 | `WebDavClient.kt` | 30 min |
| S2 Steam 基址 scheme 校验 | `SettingsStore.kt`、`ImportViewModel.kt` | 20 min |
| S4 备份规则补 `auto_backups/` | `backup_rules.xml`、`data_extraction_rules.xml` | 5 min |

验证：`./gradlew.bat :app:testDebugUnitTest`（补 S1/S2 的纯函数用例）+ `:app:assembleDebug`。

### 批次 B —— 性能（不动 DB schema）

| 项 | 改动文件 | 预估 |
|---|---|---|
| P1 内存名字索引 | `SyncEngine.kt`（runSteam/runPSN）、`UpcomingViewModel.kt`（publish）、`GameRepository.kt` | 1.5 h |
| P3 Job 取消 + 源校验 | `UpcomingViewModel.kt` | 30 min |
| P2 三源并发 | `FinchViewModel.kt` | 40 min |
| P4 ticker 对齐整分 | `TimerService.kt` | 20 min |

验证：`./gradlew.bat :app:testDebugUnitTest`（现有迁移测试 + 纯函数测试）→ `:app:assembleDebug` → 真机冒烟（同步 / 日程刷三次 / 计时 5 分钟观察通知与小组件）。

### 批次 C —— 健壮性（可延后）

| 项 | 改动文件 | 备注 |
|---|---|---|
| S3 尺寸上限 + 流式下载 | `BackupManager.kt`、`WebDavClient.kt`、`ImportViewModel.kt` | 动到恢复链路，需真机验证一次完整「上传 → 恢复」 |
| C1 `exit(0)` → `killProcess` | `BackupManager.kt`、`SettingsStore.kt` | 需确认各 ROM 上重启行为 |
| C2 Bridge 清理 | `TimerService.kt` | 5 min |
| P1 长线：DB 24 加 NOCASE 索引 | `FinchDatabase.kt` + 实体 collation | 需补迁移测试，建议单独一轮 |

---

## 附录：审查方法与可复现命令

```bash
# 规模与热点文件
find app/src/main/java -name "*.kt" | xargs wc -l | sort -rn | head -40

# 敏感文件是否入库（结果：仅 keystore.properties.example，真实密钥已被 .gitignore 排除）
git ls-files | grep -iE "keystore|\.jks|\.env|secret|token"

# 明文 http / TLS 覆盖 / WebView / WebView 桥
grep -rn '"http://' --include=*.kt app/src/main/java
grep -rn "TrustManager\|HostnameVerifier\|sslSocketFactory" --include=*.kt app/src/main/java

# 索引实况（games 表确认为空）
python -c "import json; d=json.load(open('app/schemas/dev.cao.finch.data.FinchDatabase/23.json',encoding='utf-8')); \
print([(e['tableName'], e.get('indices')) for e in d['database']['entities']])"

# 日志中是否混入凭据
grep -rn "Log\.[dviw](" --include=*.kt app/src/main/java | grep -iE "token|key|password|secret"

# 列表 key / 秒级重组热点
grep -rn "items(" --include=*.kt app/src/main/java | grep -v "key ="
grep -rn "while (true)\|delay(1000)" --include=*.kt app/src/main/java
```

> 注：本报告所有行号基于 `8d6cfd4` 之后的当前工作区状态（v0.29.3 / DB 23）。修改代码后行号会漂移，请以「函数名 + 文件」定位为准。
