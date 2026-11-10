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
 *
 * v0.29（DB 23）加了价格快照 5 列：愿望单页要在离线时也能看到价格/折扣/截止日，
 * 而港服价格是网络数据，只能在拉到数据时顺手回写（[ReleaseFollowDao.updatePrice]）。
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
    // ---- 价格快照（v0.29，只有 PSN 港服条目有值）----
    /** 现价 "HK$388.00"；无价格信息时 null */
    val priceText: String? = null,
    /** 原价（仅打折时非空，愿望单页划线用） */
    val basePriceText: String? = null,
    /** 折扣文案 "-70%" */
    val discountText: String? = null,
    /** 本档折扣截止日 ISO；null=没有折扣或未知 */
    val saleEndIso: String? = null,
    /** 港服商品 ID（拼商店链接 + 价格同步用） */
    val productId: String? = null,
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

    /** 回写价格快照（v0.29）：拉到港服数据时顺手更新，愿望单页离线也有价可看 */
    @Query(
        """
        UPDATE release_follows
        SET priceText = :priceText,
            basePriceText = :basePriceText,
            discountText = :discountText,
            saleEndIso = :saleEndIso,
            productId = :productId
        WHERE `key` = :key
        """
    )
    suspend fun updatePrice(
        key: String,
        priceText: String?,
        basePriceText: String?,
        discountText: String?,
        saleEndIso: String?,
        productId: String?,
    )
}
