package com.yohuohuohuo.browser.web

import android.annotation.SuppressLint
import android.webkit.WebSettings
import android.webkit.WebView

class WebViewManager {

    @SuppressLint("SetJavaScriptEnabled")
    fun configure(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            allowFileAccess = false
            allowContentAccess = false
        }
        webView.isVerticalScrollBarEnabled = true
        webView.isHorizontalScrollBarEnabled = false
    }

    fun loadUrl(webView: WebView, url: String) {
        webView.loadUrl(url)
    }

    fun goBack(webView: WebView): Boolean {
        if (webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return false
    }

    fun reload(webView: WebView) {
        webView.reload()
    }

    fun destroy(webView: WebView) {
        webView.stopLoading()
        webView.loadUrl("about:blank")
        webView.removeAllViews()
        webView.destroy()
    }
}
