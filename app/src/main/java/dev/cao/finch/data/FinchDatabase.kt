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
const val FINCH_DB_VERSION = 6

@Database(
    entities = [Game::class, PlaySession::class, PlaytimeSnapshot::class],
    version = FINCH_DB_VERSION,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class FinchDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
    abstract fun sessionDao(): SessionDao
    abstract fun snapshotDao(): SnapshotDao

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

        fun build(context: Context): FinchDatabase =
            Room.databaseBuilder(context, FinchDatabase::class.java, "finch.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()
    }
}
