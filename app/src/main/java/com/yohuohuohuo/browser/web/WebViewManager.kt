package com.yohuohuohuo.browser.web

import android.annotation.SuppressLint
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.yohuohuohuo.browser.ui.BrowserWebView

class WebViewManager {

    @SuppressLint("SetJavaScriptEnabled")
    fun configure(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
            // 保持 WebView 默认的用户手势播放边界，避免页面静默自动播放。
            mediaPlaybackRequiresUserGesture = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
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
        (webView as? BrowserWebView)?.gestureHandler = null
        webView.removeJavascriptInterface("AndroidBridge")
        webView.webChromeClient = null
        // WebView.webViewClient 是非空属性；替换为空 client 后再 destroy，
        // 避免旧 Fragment callback 在清理阶段收到 about:blank 事件。
        webView.webViewClient = WebViewClient()
        webView.removeAllViews()
        webView.destroy()
    }
}
