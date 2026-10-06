package dev.cao.finch.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 数据备份/恢复：把 Room 数据库（finch.db）打包成 zip 导出，导入时校验后整库替换。
 *
 * - 导出优先 `VACUUM INTO` 生成一致性快照（WAL 内容一并固化）；旧 SQLite 设备
 *   （低于 3.27 无 VACUUM INTO）回退 `wal_checkpoint(FULL)` + 拷贝主文件。
 * - zip 里只有 finch.db + meta.json，**不含** Steam Key / Switch token 等密钥
 *   （它们在 finch_settings.xml，且已被备份规则排除）。
 * - 恢复会覆盖当前全部游玩记录，成功后由调用方重启进程让 Room 重新挂库。
 */
object BackupManager {

    private const val DB_NAME = "finch.db"
    private const val DB_ENTRY = "finch.db"
    private const val META_ENTRY = "meta.json"

    /** 备份必备核心表全集（release_follows 自 DB 12 起才有） */
    private val CORE_TABLES = listOf("games", "play_sessions", "playtime_snapshots", "release_follows")

    /** 备份应含核心表中缺失的部分（纯函数，单测直接调用）：
     *  [schemaVersion] ≥ 12 还须有 release_follows（DB 12 才建表） */
    internal fun missingCoreTables(presentTables: Set<String>, schemaVersion: Int): List<String> {
        val required = mutableListOf("games", "play_sessions", "playtime_snapshots")
        if (schemaVersion >= 12) required += "release_follows"
        return required.filterNot { it in presentTables }
    }

    /** 备份 meta.json 头部校验（纯函数，单测直接调用）：返回错误文案，null = 通过 */
    internal fun validateBackupMeta(app: String?, metaVersion: Int, maxVersion: Int): String? = when {
        app != "finch" -> "备份文件不完整或不是 Finch 备份"
        metaVersion > maxVersion -> "备份来自更新版本的应用（schema v$metaVersion > v$maxVersion），请先升级 App"
        else -> null
    }

    /** 导出到 SAF Uri（用户通过系统文件选择器选位置），返回写入的字节数 */
    fun export(context: Context, db: FinchDatabase, target: Uri): Long {
        val dbFile = context.getDatabasePath(DB_NAME)
        if (!dbFile.exists()) throw IOException("数据库文件不存在")
        // 一致性快照：优先 VACUUM INTO（SQLite 3.27+），checkpoint 与拷贝之间不会撕裂；
        // 旧设备系统 SQLite 可能低于 3.27（minSdk 26），捕获异常回退 WAL checkpoint + 拷贝
        val snapshot = File(context.cacheDir, "export-$DB_NAME")
        snapshot.delete()
        val useSnapshot = try {
            db.openHelper.writableDatabase.execSQL(
                "VACUUM INTO '${snapshot.absolutePath.replace("'", "''")}'"
            )
            true
        } catch (e: Exception) {
            snapshot.delete()
            // 回退：把 -wal 里的数据回写进主文件，再直接拷主文件
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
            false
        }
        try {
            var written = 0L
            val meta = JSONObject()
                .put("app", "finch")
                .put("schemaVersion", db.openHelper.writableDatabase.version)
                .put("exportedAt", LocalDateTime.now().toString())
            val metaBytes = meta.toString().toByteArray(Charsets.UTF_8)
            context.contentResolver.openOutputStream(target)?.use { out ->
                ZipOutputStream(out).use { zip ->
                    zip.putNextEntry(ZipEntry(DB_ENTRY))
                    val src = if (useSnapshot) snapshot else dbFile
                    src.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    written += src.length()
                    zip.putNextEntry(ZipEntry(META_ENTRY))
                    zip.write(metaBytes)
                    zip.closeEntry()
                    written += metaBytes.size
                }
            } ?: throw IOException("无法写入所选位置")
            return written
        } finally {
            snapshot.delete()
        }
    }

