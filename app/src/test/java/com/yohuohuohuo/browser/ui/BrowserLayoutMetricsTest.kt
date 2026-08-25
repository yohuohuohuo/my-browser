package com.yohuohuohuo.browser.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserLayoutMetricsTest {

    @Test
    fun `visible toolbar reserves its full height while hidden content keeps status bar inset`() {
        assertEquals(
            88,
            BrowserLayoutMetrics.contentTopInset(
                statusBarInset = 24,
                toolbarHeight = 88,
                toolbarHidden = false
            )
        )
        assertEquals(
            24,
            BrowserLayoutMetrics.contentTopInset(
                statusBarInset = 24,
                toolbarHeight = 88,
                toolbarHidden = true
            )
        )
    }

    @Test
    fun `toolbar hides completely instead of remaining in the status bar area`() {
        assertEquals(
            -88f,
            BrowserLayoutMetrics.toolbarHideTranslation(
                toolbarHeight = 88
            )
        )
        assertEquals(
            0f,
            BrowserLayoutMetrics.toolbarHideTranslation(
                toolbarHeight = 0
            )
        )
    }
}
