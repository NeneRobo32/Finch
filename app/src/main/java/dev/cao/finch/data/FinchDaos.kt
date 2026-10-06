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

    @Query("SELECT * FROM games WHERE switchAppId = :appId ORDER BY id LIMIT 1")
    suspend fun bySwitchAppId(appId: String): Game?

    @Query("SELECT * FROM games")
    suspend fun all(): List<Game>

    /** 同名游戏可能有多行（PC + Switch/PS 同名常见），按 id 定序保证挑选确定性 */
    @Query("SELECT * FROM games WHERE name = :name COLLATE NOCASE ORDER BY id")
    suspend fun allByName(name: String): List<Game>

    @Query("SELECT * FROM games WHERE name LIKE :query ESCAPE '\\' ORDER BY name COLLATE NOCASE LIMIT 10")
    suspend fun searchByName(query: String): List<Game>

    @Query("SELECT * FROM games WHERE bangumiId = :id ORDER BY id LIMIT 1")
    suspend fun byBangumiId(id: Long): Game?

    @Query("SELECT * FROM games WHERE steamAppId = :appid ORDER BY id LIMIT 1")
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

    /** 修正异常的未来会话（同步占位偏差）：已结束的前移到「恰好在现在之前」结束（时长不变），
     *  进行中的起点拉回现在。旧版一律挪 24h，微超前的会话会被错挪到昨天 */
    @Query(
        "UPDATE play_sessions SET " +
            "endTime = CASE WHEN endTime IS NULL THEN NULL ELSE :nowMillis END, " +
            "startTime = CASE WHEN endTime IS NULL THEN :nowMillis ELSE :nowMillis - (endTime - startTime) END " +
            "WHERE startTime > :nowMillis"
    )
    suspend fun fixFutureSessions(nowMillis: Long)

    @Query("SELECT * FROM play_sessions WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    fun observeRunning(): Flow<PlaySession?>

    @Query("SELECT * FROM play_sessions WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    suspend fun running(): PlaySession?

    @Query("SELECT * FROM play_sessions WHERE id = :id")
    suspend fun byId(id: Long): PlaySession?

    /** 每款游戏的已完成会话累计（扣暂停），主页按总时长排序用 */
    @Query(
        "SELECT gameId, SUM(MAX(0, endTime - startTime - COALESCE(pauseAccumMs, 0))) AS totalMs " +
            "FROM play_sessions WHERE endTime IS NOT NULL GROUP BY gameId"
    )
    fun observeTotalsAll(): Flow<List<GameTotalMini>>

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
        SELECT SUM(MAX(0, endTime - startTime - COALESCE(pauseAccumMs, 0))) FROM play_sessions
        WHERE endTime IS NOT NULL AND startTime >= :fromMillis AND startTime < :toMillis
        """
    )
    fun observeTotalBetween(fromMillis: Long, toMillis: Long): Flow<Long?>

    @Query(
        """
        SELECT SUM(MAX(0, endTime - startTime - COALESCE(pauseAccumMs, 0))) FROM play_sessions
        WHERE endTime IS NOT NULL AND startTime >= :fromMillis AND startTime < :toMillis
        """
    )
    suspend fun totalBetween(fromMillis: Long, toMillis: Long): Long?

    /** 同游戏同来源同起点的占位会话（同步去重/幂等更新用；同一条占位只会有一行） */
    @Query(
        "SELECT * FROM play_sessions WHERE gameId = :gameId AND source = :source AND startTime = :startMillis LIMIT 1"
    )
    suspend fun at(gameId: Long, startMillis: Long, source: SessionSource): PlaySession?

    @Query(
        """
        SELECT strftime('%Y-%m-%d', startTime / 1000, 'unixepoch', 'localtime') AS day,
               SUM(MAX(0, endTime - startTime - COALESCE(pauseAccumMs, 0))) AS totalMs
        FROM play_sessions
        WHERE endTime IS NOT NULL AND startTime >= :fromMillis AND startTime < :toMillis
        GROUP BY day ORDER BY day
        """
    )
    fun observeDailyTotals(fromMillis: Long, toMillis: Long): Flow<List<DailyTotal>>

    @Query(
        """
        SELECT g.platform AS platform, SUM(MAX(0, s.endTime - s.startTime - COALESCE(s.pauseAccumMs, 0))) AS totalMs
        FROM play_sessions s JOIN games g ON g.id = s.gameId
        WHERE s.endTime IS NOT NULL AND s.startTime >= :fromMillis AND s.startTime < :toMillis
        GROUP BY g.platform
        """
    )
    fun observePlatformTotals(fromMillis: Long, toMillis: Long): Flow<List<PlatformTotal>>

    @Query("SELECT g.id AS gameId, g.name AS name, g.platform AS platform, g.coverUrl AS coverUrl,\n" +
        "       g.steamPlaytimeMin AS steamPlaytimeMin, SUM(MAX(0, s.endTime - s.startTime - COALESCE(s.pauseAccumMs, 0))) AS totalMs, COUNT(s.id) AS sessionCount\n" +
        "FROM play_sessions s JOIN games g ON g.id = s.gameId\n" +
        "WHERE s.endTime IS NOT NULL AND s.startTime >= :fromMillis AND s.startTime < :toMillis\n" +
        "GROUP BY g.id ORDER BY totalMs DESC")
    fun observeTopGamesWithCover(fromMillis: Long, toMillis: Long): Flow<List<TopGameRow>>

    /** Steam 同步的终身累计总分钟（只对账展示，不生成会话） */
    @Query("SELECT SUM(steamPlaytimeMin) FROM games WHERE steamPlaytimeMin IS NOT NULL")
    fun observeSteamTotalMinutes(): Flow<Long?>

    /** 某游戏的会话历史（最近 50 条已完成） */
    @Query("SELECT * FROM play_sessions WHERE gameId = :gameId AND endTime IS NOT NULL ORDER BY startTime DESC LIMIT 50")
    fun observeSessionsForGame(gameId: Long): Flow<List<PlaySession>>

    /** 某游戏的累计统计（总时长扣掉暂停 / 会话数 / 最近游玩） */
    @Query(
        """
        SELECT SUM(MAX(0, endTime - startTime - COALESCE(pauseAccumMs, 0))) AS totalMs, COUNT(id) AS sessionCount, MAX(startTime) AS lastPlayedAt
        FROM play_sessions WHERE gameId = :gameId AND endTime IS NOT NULL
        """
    )
    fun observeStatsForGame(gameId: Long): Flow<GameStatsRow?>

    /** 某游戏累计毫秒（通关联动封顶用，一次性 suspend 查询） */
    @Query(
        """
        SELECT COALESCE(SUM(MAX(0, endTime - startTime - COALESCE(pauseAccumMs, 0))), 0) FROM play_sessions
        WHERE gameId = :gameId AND endTime IS NOT NULL
        """
    )
    suspend fun totalMsForGame(gameId: Long): Long

    /** 时段分布：按周几聚合（0=周日..6=周六，SQLite %w 口径），时长扣暂停 */
    @Query(
        """
        SELECT CAST(strftime('%w', startTime / 1000, 'unixepoch', 'localtime') AS INTEGER) AS bucket,
               SUM(MAX(0, endTime - startTime - COALESCE(pauseAccumMs, 0))) AS totalMs
        FROM play_sessions
        WHERE endTime IS NOT NULL AND startTime >= :fromMillis AND startTime < :toMillis
        GROUP BY bucket ORDER BY bucket
        """
    )
    fun observeWeekdayTotals(fromMillis: Long, toMillis: Long): Flow<List<BucketTotal>>

    /** 时段分布：按小时聚合（0..23，本地时间），时长扣暂停 */
    @Query(
        """
        SELECT CAST(strftime('%H', startTime / 1000, 'unixepoch', 'localtime') AS INTEGER) AS bucket,
               SUM(MAX(0, endTime - startTime - COALESCE(pauseAccumMs, 0))) AS totalMs
        FROM play_sessions
        WHERE endTime IS NOT NULL AND startTime >= :fromMillis AND startTime < :toMillis
        GROUP BY bucket ORDER BY bucket
        """
    )
    fun observeHourTotals(fromMillis: Long, toMillis: Long): Flow<List<BucketTotal>>
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
