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
 * - 导出前先 `PRAGMA wal_checkpoint(FULL)`，保证主库文件完整（WAL 内容回写）。
 * - zip 里只有 finch.db + meta.json，**不含** Steam Key / Switch token 等密钥
 *   （它们在 finch_settings.xml，且已被备份规则排除）。
 * - 恢复会覆盖当前全部游玩记录，成功后由调用方重启进程让 Room 重新挂库。
 */
object BackupManager {

    private const val DB_NAME = "finch.db"
    private const val DB_ENTRY = "finch.db"
    private const val META_ENTRY = "meta.json"

    /** 导出到 SAF Uri（用户通过系统文件选择器选位置），返回写入的字节数 */
    fun export(context: Context, db: FinchDatabase, target: Uri): Long {
        val dbFile = context.getDatabasePath(DB_NAME)
        if (!dbFile.exists()) throw IOException("数据库文件不存在")
        // WAL checkpoint：把 -wal 里的数据回写进主文件，导出的才是完整快照
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        var written = 0L
        context.contentResolver.openOutputStream(target)?.use { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry(DB_ENTRY))
                dbFile.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                written += dbFile.length()
                val meta = JSONObject()
                    .put("app", "finch")
                    .put("schemaVersion", db.openHelper.writableDatabase.version)
                    .put("exportedAt", LocalDateTime.now().toString())
                zip.putNextEntry(ZipEntry(META_ENTRY))
                zip.write(meta.toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        } ?: throw IOException("无法写入所选位置")
        return written
    }

    /**
     * 从 SAF Uri 恢复：校验 → 关库 → 覆盖 finch.db（清除 -wal/-shm）→ 重启进程。
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
            db.close()
            val dbFile = context.getDatabasePath(DB_NAME)
            val extracted = unzipDb(tmp, context.cacheDir)
            dbFile.delete()
            if (!extracted.renameTo(dbFile)) {
                // rename 跨目录可能失败，退化成复制
                extracted.copyTo(dbFile, overwrite = true)
                extracted.delete()
            }
            // 清掉旧 WAL/SHM，避免旧事务日志污染新库
            File(dbFile.parentFile, "$DB_NAME-wal").delete()
            File(dbFile.parentFile, "$DB_NAME-shm").delete()
            restartApp(context)
        } finally {
            tmp.delete()
        }
    }

    /** 校验 zip：必须含 finch.db 且是合法 SQLite 库，返回备份的 schema 版本 */
    private fun validate(zipFile: File): Int {
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
            val version = db.version
            db.close()
            return version
        } finally {
            extracted.delete()
        }
    }

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
        intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}
