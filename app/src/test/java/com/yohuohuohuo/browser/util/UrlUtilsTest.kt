package com.yohuohuohuo.browser.util

import org.junit.Assert.*
import org.junit.Test

class UrlUtilsTest {

    @Test
    fun `isUrl returns true for http URL`() {
        assertTrue(UrlUtils.isUrl("http://example.com"))
    }

    @Test
    fun `isUrl returns true for https URL`() {
        assertTrue(UrlUtils.isUrl("https://example.com/path?query=1"))
    }

    @Test
    fun `isUrl returns true for domain without scheme`() {
        assertTrue(UrlUtils.isUrl("example.com"))
        assertTrue(UrlUtils.isUrl("www.example.com"))
        assertTrue(UrlUtils.isUrl("sub.example.com/path"))
    }

    @Test
    fun `isUrl returns false for plain text`() {
        assertFalse(UrlUtils.isUrl("hello world"))
        assertFalse(UrlUtils.isUrl("android tutorial"))
    }

    @Test
    fun `isUrl returns false for empty string`() {
        assertFalse(UrlUtils.isUrl(""))
        assertFalse(UrlUtils.isUrl("   "))
    }

    @Test
    fun `isUrl returns false for single word without dot`() {
        assertFalse(UrlUtils.isUrl("localhost"))
    }

    @Test
    fun `normalizeUrl adds https scheme`() {
        assertEquals("https://example.com", UrlUtils.normalizeUrl("example.com"))
    }

    @Test
    fun `normalizeUrl keeps existing scheme`() {
        assertEquals("http://example.com", UrlUtils.normalizeUrl("http://example.com"))
        assertEquals("https://example.com", UrlUtils.normalizeUrl("https://example.com"))
    }

    @Test
    fun `normalizeUrl trims whitespace`() {
        assertEquals("https://example.com", UrlUtils.normalizeUrl("  example.com  "))
    }
}
