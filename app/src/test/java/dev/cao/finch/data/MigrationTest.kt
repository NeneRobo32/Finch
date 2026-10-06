package dev.cao.finch.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Room 手写迁移链的端到端测试（Robolectric，JVM 可跑，无需设备）。
 *
 * 方法：用 app/schemas 的历史 schema JSON 建出「起始版本 v」的库并插样例行 →
 * Room 挂全量 MIGRATION_* 打开（Room 自带 schema 校验，迁移 SQL 与实体不一致会抛
 * IllegalStateException —— v0.15.0 的启动闪退就是这么炸的）→ 断言样例数据保留、列值正确。
 *
 * 覆盖起始版本 7..15 逐版升到 FINCH_DB_VERSION=16。
 * 1..6 无 schema JSON（schemas 从 7 起入库），无法起测，已在报告注明。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    private val context get() = RuntimeEnvironment.getApplication()
    private val dbName = "finch.db"

    @Before
    fun setUp() {
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    // ---- 起始版本 7..15 各一测（每个都是完整升级链到 v16）----

    @Test
    fun `v7升级到当前_数据保留`() = checkFrom(7)

    @Test
    fun `v8升级到当前_数据保留`() = checkFrom(8)

    @Test
    fun `v9升级到当前_数据保留`() = checkFrom(9)

    @Test
    fun `v10升级到当前_数据保留`() = checkFrom(10)

    @Test
    fun `v11升级到当前_数据保留`() = checkFrom(11)

    @Test
    fun `v12升级到当前_数据保留`() = checkFrom(12)

    @Test
    fun `v13升级到当前_数据保留`() = checkFrom(13)

    @Test
    fun `v14升级到当前_数据保留`() = checkFrom(14)

    @Test
    fun `v15升级到当前_数据保留`() = checkFrom(15)

    // ---- 专项：15→16 重建 play_sessions 的 DDL 与索引 ----

    @Test
    fun `v15到16重建后_pauseAccumMs带DEFAULT0_两条索引齐全`() {
        createOldDb(15)
        val room = openWithMigrations()
        try {
            val db = room.openHelper.writableDatabase
            // pauseAccumMs 的 DDL 必须带 DEFAULT 0（与实体 defaultValue="0" 一致，升级装/新装统一）
            var dflt: String? = null
            db.query("PRAGMA table_info(`play_sessions`)").use { c ->
                val nameIdx = c.getColumnIndex("name")
                val dfltIdx = c.getColumnIndex("dflt_value")
                while (c.moveToNext()) {
                    if (c.getString(nameIdx) == "pauseAccumMs") {
                        dflt = if (c.isNull(dfltIdx)) null else c.getString(dfltIdx)
                    }
                }
            }
            assertEquals("pauseAccumMs 的 DEFAULT 应为 0", "0", dflt)
            // 重建后两条索引必须回来
            val idx = mutableSetOf<String>()
            db.query(
                "SELECT name FROM sqlite_master WHERE type = 'index' AND name IN " +
                    "('index_play_sessions_gameId','index_play_sessions_startTime')"
            ).use { c ->
                while (c.moveToNext()) idx += c.getString(0)
            }
            assertEquals(setOf("index_play_sessions_gameId", "index_play_sessions_startTime"), idx)
            // 重建不丢数据、不丢暂停累计
            runBlocking {
                val s = room.sessionDao().byId(1L)
                assertNotNull(s)
                assertEquals(42L, s!!.pauseAccumMs)
            }
        } finally {
            room.close()
        }
    }

    @Test
    fun `新装库直接建v16_可正常读写`() {
        // 无旧文件：Room 直接按实体建 v16（与迁移结果同构是 Room 校验口径统一的前提）
        val room = openWithMigrations()
        try {
            runBlocking {
                assertEquals(0, room.gameDao().count())
                assertTrue(room.sessionDao().totalMsForGame(1L) == 0L)
            }
        } finally {
            room.close()
        }
    }

    // ---- 断言主体：升级后样例行保留、列值符合该起始版本的演进语义 ----

    private fun checkFrom(version: Int) {
        createOldDb(version)
        val room = openWithMigrations()
        try {
            runBlocking {
                val games = room.gameDao()
                val sessions = room.sessionDao()
                val snaps = room.snapshotDao()

                assertEquals(2, games.count())
                val g1 = games.byId(1L)!!
                assertEquals("Sample Game", g1.name)
                assertEquals(Platform.PC, g1.platform)
                assertEquals(500L, g1.steamPlaytimeMin)

                val g2 = games.byId(2L)!!
                assertEquals("通关游戏", g2.name)
                assertEquals(Platform.SWITCH, g2.platform)
                assertEquals("0100ABCDEF000000", g2.switchAppId)
                if (version >= 8) {
                    // 7→8 才加这些列：v8+ 起点的样例值必须原样活过 10→11 / 13→14 / 15→16 三次重建
                    assertEquals(5, g2.rating)
                    assertEquals("好游戏", g2.thoughts)
                    assertEquals(true, g2.favorite)
                    assertEquals(true, g2.completed)
                    assertEquals(
                        1700001000000L,
                        g2.completedAt?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
                    )
                } else {
                    assertEquals(null, g2.rating)
                    assertEquals(false, g2.favorite)
                }
                // hltbMainMin：11→12 加列；13→14 重建保留
                assertEquals(if (version >= 12) 360L else null, g2.hltbMainMin)
                // hltbExtra/hltb100：v13 起点插入过，但 13→14 重建按当年产品决策删除、14→15 加回为空；
                // 只有 v15+ 起点的值能活到现在
                assertEquals(if (version >= 15) 500L else null, g2.hltbExtraMin)
                assertEquals(if (version >= 15) 720L else null, g2.hltb100Min)

                // 会话：pauseAccumMs 在 v9 起点是 42，v7/8 起点经 8→9 ALTER 落默认 0
                val s = sessions.byId(1L)!!
                assertEquals(1L, s.gameId)
                assertEquals(if (version >= 9) 42L else 0L, s.pauseAccumMs)
                // 时长口径：endTime - startTime - pauseAccumMs（样例会话 1 小时）
                assertEquals(3_600_000L - s.pauseAccumMs, sessions.totalMsForGame(1L))

                // 快照保留
                val snap = snaps.byKey("steam", "steam:1")
                assertNotNull(snap)
                assertEquals(123L, snap!!.totalMin)

                // release_follows：DB 12 起才有（样例行只在 v12+ 起点插入；更早起点升级后表为空）
                val followName = db(room).query("SELECT name FROM release_follows WHERE `key` = 'k1'").use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
                if (version >= 12) assertEquals("Sample Follow", followName) else assertEquals(null, followName)
            }
        } finally {
            room.close()
        }
    }

    private fun db(room: FinchDatabase): androidx.sqlite.db.SupportSQLiteDatabase = room.openHelper.writableDatabase

    // ---- 旧版本库构建：按 schema JSON 的 createSql/indexSql 建表 + 插样例行 ----

    private fun schemaFile(version: Int): File =
        listOf("schemas", "app/schemas")
            .map { File("$it/dev.cao.finch.data.FinchDatabase/$version.json") }
            .firstOrNull { it.exists() }
            ?: error("找不到 schema JSON（v$version）——请先跑一次构建生成 app/schemas")

    private fun createOldDb(version: Int) {
        val database = JSONObject(schemaFile(version).readText(Charsets.UTF_8)).getJSONObject("database")
        val entities = database.getJSONArray("entities")
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            db.execSQL("PRAGMA foreign_keys=OFF")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                val table = e.getString("tableName")
                db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = e.optJSONArray("indices")
                if (indices != null) {
                    for (j in 0 until indices.length()) {
                        db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                    }
                }
            }
            seed(db)
            db.version = version
        } finally {
            db.close()
        }
    }

    private fun tableExists(db: SQLiteDatabase, table: String): Boolean =
        db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table))
            .use { it.moveToFirst() }

    /** 插样例行（含边界值）：values 里多余的列名自动忽略（旧版本无该列），NOT NULL 无默认列按类型补占位 */
    private fun seed(db: SQLiteDatabase) {
        if (tableExists(db, "games")) {
            insertRow(
                db, "games", mapOf(
                    "id" to 1L, "name" to "Sample Game", "platform" to "PC",
                    "createdAt" to 1700000000000L, "steamAppId" to 400L,
                    "steamPlaytimeMin" to 500L, "steamSyncedAt" to 1700000000000L,
                )
            )
            insertRow(
                db, "games", mapOf(
                    "id" to 2L, "name" to "通关游戏", "platform" to "SWITCH",
                    "createdAt" to 1700000600000L, "switchAppId" to "0100ABCDEF000000",
                    "favorite" to 1, "completed" to 1, "completedAt" to 1700001000000L,
                    "rating" to 5, "thoughts" to "好游戏",
                    "hltbMainMin" to 360L, "hltbExtraMin" to 500L, "hltb100Min" to 720L,
                    "igdbId" to 999L,
                )
            )
        }
        if (tableExists(db, "play_sessions")) {
            insertRow(
                db, "play_sessions", mapOf(
                    "id" to 1L, "gameId" to 1L,
                    "startTime" to 1700000000000L, "endTime" to 1700003600000L,
                    "source" to "TIMER", "pauseAccumMs" to 42L,
                )
            )
        }
        if (tableExists(db, "playtime_snapshots")) {
            insertRow(
                db, "playtime_snapshots", mapOf(
                    "id" to 1L, "source" to "steam", "refKey" to "steam:1",
                    "refName" to "Sample Game", "totalMin" to 123L, "at" to 1700000000000L,
                )
            )
        }
        if (tableExists(db, "release_follows")) {
            insertRow(
                db, "release_follows", mapOf(
                    "id" to 1L, "key" to "k1", "name" to "Sample Follow",
                    "source" to "bangumi", "notifyDays" to 3, "addedAt" to 1700000000000L,
                    "dateIso" to "2027-01-01",
                )
            )
        }
    }

    private fun insertRow(db: SQLiteDatabase, table: String, values: Map<String, Any?>) {
        val cv = ContentValues()
        db.rawQuery("PRAGMA table_info(`$table`)", null).use { c ->
            val nameIdx = c.getColumnIndex("name")
            val typeIdx = c.getColumnIndex("type")
            val notNullIdx = c.getColumnIndex("notnull")
            val dfltIdx = c.getColumnIndex("dflt_value")
            while (c.moveToNext()) {
                val name = c.getString(nameIdx)
                val notNull = c.getInt(notNullIdx) == 1
                val hasDefault = !c.isNull(dfltIdx)
                when {
                    values.containsKey(name) -> put(cv, name, values[name])
                    // NOT NULL 且无默认且调用方没给值：按亲和性补占位（id 除外，交给自增）
                    notNull && !hasDefault && !name.equals("id", ignoreCase = true) -> {
                        val type = c.getString(typeIdx).uppercase()
                        put(
                            cv, name, when {
                                type.contains("INT") -> 0L
                                type.contains("REAL") || type.contains("FLOA") || type.contains("DOUB") -> 0.0
                                type.contains("BLOB") -> ByteArray(0)
                                else -> "x"
                            }
                        )
                    }
                    // 其余列留 NULL / 走默认
                }
            }
        }
        db.insertOrThrow(table, null, cv)
    }

    private fun put(cv: ContentValues, key: String, v: Any?) {
        when (v) {
            null -> cv.putNull(key)
            is Long -> cv.put(key, v)
            is Int -> cv.put(key, v)
            is Double -> cv.put(key, v)
            is String -> cv.put(key, v)
            is ByteArray -> cv.put(key, v)
            else -> error("不支持的绑定类型：${v::class}")
        }
    }

    /** 挂全量手写迁移打开 Room：迁移 SQL 与实体不一致时 Room 校验直接抛异常（测试要的断言点） */
    private fun openWithMigrations(): FinchDatabase =
        Room.databaseBuilder(context, FinchDatabase::class.java, dbName)
            .addMigrations(
                FinchDatabase.MIGRATION_1_2, FinchDatabase.MIGRATION_2_3,
                FinchDatabase.MIGRATION_3_4, FinchDatabase.MIGRATION_4_5,
                FinchDatabase.MIGRATION_5_6, FinchDatabase.MIGRATION_6_7,
                FinchDatabase.MIGRATION_7_8, FinchDatabase.MIGRATION_8_9,
                FinchDatabase.MIGRATION_9_10, FinchDatabase.MIGRATION_10_11,
                FinchDatabase.MIGRATION_11_12, FinchDatabase.MIGRATION_12_13,
                FinchDatabase.MIGRATION_13_14, FinchDatabase.MIGRATION_14_15,
                FinchDatabase.MIGRATION_15_16,
            )
            .allowMainThreadQueries()
            .build()
}
