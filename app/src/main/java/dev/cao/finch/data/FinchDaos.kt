package dev.cao.finch.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Insert
    suspend fun insert(game: Game): Long

    @Update
    suspend fun update(game: Game)

    @Query("DELETE FROM games WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM games ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Game>>

    /** 按最近游玩排序（最后一次会话开始时间倒序，未玩过的按名字排最后） */
    @Query(
        """
        SELECT g.* FROM games g
        LEFT JOIN (SELECT gameId, MAX(startTime) AS lastPlayed FROM play_sessions GROUP BY gameId) s
        ON s.gameId = g.id
        ORDER BY s.lastPlayed IS NULL, s.lastPlayed DESC, g.name COLLATE NOCASE
        """
    )
    fun observeAllByRecentPlay(): Flow<List<Game>>

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun byId(id: Long): Game?

    @Query("SELECT * FROM games WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun byName(name: String): Game?

    @Query("SELECT * FROM games WHERE switchAppId = :appId LIMIT 1")
    suspend fun bySwitchAppId(appId: String): Game?

    @Query("SELECT * FROM games")
    suspend fun all(): List<Game>

    @Query("SELECT * FROM games WHERE name = :name COLLATE NOCASE")
    suspend fun allByName(name: String): List<Game>

    @Query("SELECT * FROM games WHERE name LIKE :query ORDER BY name COLLATE NOCASE LIMIT 10")
    suspend fun searchByName(query: String): List<Game>

    @Query("SELECT * FROM games WHERE bangumiId = :id LIMIT 1")
    suspend fun byBangumiId(id: Long): Game?

    @Query("SELECT * FROM games WHERE steamAppId = :appid LIMIT 1")
    suspend fun bySteamAppId(appid: Long): Game?

    @Query("UPDATE games SET bangumiId = :bangumiId WHERE id = :id")
    suspend fun setBangumiId(id: Long, bangumiId: Long)

    @Query("SELECT * FROM games WHERE id = :id")
    fun observeById(id: Long): Flow<Game?>

    @Query("SELECT COUNT(*) FROM games")
    suspend fun count(): Int
}

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: PlaySession): Long

    @Update
    suspend fun update(session: PlaySession)

    @Query("DELETE FROM play_sessions WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 修正异常的未来会话（同步占位偏差）：整体往前挪一天，落到昨天同一时刻 */
    @Query("UPDATE play_sessions SET startTime = startTime - 86400000, endTime = endTime - 86400000 WHERE startTime > :nowMillis")
    suspend fun fixFutureSessions(nowMillis: Long)

    @Query("SELECT * FROM play_sessions WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    fun observeRunning(): Flow<PlaySession?>

    @Query("SELECT * FROM play_sessions WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    suspend fun running(): PlaySession?

    /** 每款游戏最后游玩时间（会话里 endTime 非空才算完成游玩） */
    @Query(
        "SELECT gameId, MAX(startTime) AS lastPlayedAt FROM play_sessions " +
            "WHERE endTime IS NOT NULL GROUP BY gameId"
    )
    fun observeLastPlayedAll(): Flow<List<LastPlayedRow>>

    /** 某区间内玩过的不同游戏数 */
    @Query(
        "SELECT COUNT(DISTINCT gameId) FROM play_sessions " +
            "WHERE endTime IS NOT NULL AND startTime >= :fromMillis AND startTime < :toMillis"
    )
    fun observeDistinctGamesInRange(fromMillis: Long, toMillis: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM play_sessions WHERE endTime IS NULL")
    suspend fun runningCount(): Int

    @Transaction
    @Query("SELECT * FROM play_sessions ORDER BY startTime DESC LIMIT 300")
    fun observeRecentWithGame(): Flow<List<SessionWithGame>>

    @Query(
        """
        SELECT SUM(endTime - startTime) FROM play_sessions
        WHERE endTime IS NOT NULL AND startTime >= :fromMillis AND startTime < :toMillis
        """
    )
    fun observeTotalBetween(fromMillis: Long, toMillis: Long): Flow<Long?>

    @Query(
        """
        SELECT SUM(endTime - startTime) FROM play_sessions
        WHERE endTime IS NOT NULL AND startTime >= :fromMillis AND startTime < :toMillis
        """
    )
    suspend fun totalBetween(fromMillis: Long, toMillis: Long): Long?

    @Query(
        """
        SELECT COUNT(*) FROM play_sessions
        WHERE gameId = :gameId AND startTime >= :fromMillis AND startTime < :toMillis
        """
    )
    suspend fun countBetween(gameId: Long, fromMillis: Long, toMillis: Long): Long

    @Query(
        """
        SELECT strftime('%Y-%m-%d', startTime / 1000, 'unixepoch', 'localtime') AS day,
               SUM(endTime - startTime) AS totalMs
        FROM play_sessions
        WHERE endTime IS NOT NULL AND startTime >= :fromMillis AND startTime < :toMillis
        GROUP BY day ORDER BY day
        """
    )
    fun observeDailyTotals(fromMillis: Long, toMillis: Long): Flow<List<DailyTotal>>

    @Query(
        """
        SELECT g.platform AS platform, SUM(s.endTime - s.startTime) AS totalMs
        FROM play_sessions s JOIN games g ON g.id = s.gameId
        WHERE s.endTime IS NOT NULL AND s.startTime >= :fromMillis AND s.startTime < :toMillis
        GROUP BY g.platform
        """
    )
    fun observePlatformTotals(fromMillis: Long, toMillis: Long): Flow<List<PlatformTotal>>

    @Query("SELECT g.id AS gameId, g.name AS name, g.platform AS platform, g.coverUrl AS coverUrl,\n" +
        "       g.steamPlaytimeMin AS steamPlaytimeMin, SUM(s.endTime - s.startTime) AS totalMs, COUNT(s.id) AS sessionCount\n" +
        "FROM play_sessions s JOIN games g ON g.id = s.gameId\n" +
        "WHERE s.endTime IS NOT NULL AND s.startTime >= :fromMillis AND s.startTime < :toMillis\n" +
        "GROUP BY g.id ORDER BY totalMs DESC")
    fun observeTopGamesWithCover(fromMillis: Long, toMillis: Long): Flow<List<TopGameRow>>

    /** Steam 同步的终身累计总分钟（只对账展示，不生成会话） */
    @Query("SELECT SUM(steamPlaytimeMin) FROM games WHERE steamPlaytimeMin IS NOT NULL")
    fun observeSteamTotalMinutes(): Flow<Long?>
}

@Dao
interface SnapshotDao {
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: PlaytimeSnapshot)

    @Query("SELECT * FROM playtime_snapshots WHERE source = :source AND refKey = :refKey LIMIT 1")
    suspend fun byKey(source: String, refKey: String): PlaytimeSnapshot?

    @Query("SELECT * FROM playtime_snapshots WHERE source = :source ORDER BY at DESC LIMIT 1")
    suspend fun latest(source: String): PlaytimeSnapshot?
}
