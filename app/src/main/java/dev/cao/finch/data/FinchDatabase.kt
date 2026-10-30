package dev.cao.finch.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.LocalDateTime

class Converters {
    @TypeConverter
    fun fromLocalDateTime(value: LocalDateTime?): Long? = value?.atZone(java.time.ZoneId.systemDefault())?.toInstant()?.toEpochMilli()

    @TypeConverter
    fun toLocalDateTime(value: Long?): LocalDateTime? =
        value?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime() }
}

/** Room schema 版本（迁移与备份校验共用） */
const val FINCH_DB_VERSION = 19

@Database(
    entities = [Game::class, PlaySession::class, PlaytimeSnapshot::class, ReleaseFollow::class],
    version = FINCH_DB_VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class FinchDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
    abstract fun sessionDao(): SessionDao
    abstract fun snapshotDao(): SnapshotDao
    abstract fun releaseFollowDao(): ReleaseFollowDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN steamAppId INTEGER")
                db.execSQL("ALTER TABLE games ADD COLUMN steamPlaytimeMin INTEGER")
                db.execSQL("ALTER TABLE games ADD COLUMN steamSyncedAt INTEGER")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN platformsCsv TEXT")
                db.execSQL("ALTER TABLE games ADD COLUMN coverUrl TEXT")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN bangumiId INTEGER")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN switchAppId TEXT")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `playtime_snapshots` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`source` TEXT NOT NULL, " +
                        "`refKey` TEXT NOT NULL, " +
                        "`refName` TEXT NOT NULL, " +
                        "`totalMin` INTEGER NOT NULL, " +
                        "`at` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_playtime_snapshots_source_refKey` " +
                        "ON `playtime_snapshots` (`source`, `refKey`)"
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN psnTitleId TEXT")
                db.execSQL("ALTER TABLE games ADD COLUMN psnPlaytimeMin INTEGER")
                db.execSQL("ALTER TABLE games ADD COLUMN psnSyncedAt INTEGER")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE games ADD COLUMN completed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE games ADD COLUMN completedAt INTEGER")
                db.execSQL("ALTER TABLE games ADD COLUMN rating INTEGER")
                db.execSQL("ALTER TABLE games ADD COLUMN thoughts TEXT")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 游戏状态（默认“在玩”=PLAYING；已通关的回填 COMPLETED）
                db.execSQL("ALTER TABLE games ADD COLUMN status TEXT")
                db.execSQL("ALTER TABLE games ADD COLUMN statusUpdatedAt INTEGER")
                db.execSQL("UPDATE games SET status = 'COMPLETED' WHERE completed = 1")
                db.execSQL("UPDATE games SET status = 'PLAYING' WHERE status IS NULL")
                // 计时暂停累计
                db.execSQL("ALTER TABLE play_sessions ADD COLUMN pauseAccumMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE play_sessions ADD COLUMN pauseStartedAt INTEGER")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 回本率价格（v0.14.0 引入，v0.14.1 移除；保留空迁移保证已升级用户能继续走）
                db.execSQL("ALTER TABLE games ADD COLUMN priceCny REAL")
                db.execSQL("ALTER TABLE games ADD COLUMN priceFetchedAt INTEGER")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 移除回本率价格列：SQLite 不支持 DROP COLUMN（旧版本），重建 games 表
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `games_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `platform` TEXT NOT NULL, " +
                        "`platformsCsv` TEXT, `coverUrl` TEXT, `bangumiId` INTEGER, " +
                        "`createdAt` INTEGER NOT NULL, `steamAppId` INTEGER, " +
                        "`steamPlaytimeMin` INTEGER, `steamSyncedAt` INTEGER, " +
                        "`switchAppId` TEXT, `psnTitleId` TEXT, `psnPlaytimeMin` INTEGER, " +
                        "`psnSyncedAt` INTEGER, `favorite` INTEGER NOT NULL DEFAULT 0, " +
                        "`completed` INTEGER NOT NULL DEFAULT 0, `completedAt` INTEGER, " +
                        "`rating` INTEGER, `thoughts` TEXT, `status` TEXT, `statusUpdatedAt` INTEGER)"
                )
                db.execSQL(
                    "INSERT INTO games_new (id, name, platform, platformsCsv, coverUrl, bangumiId, " +
                        "createdAt, steamAppId, steamPlaytimeMin, steamSyncedAt, switchAppId, " +
                        "psnTitleId, psnPlaytimeMin, psnSyncedAt, favorite, completed, completedAt, " +
                        "rating, thoughts, status, statusUpdatedAt) " +
                        "SELECT id, name, platform, platformsCsv, coverUrl, bangumiId, " +
                        "createdAt, steamAppId, steamPlaytimeMin, steamSyncedAt, switchAppId, " +
                        "psnTitleId, psnPlaytimeMin, psnSyncedAt, favorite, completed, completedAt, " +
                        "rating, thoughts, status, statusUpdatedAt FROM games"
                )
                db.execSQL("DROP TABLE games")
                db.execSQL("ALTER TABLE games_new RENAME TO games")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 发售关注表 + 通关参考时长（null=未知，不回填）
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `release_follows` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`key` TEXT NOT NULL, `name` TEXT NOT NULL, `coverUrl` TEXT, " +
                        "`dateIso` TEXT, `source` TEXT NOT NULL, `notifyDays` INTEGER NOT NULL, " +
                        "`addedAt` INTEGER NOT NULL, `notifiedFor` TEXT)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_release_follows_key` " +
                        "ON `release_follows` (`key`)"
                )
                db.execSQL("ALTER TABLE games ADD COLUMN hltbMainMin INTEGER")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // IGDB 关联 + HLTB 三围（v0.15.1 引入，v0.15.4 移除；保留空转保证已升级用户能继续走）
                db.execSQL("ALTER TABLE games ADD COLUMN hltbExtraMin INTEGER")
                db.execSQL("ALTER TABLE games ADD COLUMN hltb100Min INTEGER")
                db.execSQL("ALTER TABLE games ADD COLUMN igdbId INTEGER")
                // v13 起实体声明 release_follows.key 唯一索引（schema 13 有记），但 v12 新装库
                // 按当时无索引的实体所建（schema 12 未记）——此处补建，否则 v12 一路升上来的库
                // 过 Room schema 校验会炸（启动崩溃）
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_release_follows_key` " +
                        "ON `release_follows` (`key`)"
                )
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 移除 IGDB/HLTB 列：SQLite 不支持 DROP COLUMN（旧版本），重建 games 表
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `games_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `platform` TEXT NOT NULL, " +
                        "`platformsCsv` TEXT, `coverUrl` TEXT, `bangumiId` INTEGER, " +
                        "`createdAt` INTEGER NOT NULL, `steamAppId` INTEGER, " +
                        "`steamPlaytimeMin` INTEGER, `steamSyncedAt` INTEGER, " +
                        "`switchAppId` TEXT, `psnTitleId` TEXT, `psnPlaytimeMin` INTEGER, " +
                        "`psnSyncedAt` INTEGER, `favorite` INTEGER NOT NULL DEFAULT 0, " +
                        "`completed` INTEGER NOT NULL DEFAULT 0, `completedAt` INTEGER, " +
                        "`rating` INTEGER, `thoughts` TEXT, `status` TEXT, `statusUpdatedAt` INTEGER, " +
                        "`hltbMainMin` INTEGER)"
                )
                db.execSQL(
                    "INSERT INTO games_new (id, name, platform, platformsCsv, coverUrl, bangumiId, " +
                        "createdAt, steamAppId, steamPlaytimeMin, steamSyncedAt, switchAppId, " +
                        "psnTitleId, psnPlaytimeMin, psnSyncedAt, favorite, completed, completedAt, " +
                        "rating, thoughts, status, statusUpdatedAt, hltbMainMin) " +
                        "SELECT id, name, platform, platformsCsv, coverUrl, bangumiId, " +
                        "createdAt, steamAppId, steamPlaytimeMin, steamSyncedAt, switchAppId, " +
                        "psnTitleId, psnPlaytimeMin, psnSyncedAt, favorite, completed, completedAt, " +
                        "rating, thoughts, status, statusUpdatedAt, hltbMainMin FROM games"
                )
                db.execSQL("DROP TABLE games")
                db.execSQL("ALTER TABLE games_new RENAME TO games")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // HLTB 写回：加回支线/全收集两列（null=未知，不回填）
                db.execSQL("ALTER TABLE games ADD COLUMN hltbExtraMin INTEGER")
                db.execSQL("ALTER TABLE games ADD COLUMN hltb100Min INTEGER")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 统一 pauseAccumMs 的 DDL：v8 升级用户经 ALTER 带 DEFAULT 0，新装建表没有，
                // 实体补 defaultValue="0" 后重建 play_sessions，升级装/新装结构一致（Room 校验口径统一）
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `play_sessions_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`gameId` INTEGER NOT NULL, " +
                        "`startTime` INTEGER NOT NULL, " +
                        "`endTime` INTEGER, " +
                        "`source` TEXT NOT NULL, " +
                        "`pauseAccumMs` INTEGER NOT NULL DEFAULT 0, " +
                        "`pauseStartedAt` INTEGER, " +
                        "FOREIGN KEY(`gameId`) REFERENCES `games`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "INSERT INTO play_sessions_new (id, gameId, startTime, endTime, source, pauseAccumMs, pauseStartedAt) " +
                        "SELECT id, gameId, startTime, endTime, source, pauseAccumMs, pauseStartedAt FROM play_sessions"
                )
                db.execSQL("DROP TABLE play_sessions")
                db.execSQL("ALTER TABLE play_sessions_new RENAME TO play_sessions")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_play_sessions_gameId` ON `play_sessions` (`gameId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_play_sessions_startTime` ON `play_sessions` (`startTime`)")
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // PSN 奖杯概要（v0.17）：已获得数四围 + 进度百分比 + 同步时间（null=未同步，不回填）
                db.execSQL("ALTER TABLE games ADD COLUMN trophyPlatinum INTEGER")
                db.execSQL("ALTER TABLE games ADD COLUMN trophyGold INTEGER")
                db.execSQL("ALTER TABLE games ADD COLUMN trophySilver INTEGER")
                db.execSQL("ALTER TABLE games ADD COLUMN trophyBronze INTEGER")
                db.execSQL("ALTER TABLE games ADD COLUMN trophyProgress INTEGER")
                db.execSQL("ALTER TABLE games ADD COLUMN trophyUpdatedAt INTEGER")
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // v0.18.6：play_sessions 加 estimated 标记（1 = 云端累计对账摊出的历史会话，
                // 日期是推算的，不计入区间统计）。列必须带 DEFAULT 0，与实体 default value 对齐。
                db.execSQL("ALTER TABLE play_sessions ADD COLUMN estimated INTEGER NOT NULL DEFAULT 0")
                // 历史遗留：v0.18.3–v0.18.5 写进来的云端会话把整份终身累计摊进了最近 60 天，
                // 正是「本月几千小时」的来源；旧行无法区分增量/历史，统一按估算处理（不清数据）
                db.execSQL("UPDATE play_sessions SET estimated = 1 WHERE source IN ('STEAM', 'SWITCH', 'PS')")
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // v0.18.8：取消「云端累计 − 本地记录」对账摊出的历史会话。
                // estimated=1 的行日期是推算出来的（v0.18.3–v0.18.5 摊出，v0.18.6 迁移时又回标了
                // 更早的云端行），正是「本月几千小时」的来源；先删干净，再把标记列移除，
                // 让记录页与本月/热力图/平台统计都只反映真实发生过的会话。
                db.execSQL("DELETE FROM play_sessions WHERE estimated = 1")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `play_sessions_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`gameId` INTEGER NOT NULL, " +
                        "`startTime` INTEGER NOT NULL, " +
                        "`endTime` INTEGER, " +
                        "`source` TEXT NOT NULL, " +
                        "`pauseAccumMs` INTEGER NOT NULL DEFAULT 0, " +
                        "`pauseStartedAt` INTEGER, " +
                        "FOREIGN KEY(`gameId`) REFERENCES `games`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "INSERT INTO play_sessions_new (id, gameId, startTime, endTime, source, pauseAccumMs, pauseStartedAt) " +
                        "SELECT id, gameId, startTime, endTime, source, pauseAccumMs, pauseStartedAt FROM play_sessions"
                )
                db.execSQL("DROP TABLE play_sessions")
                db.execSQL("ALTER TABLE play_sessions_new RENAME TO play_sessions")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_play_sessions_gameId` ON `play_sessions` (`gameId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_play_sessions_startTime` ON `play_sessions` (`startTime`)")
            }
        }

        /** 全量手写迁移（生产 build 与迁移测试共用一份，避免两处链不同步） */
        val ALL_MIGRATIONS = arrayOf(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
            MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
            MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
            MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18,
            MIGRATION_18_19,
        )

        fun build(context: Context): FinchDatabase =
            Room.databaseBuilder(context, FinchDatabase::class.java, "finch.db")
                .addMigrations(*ALL_MIGRATIONS)
                .build()
    }
}
