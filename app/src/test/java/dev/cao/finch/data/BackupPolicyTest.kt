package dev.cao.finch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** BackupManager 校验决策的纯函数单测（缺核心表 / meta 标识与版本），JVM 直接可跑无需 Android API。
 *  对应修复：任意 SQLite 文件/伪造备份不得通过校验顶掉 finch.db。 */
class BackupPolicyTest {

    private val full = setOf("games", "play_sessions", "playtime_snapshots", "release_follows")

    @Test
    fun `核心表齐全时无缺失`() {
        assertTrue(BackupManager.missingCoreTables(full, 16).isEmpty())
        assertTrue(BackupManager.missingCoreTables(full, 7).isEmpty())
    }

    @Test
    fun `缺核心表被点名`() {
        val missing = BackupManager.missingCoreTables(setOf("games"), 16)
        assertEquals(listOf("play_sessions", "playtime_snapshots", "release_follows"), missing)
    }

    @Test
    fun `DB12之前不要求release_follows`() {
        assertTrue(BackupManager.missingCoreTables(full - "release_follows", 11).isEmpty())
        assertEquals(
            listOf("release_follows"),
            BackupManager.missingCoreTables(full - "release_follows", 12),
        )
    }

    @Test
    fun `meta标识不是finch一律拒绝`() {
        assertNotNull(BackupManager.validateBackupMeta("other", 15, 16))
        assertNotNull(BackupManager.validateBackupMeta("", 15, 16))
        assertNotNull(BackupManager.validateBackupMeta(null, 15, 16))
    }

    @Test
    fun `meta版本高于当前应用拒绝_旧版或同版放行`() {
        assertNotNull(BackupManager.validateBackupMeta("finch", 17, 16))
        assertNull(BackupManager.validateBackupMeta("finch", 16, 16))
        assertNull(BackupManager.validateBackupMeta("finch", 7, 16))
    }
}
