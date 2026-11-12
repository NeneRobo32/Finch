package dev.cao.finch.data

import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * WebDAV 云端备份客户端（v0.25.0）：
 *
 * 只做四件事 —— 探活、上传、列出、下载。**不删远端文件**（远端保留策略留给用户自己的
 * Nextcloud / 群晖客户端），也不做增量或冲突合并：一个账号一份 `finch-*.zip`，
 * 按文件名里的时间戳取最新。
 *
 * 认证只支持 HTTP Basic（Nextcloud / 群晖 / Box /坚果云都接受应用密码），
 * 因此**必须走 https**：明文 Basic 走 http 等于把密码写在信封上。
 * v0.29.4（审查 S1）：改为私网 http 放行、公网 http 拒绝，见 [normalizeBase]。
 */
internal object WebDavClient {

    /** 云端备份文件名前缀：只有带这个前缀的 zip 会被认成 Finch 备份 */
    const val PREFIX = "finch-cloud-"

    private val ZIP_MIME = "application/zip".toMediaType()

    data class Config(val baseUrl: String, val username: String, val password: String)

    data class RemoteFile(val name: String, val href: String)

    /**
     * 归一化目录地址（纯函数，单测直接调用）：
     * 去空白 → 补协议（缺省 https://）→ 确保以单个 `/` 结尾 → 校验 scheme。
     *
     * 审查 S1：https 直接放行；http 只在 localhost / 私网段允许（局域网自签端口是常见用法），
     * 公网 http 直接拒绝 —— Basic 认证在 http 下等于把密码抄在信封上，
     * 而出网的内容是整个游玩历史数据库。
     */
    fun normalizeBase(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val withScheme = when {
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed
            else -> "https://$trimmed"
        }
        val normalized = withScheme.trimEnd('/') + "/"
        require(isTransportSafe(normalized)) {
            "公网地址必须用 https（Basic 认证在 http 下是明文），请改填 https:// 开头的地址"
        }
        return normalized
    }

    /** http 只允许 localhost / 私网段（RFC1918 + 链路本地），其余 scheme 一律放行 */
    internal fun isTransportSafe(url: String): Boolean {
        if (!url.startsWith("http://", ignoreCase = true)) return true
        val host = runCatching {
            java.net.URI(url).host?.lowercase().orEmpty()
        }.getOrDefault("")
        if (host.isEmpty()) return false
        if (host == "localhost" || host == "127.0.0.1" || host == "::1") return true
        if (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("169.254.")) return true
        if (host.startsWith("172.")) {
            val second = host.split('.').getOrNull(1)?.toIntOrNull() ?: return false
            return second in 16..31
        }
        return false
    }

    /** Basic 认证头（纯函数，单测直接调用；委托给 OkHttp 的实现避免自己拼错 base64 口径） */
    fun authHeader(username: String, password: String): String = Credentials.basic(username, password)

    /** 是否是我们自己的云端备份（纯函数）：前缀 + .zip 后缀，大小写不敏感 */
    fun isBackupName(name: String): Boolean =
        name.startsWith(PREFIX, ignoreCase = true) && name.endsWith(".zip", ignoreCase = true)

