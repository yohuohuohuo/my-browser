package com.yohuohuohuo.browser.ui

object BrowserLayoutMetrics {
    fun contentTopInset(
        statusBarInset: Int,
        toolbarHeight: Int,
        toolbarHidden: Boolean
    ): Int {
        val safeStatusBarInset = statusBarInset.coerceAtLeast(0)
        val safeToolbarHeight = toolbarHeight.coerceAtLeast(0)
        return if (toolbarHidden) {
            safeStatusBarInset
        } else {
            maxOf(safeStatusBarInset, safeToolbarHeight)
        }
    }

    fun toolbarHideTranslation(toolbarHeight: Int): Float {
        val safeToolbarHeight = toolbarHeight.coerceAtLeast(0)
        return if (safeToolbarHeight == 0) 0f else -safeToolbarHeight.toFloat()
    }
}
