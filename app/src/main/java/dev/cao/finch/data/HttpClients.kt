package dev.cao.finch.data

import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 全局共享 OkHttpClient（G.1 收敛）：所有数据客户端共用一个连接池/线程池，
 * 超时口径统一 —— connect 5s / read 10s / write 10s / **call 30s**。
 * callTimeout 是关键：没有它时慢滴响应会无限挂住调用线程。
 * 需要差异配置的客户端用 shared.newBuilder() 仅覆盖差异（如 PsnClient 的 noRedirectClient）。
 */
internal object HttpClients {
    val shared: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

/** HTTP 状态错误（带状态码）：重试策略按码段判断 —— 5xx/429 重试，4xx（无效 Key 等）直接失败 */
internal class HttpStatusException(val code: Int, message: String) : IOException(message)

/** 是否值得重试：网络 IO/超时、5xx、429 重试；4xx 与非 IO 异常（解析等）直接失败 */
internal fun isRetryable(e: Exception): Boolean = when (e) {
    is HttpStatusException -> e.code >= 500 || e.code == 429
    is IOException -> true
    else -> false
}

/**
 * 可中断的退避等待（非 suspend API 的方案 b）：
 * 被中断时恢复中断标记并抛 IOException，不再静默吞掉 InterruptedException。
 */
internal fun interruptibleBackoff(ms: Long) {
    try {
        Thread.sleep(ms)
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        throw IOException("等待被中断（已取消）", e)
    }
}
