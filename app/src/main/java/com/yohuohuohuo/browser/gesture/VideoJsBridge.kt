package com.yohuohuohuo.browser.gesture

import android.content.Context
import android.graphics.RectF
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch

class VideoJsBridge(context: Context) {

    private val appContext = context.applicationContext
    private var webView: WebView? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    @Volatile
    private var attachGeneration = 0L
    @Volatile
    private var pageGeneration = 0L
    private var closed = false

    /** 缓存注入脚本，避免每次页面加载都读 assets */
    private val scriptCache: String by lazy {
        appContext.assets.open("video_bridge.js").bufferedReader().use { it.readText() }
    }

    var onVideoActiveChanged: ((Boolean) -> Unit)? = null
    var onVideoBoundsChanged: ((RectF?) -> Unit)? = null

    fun attach(webView: WebView) {
        if (closed) return
        // 防御重复 attach：如果已是同一个 WebView 则跳过
        if (this.webView === webView) return
        // 先清理旧的
        if (this.webView != null) {
            detach()
        }
        this.webView = webView
        attachGeneration++
        pageGeneration++
        webView.addJavascriptInterface(this, "AndroidBridge")
    }

    fun detach() {
        attachGeneration++
        webView?.removeJavascriptInterface("AndroidBridge")
        webView = null
        // 只取消子协程，不取消 SupervisorJob 本身，保持 scope 可复用
        scope.coroutineContext[Job]?.cancelChildren()
        onVideoActiveChanged = null
        onVideoBoundsChanged = null
    }

    /** Fragment 永久销毁时调用；与 view recreation 使用的 detach 不同。 */
    fun close() {
        if (closed) return
        detach()
        closed = true
        scope.cancel()
    }

    fun injectScript() {
        val wv = webView ?: return
        val generation = pageGeneration
        wv.evaluateJavascript(scriptCache, null)
        wv.evaluateJavascript(
            "window.__videoBridge.setCallbacks(" +
                "function(){ AndroidBridge.onVideoActive($generation); }," +
                "function(){ AndroidBridge.onVideoInactive($generation); }," +
                "function(left, top, width, height){ AndroidBridge.onVideoBounds($generation, left, top, width, height); }" +
                ");",
            null
        )
    }

    fun beginNavigation() {
        pageGeneration++
        scope.coroutineContext[Job]?.cancelChildren()
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
        val wv = webView ?: return
        val generation = attachGeneration
        wv.evaluateJavascript("window.__videoBridge.getCurrentTime();") { result ->
            if (this.webView === wv && generation == attachGeneration) {
                callback(result?.toFloatOrNull() ?: 0f)
            }
        }
    }

    fun getDuration(callback: (Float) -> Unit) {
        val wv = webView ?: return
        val generation = attachGeneration
        wv.evaluateJavascript("window.__videoBridge.getDuration();") { result ->
            if (this.webView === wv && generation == attachGeneration) {
                callback(result?.toFloatOrNull() ?: 0f)
            }
        }
    }

    fun getActiveVideoDimensions(callback: (Float, Float) -> Unit) {
        val wv = webView ?: return
        val attachGeneration = this.attachGeneration
        val pageGeneration = this.pageGeneration
        wv.evaluateJavascript("window.__videoBridge.getActiveVideoDimensions();") { result ->
            if (this.webView !== wv ||
                attachGeneration != this.attachGeneration ||
                pageGeneration != this.pageGeneration
            ) {
                return@evaluateJavascript
            }
            val value = result?.trim()?.removeSurrounding("\"") ?: return@evaluateJavascript
            val parts = value.split(',')
            if (parts.size != 2) return@evaluateJavascript
            val width = parts[0].toFloatOrNull() ?: return@evaluateJavascript
            val height = parts[1].toFloatOrNull() ?: return@evaluateJavascript
            if (width > 0f && height > 0f && width.isFinite() && height.isFinite()) {
                callback(width, height)
            }
        }
    }

    @JavascriptInterface
    fun onVideoActive(generation: Long) {
        if (generation != pageGeneration) return
        val attachGeneration = this.attachGeneration
        scope.launch {
            if (generation == pageGeneration && attachGeneration == this@VideoJsBridge.attachGeneration) {
                onVideoActiveChanged?.invoke(true)
            }
        }
    }

    @JavascriptInterface
    fun onVideoInactive(generation: Long) {
        if (generation != pageGeneration) return
        val attachGeneration = this.attachGeneration
        scope.launch {
            if (generation == pageGeneration && attachGeneration == this@VideoJsBridge.attachGeneration) {
                onVideoActiveChanged?.invoke(false)
                onVideoBoundsChanged?.invoke(null)
            }
        }
    }

    @JavascriptInterface
    fun onVideoBounds(
        generation: Long,
        left: Float,
        top: Float,
        width: Float,
        height: Float
    ) {
        if (generation != pageGeneration) return
        val bounds = if (
            left.isFinite() && top.isFinite() && width.isFinite() && height.isFinite() &&
            width > 0f && height > 0f &&
            (left + width).isFinite() && (top + height).isFinite()
        ) {
            RectF(left, top, left + width, top + height)
        } else {
            null
        }
        val attachGeneration = this.attachGeneration
        scope.launch {
            if (generation == pageGeneration && attachGeneration == this@VideoJsBridge.attachGeneration) {
                onVideoBoundsChanged?.invoke(bounds)
            }
        }
    }
}
