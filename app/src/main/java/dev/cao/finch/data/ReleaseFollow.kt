package dev.cao.finch.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 发售关注（v0.15）：日程页 ☆ 关注的游戏，发售前 N 天本地推送提醒。
 * - key：去重键（bangumi:<id> / steam:<appid> / name:<小写去空格名>），唯一索引
 * - dateIso：发售日 "yyyy-MM-dd"，null=无日期（只展示不提醒）
 * - notifiedFor：已提醒过的日期 ISO（避免同一天重复推；日期变更自动失效）
 */
@Entity(
    tableName = "release_follows",
    indices = [Index(value = ["key"], unique = true)],
)
data class ReleaseFollow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val name: String,
    val coverUrl: String? = null,
    val dateIso: String? = null,
    val source: String = "",
    val notifyDays: Int = 3,
    val addedAt: Long = System.currentTimeMillis(),
    val notifiedFor: String? = null,
)

@Dao
interface ReleaseFollowDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(follow: ReleaseFollow): Long

    @Query("DELETE FROM release_follows WHERE `key` = :key")
    suspend fun deleteByKey(key: String)

    @Query("SELECT * FROM release_follows WHERE `key` = :key LIMIT 1")
    suspend fun byKey(key: String): ReleaseFollow?

    @Query("SELECT `key` FROM release_follows")
    fun observeKeys(): Flow<List<String>>

    @Query("SELECT * FROM release_follows ORDER BY dateIso IS NULL, dateIso, addedAt DESC")
    fun observeAll(): Flow<List<ReleaseFollow>>

    @Query("SELECT * FROM release_follows")
    suspend fun all(): List<ReleaseFollow>

    /** 标记某日期已提醒（同 key 同日期只推一次） */
    @Query("UPDATE release_follows SET notifiedFor = :dateIso WHERE `key` = :key")
    suspend fun markNotified(key: String, dateIso: String)
}
