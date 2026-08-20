package com.yohuohuohuo.browser.gesture

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import kotlin.math.abs

class VideoGestureDetector(
    context: Context,
    private val listener: GestureListener
) {
    interface GestureListener {
        fun onLongPressStart()
        fun onLongPressEnd()
        fun onDoubleTap()
        fun onHorizontalScroll(deltaX: Float)
        fun onLeftVerticalScroll(deltaY: Float)
        fun onRightVerticalScroll(deltaY: Float)
        fun onScrollEnd()
    }

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onLongPress(e: MotionEvent) {
            isLongPressing = true
            listener.onLongPressStart()
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            listener.onDoubleTap()
            return true
        }

        override fun onScroll(
            e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float
        ): Boolean {
            val width = context.resources.displayMetrics.widthPixels.toFloat()
            val isHorizontal = abs(distanceX) > abs(distanceY)
            if (isHorizontal) {
                listener.onHorizontalScroll(-distanceX)
            } else {
                val startX = e1?.x ?: 0f
                if (startX < width / 2) {
                    listener.onLeftVerticalScroll(distanceY)
                } else {
                    listener.onRightVerticalScroll(distanceY)
                }
            }
            return true
        }
    })

    private var isLongPressing = false

    fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        when (event.action) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isLongPressing) {
                    isLongPressing = false
                    listener.onLongPressEnd()
                }
                listener.onScrollEnd()
            }
        }
        return true
    }

    fun setLongPressing(value: Boolean) { isLongPressing = value }
}
