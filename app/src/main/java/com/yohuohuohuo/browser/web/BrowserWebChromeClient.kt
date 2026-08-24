package com.yohuohuohuo.browser.web

import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView

class BrowserWebChromeClient(
    private val onProgressChangedCallback: (Int) -> Unit,
    private val onShowCustomViewCallback: (View) -> Unit,
    private val onHideCustomViewCallback: () -> Unit
) : WebChromeClient() {

    private var customView: View? = null
    private var customViewCallback: CustomViewCallback? = null

    val hasCustomView: Boolean
        get() = customView != null

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        onProgressChangedCallback(newProgress)
    }

    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        super.onShowCustomView(view, callback)
        view?.let {
            if (customView != null) {
                hideCustomViewInternal()
            }
            customView = it
            customViewCallback = callback
            onShowCustomViewCallback(it)
        }
    }

    override fun onHideCustomView() {
        super.onHideCustomView()
        hideCustomViewInternal()
    }

    /** 返回键或 Fragment 销毁时调用，保证 callback 只通知一次。 */
    fun hideCustomViewFromHost() {
        hideCustomViewInternal()
    }

    private fun hideCustomViewInternal() {
        if (customView == null && customViewCallback == null) return
        // 通知 WebView 自定义视图已被移除，避免视频网站全屏状态卡住。
        val callback = customViewCallback
        customViewCallback = null
        customView = null
        callback?.onCustomViewHidden()
        onHideCustomViewCallback()
    }
}