    /**
     * 从 SAF Uri 恢复：校验 → 解包 → 关库 → 覆盖 finch.db（清除 -wal/-shm）→ 重启进程。
     * 成功路径不返回（进程重启）；失败抛异常由调用方提示。
     */
    fun restore(context: Context, db: FinchDatabase, source: Uri) {
        // SAF 流只能顺序读一次，先落成临时文件再校验、再覆盖
        val tmp = File(context.cacheDir, "restore-$DB_NAME")
        context.contentResolver.openInputStream(source)?.use { input ->
            tmp.outputStream().use { input.copyTo(it) }
        } ?: throw IOException("无法读取所选文件")
        try {
            val backupVersion = validate(tmp)
            if (backupVersion > FINCH_DB_VERSION) {
                throw IOException("备份来自更新版本的应用（schema v$backupVersion > v$FINCH_DB_VERSION），请先升级 App")
            }
            // 先解包（此时失败 Room 仍可用），全部校验通过后才关库替换
            val extracted = unzipDb(tmp, context.cacheDir)
            val dbFile = context.getDatabasePath(DB_NAME)
            db.close()
            try {
                dbFile.delete()
                if (!extracted.renameTo(dbFile)) {
                    // rename 跨目录可能失败，退化成复制
                    extracted.copyTo(dbFile, overwrite = true)
                    extracted.delete()
                }
                // 清掉旧 WAL/SHM，避免旧事务日志污染新库
                File(dbFile.parentFile, "$DB_NAME-wal").delete()
                File(dbFile.parentFile, "$DB_NAME-shm").delete()
            } catch (e: Exception) {
                // 替换阶段失败时 Room 已关死、进程内无法自愈：重启兜底，绝不留半死状态
                restartApp(context)
                throw IOException("恢复失败，请重新导入备份", e)
            }
            restartApp(context)
        } finally {
            tmp.delete()
        }
    }

    /** 校验 zip：meta.json 标识/版本 + finch.db 是含核心表的合法 SQLite 库，返回备份的 schema 版本 */
    private fun validate(zipFile: File): Int {
        val meta = readMeta(zipFile)
        val metaVersion = meta.optInt("schemaVersion", -1)
        validateBackupMeta(meta.optString("app"), metaVersion, FINCH_DB_VERSION)?.let { throw IOException(it) }
        val extracted = unzipDb(zipFile, zipFile.parentFile ?: File("."))
        try {
            val header = ByteArray(16)
            extracted.inputStream().use { it.read(header) }
            if (!String(header, Charsets.US_ASCII).startsWith("SQLite format 3")) {
                throw IOException("备份文件不完整或不是 Finch 备份")
            }
            val db = SQLiteDatabase.openDatabase(
                extracted.absolutePath, null, SQLiteDatabase.OPEN_READONLY
            )
            try {
                // 光有 SQLite 文件头不够：核心表必须齐全，防止任意 SQLite 文件顶掉 finch.db
                val present = CORE_TABLES.filter { db.hasTable(it) }.toSet()
                val missing = missingCoreTables(present, db.version)
                if (missing.isNotEmpty()) {
                    throw IOException("备份缺少核心表（${missing.joinToString()}），不是 Finch 备份")
                }
                val version = db.version
                // meta 与库内 user_version 交叉核对，防止拼接/篡改的备份
                if (metaVersion != version) {
                    throw IOException("备份元信息与数据库版本不一致（meta v$metaVersion / db v$version）")
                }
                return version
            } finally {
                db.close()
            }
        } finally {
            extracted.delete()
        }
    }

    /** 从 zip 里读出 meta.json（导出端写入：app / schemaVersion / exportedAt），缺失或损坏则抛异常 */
    private fun readMeta(zipFile: File): JSONObject {
        try {
            ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name == META_ENTRY) {
                        val text = zip.readBytes().toString(Charsets.UTF_8)
                        zip.closeEntry()
                        return JSONObject(text)
                    }
                    zip.closeEntry()
                }
            }
        } catch (e: Exception) {
            throw IOException("备份里的 $META_ENTRY 读取失败", e)
        }
        throw IOException("备份里没有 $META_ENTRY，不是 Finch 备份")
    }

    /** 该库是否存在指定表 */
    private fun SQLiteDatabase.hasTable(name: String): Boolean =
        rawQuery("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(name))
            .use { it.moveToFirst() }

    /** 从 zip 里解出 finch.db 到 dir，找不到该条目或 zip 损坏则抛异常 */
    private fun unzipDb(zipFile: File, dir: File): File {
        val out = File(dir, "restored-$DB_NAME")
        out.delete()
        var found = false
        try {
            ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name == DB_ENTRY) {
                        out.outputStream().use { zip.copyTo(it) }
                        found = true
                    }
                    zip.closeEntry()
                    if (found) break
                }
            }
        } catch (e: Exception) {
            throw IOException("备份文件读取失败（不是有效的 zip）", e)
        }
        if (!found) throw IOException("备份里没有 $DB_ENTRY，不是 Finch 备份")
        return out
    }

    private fun restartApp(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.let {
            it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(it)
        }
        Runtime.getRuntime().exit(0)
    }
}
