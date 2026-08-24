package com.yohuohuohuo.browser.web

import android.graphics.Bitmap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.RenderProcessGoneDetail

class BrowserWebViewClient(
    private val onPageStartedCallback: (String) -> Unit,
    private val onPageFinishedCallback: (WebView, String) -> Unit,
    private val onReceivedErrorCallback: (String, String) -> Unit,
    private val onSslErrorCallback: (String) -> Unit = {},
    private val onRenderProcessGoneCallback: (Boolean) -> Unit = {}
) : WebViewClient() {

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        url?.let { onPageStartedCallback(it) }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (view != null && url != null) {
            onPageFinishedCallback(view, url)
        }
    }

    override fun onReceivedError(
        view: WebView?, request: WebResourceRequest?, error: WebResourceError?
    ) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame == true) {
            val msg = error?.description?.toString() ?: "Unknown error"
            val url = request.url?.toString() ?: ""
            onReceivedErrorCallback(msg, url)
        }
    }

    override fun onReceivedHttpError(
        view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?
    ) {
        super.onReceivedHttpError(view, request, errorResponse)
        if (request?.isForMainFrame == true) {
            val code = errorResponse?.statusCode ?: 0
            if (code >= 400) {
                val url = request.url?.toString() ?: ""
                onReceivedErrorCallback("HTTP $code error", url)
            }
        }
    }

    override fun onReceivedSslError(
        view: WebView?, handler: SslErrorHandler?, error: SslError?
    ) {
        // 不接受证书错误，也不把“继续访问”交给网页或默认 UI；浏览器只报告错误。
        handler?.cancel()
        val errorUrl = error?.url.orEmpty()
        if (errorUrl.isNotBlank() && errorUrl == view?.url) {
            onSslErrorCallback(errorUrl)
        }
    }

    override fun onRenderProcessGone(
        view: WebView?, detail: RenderProcessGoneDetail?
    ): Boolean {
        onRenderProcessGoneCallback(detail?.didCrash() == true)
        // 已失效的 renderer 不再交回 WebView 默认实现，避免应用进程被带崩。
        return true
    }
}
