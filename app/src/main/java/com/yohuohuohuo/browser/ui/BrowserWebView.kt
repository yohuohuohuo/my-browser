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
    var onTouchStateChanged: ((Boolean) -> Unit)? = null
    private var webViewTouchActive = false

    // 普通事件始终交给 WebView 处理；只有自定义 detector 已接管时才提前消费。
    // 即使 WebView 对单个事件返回 false，也要保持当前触摸序列，避免滑动被截断。
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (gestureHandler?.invoke(event) == true) {
            if (webViewTouchActive) {
                val cancel = MotionEvent.obtain(event)
                cancel.action = MotionEvent.ACTION_CANCEL
                dispatchToWebView(cancel)
                cancel.recycle()
                updateTouchState(false)
            }
            return true
        }

        val wasTouchActive = webViewTouchActive
        val handled = dispatchToWebView(event)
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                updateTouchState(true)
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                updateTouchState(false)
                handled || wasTouchActive
            }
            else -> handled || wasTouchActive
        }
    }

    private fun updateTouchState(active: Boolean) {
        if (webViewTouchActive == active) return
        webViewTouchActive = active
        onTouchStateChanged?.invoke(active)
    }

    protected open fun dispatchToWebView(event: MotionEvent): Boolean = super.onTouchEvent(event)

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
