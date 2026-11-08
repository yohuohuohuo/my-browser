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
    fun `isUrl returns true for localhost`() {
        assertTrue(UrlUtils.isUrl("localhost"))
        assertTrue(UrlUtils.isUrl("localhost:8080"))
        assertTrue(UrlUtils.isUrl("http://localhost/path"))
    }

    @Test
    fun `isUrl returns true for IPv4 address`() {
        assertTrue(UrlUtils.isUrl("192.168.1.1"))
        assertTrue(UrlUtils.isUrl("10.0.0.1"))
        assertTrue(UrlUtils.isUrl("127.0.0.1"))
        assertTrue(UrlUtils.isUrl("http://192.168.1.1/path"))
    }

    @Test
    fun `isUrl returns true for URL with port`() {
        assertTrue(UrlUtils.isUrl("example.com:8080"))
        assertTrue(UrlUtils.isUrl("192.168.1.1:3000"))
        assertTrue(UrlUtils.isUrl("https://example.com:443/path"))
    }

    @Test
    fun `isUrl returns true for bracketed IPv6 with optional port`() {
        assertTrue(UrlUtils.isUrl("[::1]"))
        assertTrue(UrlUtils.isUrl("http://[2001:db8::1]:8443/path"))
    }

    @Test
    fun `isUrl returns false for malformed IPv6 host`() {
        assertFalse(UrlUtils.isUrl("http://[::1"))
        assertFalse(UrlUtils.isUrl("http://[::1]:abc"))
    }

    @Test
    fun `isUrl returns true for URL with user info`() {
        assertTrue(UrlUtils.isUrl("user:pass@example.com"))
        assertTrue(UrlUtils.isUrl("http://user:pass@example.com/path"))
    }

    @Test
    fun `at signs outside authority do not alter host validation`() {
        listOf(
            "https://example.com/@alice", "example.com/path/@alice",
            "https://example.com?email=@alice", "https://example.com#@alice",
            "http://user:pass@example.com:8080/@alice?q=@bob#@carol",
            "http://127.0.0.1:3000/@alice", "http://[::1]:8443/@alice",
            "https://例子.com/@alice"
        ).forEach { assertTrue(it, UrlUtils.isUrl(it)) }
        listOf(
            "https://invalid/@example.com", "https://example.com:99999/@valid.com",
            "http://[::1]:abc/@valid.com", "https:///@example.com"
        ).forEach { assertFalse(it, UrlUtils.isUrl(it)) }
    }

    @Test
    fun `isUrl returns false for invalid port`() {
        assertFalse(UrlUtils.isUrl("example.com:99999"))
        assertFalse(UrlUtils.isUrl("example.com:abc"))
    }

    @Test
    fun `isUrl returns false for invalid IPv4`() {
        assertFalse(UrlUtils.isUrl("256.1.1.1"))
        assertFalse(UrlUtils.isUrl("1.2.3"))
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
        assertFalse(UrlUtils.isUrl("android"))
        assertFalse(UrlUtils.isUrl("browser"))
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
    fun `normalizeUrl is case insensitive for scheme`() {
        assertEquals("HTTP://example.com", UrlUtils.normalizeUrl("HTTP://example.com"))
        assertEquals("HTTPS://example.com", UrlUtils.normalizeUrl("HTTPS://example.com"))
    }

    @Test
    fun `normalizeUrl trims whitespace`() {
        assertEquals("https://example.com", UrlUtils.normalizeUrl("  example.com  "))
    }
}
