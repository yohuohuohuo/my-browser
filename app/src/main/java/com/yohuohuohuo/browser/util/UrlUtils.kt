package com.yohuohuohuo.browser.util

import java.net.IDN
import java.net.Inet6Address
import java.net.InetAddress

object UrlUtils {

    private val DOMAIN_REGEX = Regex(
        "^[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?)*\\.[a-zA-Z]{2,}$"
    )
    private val IPV4_REGEX = Regex(
        "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$"
    )

    private fun isValidIpv6(host: String): Boolean {
        if (!host.contains(":")) return false
        return try {
            InetAddress.getByName(host) is Inet6Address
        } catch (_: Exception) {
            false
        }
    }

    private fun isValidPort(portText: String): Boolean {
        val port = portText.toIntOrNull() ?: return false
        return port in 0..65535
    }

    fun isUrl(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.contains(" ")) return false

        val withoutScheme = when {
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed.removeRange(0, 7)
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed.removeRange(0, 8)
            else -> trimmed
        }

        // 去掉用户信息 user:pass@
        val withoutUserInfo = if (withoutScheme.contains("@")) {
            val atIndex = withoutScheme.lastIndexOf("@")
            withoutScheme.substring(atIndex + 1)
        } else {
            withoutScheme
        }
        if (withoutUserInfo.isEmpty()) return false

        // 提取 host:port 部分（去掉路径、查询、片段）
        val hostPort = withoutUserInfo.substringBefore("/").substringBefore("?").substringBefore("#")
        if (hostPort.isEmpty()) return false

        // IPv6 必须使用标准的方括号形式，避免把地址中的冒号误判成端口。
        val host: String
        if (hostPort.startsWith("[")) {
            val closingBracket = hostPort.indexOf(']')
            if (closingBracket <= 1) return false
            host = hostPort.substring(1, closingBracket)
            val suffix = hostPort.substring(closingBracket + 1)
            if (suffix.isNotEmpty() &&
                (!suffix.startsWith(":") || !isValidPort(suffix.substring(1)))
            ) {
                return false
            }
        } else if (hostPort.count { it == ':' } == 1) {
            val separator = hostPort.indexOf(':')
            host = hostPort.substring(0, separator)
            if (host.isEmpty() || !isValidPort(hostPort.substring(separator + 1))) return false
        } else if (hostPort.contains(":")) {
            // 不接受未加方括号的 IPv6，保持与 http(s) URL 的标准表示一致。
            return false
        } else {
            host = hostPort
        }
        if (host.isEmpty()) return false

        // localhost
        if (host.equals("localhost", ignoreCase = true)) return true

        // IPv4
        if (IPV4_REGEX.matches(host)) return true

        // IPv6（仅接受上面解析出的方括号内容）
        if (isValidIpv6(host)) return true

        // 域名，允许 Unicode IDN，但用 ASCII 形式做结构校验。
        val asciiHost = try {
            IDN.toASCII(host)
        } catch (_: IllegalArgumentException) {
            return false
        }
        return DOMAIN_REGEX.matches(asciiHost)
    }

    fun normalizeUrl(text: String): String {
        val trimmed = text.trim()
        return if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }
}
