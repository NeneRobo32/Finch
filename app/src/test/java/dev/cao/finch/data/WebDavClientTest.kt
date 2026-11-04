package dev.cao.finch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WebDAV 云备份的纯逻辑单测（v0.25）：
 * 只覆盖地址归一化、文件识别、PROPFIND 解析这些不依赖网络的部分。
 * 真正的 HTTP 交互靠手动「保存并测试」验证，不在这里打线上请求。
 */
class WebDavClientTest {

    // ---------- normalizeBase ----------

    @Test
    fun `缺协议的地址补上https`() {
        assertEquals("https://dav.example.com/finch/", WebDavClient.normalizeBase("dav.example.com/finch"))
    }

    @Test
    fun `已有协议不重复添加且补尾斜杠`() {
        assertEquals("http://192.168.1.10/dav/", WebDavClient.normalizeBase("http://192.168.1.10/dav"))
    }

    @Test
    fun `多余空白与尾斜杠被归一`() {
        assertEquals("https://dav.example.com/", WebDavClient.normalizeBase("  https://dav.example.com///  "))
    }

    @Test
    fun `空地址归一为空串`() {
        assertEquals("", WebDavClient.normalizeBase("   "))
    }

    // ---------- isBackupName ----------

    @Test
    fun `只认finch-cloud前缀的zip`() {
        assertTrue(WebDavClient.isBackupName("finch-cloud-20261104-235900.zip"))
        assertTrue(WebDavClient.isBackupName("FINCH-CLOUD-20261104.zip"))
        assertFalse(WebDavClient.isBackupName("finch-backup-20261104.zip"))
        assertFalse(WebDavClient.isBackupName("finch-cloud-20261104.txt"))
    }

    // ---------- parseHrefs / fileNameOf ----------

    @Test
    fun `解析带命名空间的href`() {
        val body = """
            <d:multistatus xmlns:d="DAV:">
              <d:response><d:href>/dav/finch/</d:href></d:response>
              <d:response><d:href>/dav/finch/finch-cloud-1.zip</d:href></d:response>
              <d:response><d:href>/dav/finch/finch-cloud-2.zip</d:href></d:response>
            </d:multistatus>
        """.trimIndent()
        val hrefs = WebDavClient.parseHrefs(body)
        assertEquals(3, hrefs.size)
        assertTrue(hrefs.contains("/dav/finch/finch-cloud-1.zip"))
        assertEquals(2, hrefs.map(WebDavClient::fileNameOf).count(WebDavClient::isBackupName))
    }

    @Test
    fun `文件名会解码百分号转义并去掉尾部斜杠`() {
        assertEquals("finch-cloud-2026.zip", WebDavClient.fileNameOf("/dav/finch/finch-cloud-2026.zip"))
        assertEquals("finch cloud.zip", WebDavClient.fileNameOf("/dav/finch/finch%20cloud.zip"))
        assertEquals(
            "finch-cloud-2026.zip",
            WebDavClient.fileNameOf("/dav/finch/finch-cloud-2026.zip?token=abc"),
        )
        assertEquals("finch", WebDavClient.fileNameOf("/dav/finch/"))
    }

    // ---------- pickLatest ----------

    @Test
    fun `挑出名字最大的那份备份`() {
        val files = listOf(
            WebDavClient.RemoteFile("finch-cloud-20261101-100000.zip", "/d/finch-cloud-20261101-100000.zip"),
            WebDavClient.RemoteFile("finch-cloud-20261103-090000.zip", "/d/finch-cloud-20261103-090000.zip"),
            WebDavClient.RemoteFile("other.txt", "/d/other.txt"),
        )
        assertEquals("finch-cloud-20261103-090000.zip", WebDavClient.pickLatest(files)?.name)
    }

    @Test
    fun `没有备份文件时返回null`() {
        assertNull(WebDavClient.pickLatest(emptyList()))
        assertNull(WebDavClient.pickLatest(listOf(WebDavClient.RemoteFile("a.txt", "/d/a.txt"))))
    }

    // ---------- buildName / fileUrl ----------

    @Test
    fun `备份文件名带统一前缀`() {
        assertEquals("finch-cloud-20261104-120000.zip", WebDavClient.buildName("20261104-120000"))
    }

    @Test
    fun `下载地址对文件名做转义`() {
        assertEquals(
            "https://dav.example.com/finch/finch-cloud-a.zip",
            WebDavClient.fileUrl("https://dav.example.com/finch", "finch-cloud-a.zip"),
        )
        assertEquals(
            "https://dav.example.com/finch/finch%20cloud.zip",
            WebDavClient.fileUrl("https://dav.example.com/finch", "finch cloud.zip"),
        )
    }
}
