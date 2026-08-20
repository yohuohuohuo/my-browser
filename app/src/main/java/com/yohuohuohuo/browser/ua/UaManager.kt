package com.yohuohuohuo.browser.ua

import android.content.Context
import android.content.SharedPreferences
import android.webkit.WebSettings

class UaManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("browser_prefs", Context.MODE_PRIVATE)

    private val defaultMobileUa: String = WebSettings.getDefaultUserAgent(context)

    private val desktopUa: String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    fun isDesktop(): Boolean = prefs.getBoolean(KEY_UA_DESKTOP, false)

    fun getUa(): String = if (isDesktop()) desktopUa else defaultMobileUa

    fun toggle(): Boolean {
        val newMode = !isDesktop()
        prefs.edit().putBoolean(KEY_UA_DESKTOP, newMode).apply()
        return newMode
    }

    companion object {
        private const val KEY_UA_DESKTOP = "ua_desktop"
    }
}
