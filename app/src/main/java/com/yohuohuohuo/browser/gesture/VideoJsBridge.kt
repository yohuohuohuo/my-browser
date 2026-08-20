package com.yohuohuohuo.browser.gesture

import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VideoJsBridge(private val context: Context) {

    private var webView: WebView? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    var onVideoActiveChanged: ((Boolean) -> Unit)? = null

    fun attach(webView: WebView) {
        this.webView = webView
        webView.addJavascriptInterface(this, "AndroidBridge")
    }

    fun detach() {
        webView?.removeJavascriptInterface("AndroidBridge")
        webView = null
    }

    fun injectScript() {
        val script = context.assets.open("video_bridge.js").bufferedReader().use { it.readText() }
        webView?.evaluateJavascript(script, null)
        // Register callbacks after injection
        webView?.evaluateJavascript(
            "window.__videoBridge.setCallbacks(" +
                "function(){ AndroidBridge.onVideoActive(); }," +
                "function(){ AndroidBridge.onVideoInactive(); }" +
                ");",
            null
        )
    }

    fun play() {
        webView?.evaluateJavascript("window.__videoBridge.play();", null)
    }

    fun pause() {
        webView?.evaluateJavascript("window.__videoBridge.pause();", null)
    }

    fun togglePlay() {
        webView?.evaluateJavascript("window.__videoBridge.togglePlay();", null)
    }

    fun seekBy(seconds: Float) {
        webView?.evaluateJavascript("window.__videoBridge.seekBy($seconds);", null)
    }

    fun setRate(rate: Float) {
        webView?.evaluateJavascript("window.__videoBridge.setRate($rate);", null)
    }

    fun getCurrentTime(callback: (Float) -> Unit) {
        webView?.evaluateJavascript("window.__videoBridge.getCurrentTime();") { result ->
            callback(result?.toFloatOrNull() ?: 0f)
        }
    }

    fun getDuration(callback: (Float) -> Unit) {
        webView?.evaluateJavascript("window.__videoBridge.getDuration();") { result ->
            callback(result?.toFloatOrNull() ?: 0f)
        }
    }

    @JavascriptInterface
    fun onVideoActive() {
        scope.launch { onVideoActiveChanged?.invoke(true) }
    }

    @JavascriptInterface
    fun onVideoInactive() {
        scope.launch { onVideoActiveChanged?.invoke(false) }
    }
}
