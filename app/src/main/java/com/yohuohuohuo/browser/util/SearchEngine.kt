package com.yohuohuohuo.browser.util

import java.net.URLEncoder

object SearchEngine {
    private const val BAIDU_SEARCH_URL = "https://www.baidu.com/s?wd="

    fun buildSearchUrl(query: String): String {
        return BAIDU_SEARCH_URL + URLEncoder.encode(query.trim(), "UTF-8")
    }
}
