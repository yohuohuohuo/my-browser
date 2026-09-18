package com.yohuohuohuo.browser.ui

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout

/** Routes the original fullscreen touch to WebView's custom view until a gesture takes over. */
class FullscreenVideoTouchHost(context: Context, private val videoView: View) : FrameLayout(context) {
    var gestureHandler: ((MotionEvent) -> Boolean)? = null
    private var nativeTouchActive = false
    private var lastNativeTouch: MotionEvent? = null

    init {
        addView(videoView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (gestureHandler?.invoke(event) == true) {
            cancelActiveTouch()
            return true
        }

        val wasActive = nativeTouchActive
        lastNativeTouch?.recycle()
        lastNativeTouch = MotionEvent.obtain(event)
        val handled = videoView.dispatchTouchEvent(event)
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                nativeTouchActive = true
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                nativeTouchActive = false
                lastNativeTouch?.recycle()
                lastNativeTouch = null
                handled || wasActive
            }
            else -> handled || wasActive
        }
    }

    fun cancelActiveTouch() {
        if (!nativeTouchActive) return
        lastNativeTouch?.let { previous ->
            val cancel = MotionEvent.obtain(previous)
            cancel.action = MotionEvent.ACTION_CANCEL
            videoView.dispatchTouchEvent(cancel)
            cancel.recycle()
        }
        lastNativeTouch?.recycle()
        lastNativeTouch = null
        nativeTouchActive = false
    }
}
