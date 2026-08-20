package com.yohuohuohuo.browser.web

import android.graphics.Bitmap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

class BrowserWebViewClient(
    private val onPageStarted: (String) -> Unit,
    private val onPageFinished: (WebView, String) -> Unit,
    private val onReceivedError: (String, String) -> Unit
) : WebViewClient() {

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        url?.let { onPageStarted(it) }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (view != null && url != null) {
            onPageFinished(view, url)
        }
    }

    override fun onReceivedError(
        view: WebView?, request: WebResourceRequest?, error: WebResourceError?
    ) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame == true) {
            val msg = error?.description?.toString() ?: "Unknown error"
            val url = request.url?.toString() ?: ""
            onReceivedError(msg, url)
        }
    }

    override fun onReceivedHttpError(
        view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?
    ) {
        super.onReceivedHttpError(view, request, errorResponse)
        if (request?.isForMainFrame == true) {
            val code = errorResponse?.statusCode ?: 0
            val url = request.url?.toString() ?: ""
            onReceivedError("HTTP $code error", url)
        }
    }
}
