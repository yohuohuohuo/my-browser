package com.yohuohuohuo.browser.util

object UrlUtils {

    private val DOMAIN_REGEX = Regex("^[a-zA-Z0-9][a-zA-Z0-9.-]*\\.[a-zA-Z]{2,}$")

    fun isUrl(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        val withoutScheme = when {
            trimmed.startsWith("http://") -> trimmed.removePrefix("http://")
            trimmed.startsWith("https://") -> trimmed.removePrefix("https://")
            else -> trimmed
        }
        // Contains dot and no spaces -> likely a domain
        if (withoutScheme.contains(".") && !withoutScheme.contains(" ")) {
            val domainPart = withoutScheme.substringBefore("/")
            return domainPart.matches(DOMAIN_REGEX)
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
