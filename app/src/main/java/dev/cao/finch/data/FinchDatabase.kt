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
const val FINCH_DB_VERSION = 14

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

        fun build(context: Context): FinchDatabase =
            Room.databaseBuilder(context, FinchDatabase::class.java, "finch.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
                .build()
    }
}