    /** 从 PROPFIND 响应里抠出全部 href（纯函数）：兼容带/不带 `d:` 命名空间前缀 */
    fun parseHrefs(body: String): List<String> =
        Regex("<(?:[A-Za-z0-9]+:)?href>(.*?)</(?:[A-Za-z0-9]+:)?href>", RegexOption.DOT_MATCHES_ALL)
            .findAll(body)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotEmpty() }
            .toList()

    /** href → 最后一段文件名（纯函数）：URL 解码，目录项（以 / 结尾）返回空串 */
    fun fileNameOf(href: String): String {
        val decoded = runCatching { URLDecoder.decode(href, "UTF-8") }.getOrDefault(href)
        val cleaned = decoded.substringBefore('?').trimEnd('/')
        return cleaned.substringAfterLast('/')
    }

    /** 从远端文件列表里挑最新一份（纯函数）：只看自己的前缀，按文件名（含时间戳）取最大 */
    fun pickLatest(files: List<RemoteFile>): RemoteFile? =
        files.filter { isBackupName(it.name) }.maxByOrNull { it.name }

    /** 生成一次上传用的文件名（纯函数）：前缀 + `yyyyMMdd-HHmmss` */
    fun buildName(stamp: String): String = "$PREFIX$stamp.zip"

    /** 拼完整地址：目录 + 已编码的文件名 */
    fun fileUrl(baseUrl: String, name: String): String =
        normalizeBase(baseUrl) + URLEncoder.encode(name, "UTF-8").replace("+", "%20")

    /**
     * 探活：对目录发一次 PROPFIND（Depth: 0），返回服务端 `Server` 头（可能为空串）。
     * 目录不存在时先 MKCOL 建一层，再探一次——用户填的目录大概率还没建。
     */
    suspend fun probe(config: Config): String = runInterruptible(Dispatchers.IO) {
        if (config.baseUrl.isBlank()) throw IOException("先填 WebDAV 地址")
        val first = propfind(config, depth = "0")
        if (first.isSuccessful) return@runInterruptible first.header("Server").orEmpty()
        if (first.code == 404) {
            mkcol(config).close()
            val retry = propfind(config, depth = "0")
            if (retry.isSuccessful) return@runInterruptible retry.header("Server").orEmpty()
            throw HttpStatusException(retry.code, "目录不存在且创建失败（MKCOL ${retry.code}）")
        }
        throw HttpStatusException(first.code, when (first.code) {
            401 -> "认证失败：账号或应用密码不对"
            403 -> "没有权限访问这个目录"
            405 -> "这个地址不支持 WebDAV（服务器返回 405）"
            else -> "服务器返回 ${first.code}"
        })
    }

    /** 上传一个文件（覆盖同名文件），返回写入字节数 */
    suspend fun upload(config: Config, name: String, bytes: ByteArray): Int = runInterruptible(Dispatchers.IO) {
        val request = Request.Builder()
            .url(fileUrl(config.baseUrl, name))
            .header("Authorization", authHeader(config.username, config.password))
            .put(bytes.toRequestBody(ZIP_MIME))
            .build()
        HttpClients.shared.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw HttpStatusException(resp.code, when (resp.code) {
                    401 -> "认证失败：账号或应用密码不对"
                    403 -> "没有权限写入这个目录"
                    507 -> "云端空间不足（507）"
                    else -> "上传失败：服务器返回 ${resp.code}"
                })
            }
        }
        bytes.size
    }

    /** 列出目录下的备份文件（Depth: 1） */
    suspend fun list(config: Config): List<RemoteFile> = runInterruptible(Dispatchers.IO) {
        val resp = propfind(config, depth = "1")
        val body = resp.body?.string().orEmpty()
        if (!resp.isSuccessful) {
            throw HttpStatusException(resp.code, "列出云端文件失败：服务器返回 ${resp.code}")
        }
        // 目录自身也会出现在结果里（href 以 / 结尾），fileNameOf 会返回空串，自然被过滤掉
        parseHrefs(body)
            .map { RemoteFile(fileNameOf(it), it) }
            .filter { it.name.isNotEmpty() && isBackupName(it.name) }
    }

    /** 云端单份备份的下载上限，与 BackupManager 的解压上限同口径。审查 S3 */
    private const val MAX_DOWNLOAD_BYTES = 200L * 1024 * 1024

    /**
     * 下载指定文件；不存在抛异常由调用方给文案。
     * 审查 S3：改成边读边累计并封顶，满额即中止，避免云端被替换成超大文件时整包躺进堆里。
     */
    suspend fun download(config: Config, name: String): ByteArray = runInterruptible(Dispatchers.IO) {
        val request = Request.Builder()
            .url(fileUrl(config.baseUrl, name))
            .header("Authorization", authHeader(config.username, config.password))
            .get()
            .build()
        HttpClients.shared.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw HttpStatusException(resp.code, when (resp.code) {
                    401 -> "认证失败：账号或应用密码不对"
                    404 -> "云端没有这个备份了，重新上传一次"
                    else -> "下载失败：服务器返回 ${resp.code}"
                })
            }
            val body = resp.body ?: throw IOException("云端返回空文件")
            // 先用 Content-Length 挡一次，省得白读 200MB
            if (body.contentLength() > MAX_DOWNLOAD_BYTES) {
                throw IOException("云端文件异常（超过 200MB），已中止")
            }
            val sink = java.io.ByteArrayOutputStream()
            body.byteStream().use { input ->
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_DOWNLOAD_BYTES) throw IOException("云端文件异常（超过 200MB），已中止")
                    sink.write(buf, 0, n)
                }
            }
            sink.toByteArray()
        }
    }

    private fun propfind(config: Config, depth: String) = HttpClients.shared.newCall(
        Request.Builder()
            .url(normalizeBase(config.baseUrl))
            .header("Authorization", authHeader(config.username, config.password))
            .header("Depth", depth)
            .method(
                "PROPFIND",
                """<?xml version="1.0" encoding="utf-8"?>
                   |<d:propfind xmlns:d="DAV:">
                   |  <d:prop><d:resourcetype/><d:getcontentlength/></d:prop>
                   |</d:propfind>""".trimMargin().toRequestBody("application/xml; charset=utf-8".toMediaType()),
            )
            .build()
    ).execute()

    private fun mkcol(config: Config) = HttpClients.shared.newCall(
        Request.Builder()
            .url(normalizeBase(config.baseUrl))
            .header("Authorization", authHeader(config.username, config.password))
            .method("MKCOL", ByteArray(0).toRequestBody(null))
            .build()
    ).execute()
}
