package com.yohuohuohuo.browser.util

import org.junit.Assert.*
import org.junit.Test
import java.net.URLDecoder

class SearchEngineTest {

    @Test
    fun `buildSearchUrl uses baidu`() {
        val url = SearchEngine.buildSearchUrl("test")
        assertTrue(url.startsWith("https://www.baidu.com/s?wd="))
    }

    @Test
    fun `buildSearchUrl encodes query`() {
        val url = SearchEngine.buildSearchUrl("hello world")
        val encoded = url.substringAfter("wd=")
        assertEquals("hello+world", encoded)
    }

    @Test
    fun `buildSearchUrl encodes chinese characters`() {
        val url = SearchEngine.buildSearchUrl("你好")
        val encoded = url.substringAfter("wd=")
        val decoded = URLDecoder.decode(encoded, "UTF-8")
        assertEquals("你好", decoded)
    }

    @Test
    fun `buildSearchUrl trims query`() {
        val url = SearchEngine.buildSearchUrl("  test  ")
        val encoded = url.substringAfter("wd=")
        assertEquals("test", encoded)
    }
}
