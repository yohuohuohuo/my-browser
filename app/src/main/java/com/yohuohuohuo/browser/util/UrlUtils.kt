package com.yohuohuohuo.browser.util

import android.util.Patterns

object UrlUtils {

    fun isUrl(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        // Has scheme
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return Patterns.WEB_URL.matcher(trimmed).matches()
        }
        // Contains dot and no spaces -> likely a domain
        if (trimmed.contains(".") && !trimmed.contains(" ")) {
            val domainPart = trimmed.substringBefore("/")
            return domainPart.matches(Regex("^[a-zA-Z0-9][a-zA-Z0-9.-]*\\.[a-zA-Z]{2,}$"))
        }
        return false
    }

    fun normalizeUrl(text: String): String {
        val trimmed = text.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }
}
