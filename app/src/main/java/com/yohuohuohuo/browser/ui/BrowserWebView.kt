package com.yohuohuohuo.browser.ui

import android.content.Context
import android.annotation.SuppressLint
import android.util.AttributeSet
import android.view.MotionEvent
import android.webkit.WebView

/** WebView 自身仍负责普通点击；自定义手势只在 touch listener 中接管滑动。 */
open class BrowserWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    var gestureHandler: ((MotionEvent) -> Boolean)? = null
    private var webViewTouchActive = false

    // 普通 ACTION_UP 仍交给 WebView.super.onTouchEvent 处理点击；只有自定义
    // detector 已进入手势态时才提前消费，因此这里不会绕过 WebView 的 click。
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (gestureHandler?.invoke(event) == true) {
            if (webViewTouchActive) {
                val cancel = MotionEvent.obtain(event)
                cancel.action = MotionEvent.ACTION_CANCEL
                dispatchToWebView(cancel)
                cancel.recycle()
                webViewTouchActive = false
            }
            return true
        }
        val handled = dispatchToWebView(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> webViewTouchActive = handled
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> webViewTouchActive = false
        }
        return handled
    }

    protected open fun dispatchToWebView(event: MotionEvent): Boolean = super.onTouchEvent(event)

    fun dispatchNativeTap(down: MotionEvent, up: MotionEvent) {
        dispatchToWebView(down)
        dispatchToWebView(up)
        webViewTouchActive = false
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
