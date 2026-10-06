package dev.cao.finch.data

/**
 * 网络异常分类（原 ImportViewModel×3 / UpcomingViewModel×1 各自重复的类型判断样板收敛于此）。
 * 各调用点的用户文案保持自己的一套（Steam 反代/任天堂/PSN/发售源语境不同），只共享分类口径。
 */
internal enum class NetworkErrorKind { DNS, CONNECT, TIMEOUT, SSL, IO, OTHER }

internal fun Throwable.networkErrorKind(): NetworkErrorKind = when (this) {
    is java.net.UnknownHostException -> NetworkErrorKind.DNS
    is java.net.ConnectException -> NetworkErrorKind.CONNECT
    is java.net.SocketTimeoutException -> NetworkErrorKind.TIMEOUT
    is javax.net.ssl.SSLException -> NetworkErrorKind.SSL
    is java.io.IOException -> NetworkErrorKind.IO
    else -> NetworkErrorKind.OTHER
}
