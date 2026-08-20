package com.yohuohuohuo.browser.web

import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView

class BrowserWebChromeClient(
    private val onProgressChanged: (Int) -> Unit,
    private val onShowCustomView: (View) -> Unit,
    private val onHideCustomView: () -> Unit
) : WebChromeClient() {

    private var customView: View? = null

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        onProgressChanged(newProgress)
    }

    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        super.onShowCustomView(view, callback)
        view?.let {
            customView = it
            onShowCustomView(it)
        }
    }

    override fun onHideCustomView() {
        super.onHideCustomView()
        customView = null
        onHideCustomView()
    }
}
