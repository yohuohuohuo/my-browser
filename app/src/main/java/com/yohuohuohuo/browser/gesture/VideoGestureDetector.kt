package com.yohuohuohuo.browser.gesture

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * 自定义手势识别器，解决系统 GestureDetector 在 onLongPress 触发后
 * 不再回调 onScroll 的问题，同时支持点击事件透传与滑动拦截的协作。
 */
class VideoGestureDetector(
    context: Context,
    private val listener: GestureListener,
    private val viewportWidth: () -> Int
) {
    interface GestureListener {
        fun onLongPressStart()
        fun onLongPressEnd()
        fun onDoubleTap()
        fun onSingleTapConfirmed() {}
        /** Events are valid only during this synchronous callback. */
        fun onSingleTapConfirmed(down: MotionEvent, up: MotionEvent) {
            onSingleTapConfirmed()
        }
        fun onHorizontalScroll(deltaX: Float)
        fun onLeftVerticalScroll(deltaY: Float)
        fun onRightVerticalScroll(deltaY: Float)
        fun onScrollEnd()
    }

    private enum class ScrollDirection { HORIZONTAL, VERTICAL_LEFT, VERTICAL_RIGHT }

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private val doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout().toLong()
    private val handler = Handler(Looper.getMainLooper())

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var isLongPressing = false
    private var isScrolling = false
    private var isDoubleTapInProgress = false
    private var scrollDirection: ScrollDirection? = null
    private var longPressRunnable: Runnable? = null
    private var singleTapRunnable: Runnable? = null
    private var currentDownEvent: MotionEvent? = null
    private var pendingTapDownEvent: MotionEvent? = null
    private var pendingTapUpEvent: MotionEvent? = null

    // 双击检测
    private var lastTapTime = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f
    private var hasPendingTap = false

    fun isLongPressing(): Boolean = isLongPressing
    fun isScrolling(): Boolean = isScrolling
    fun isGestureActive(): Boolean = isLongPressing || isScrolling || isDoubleTapInProgress

    /**
     * 由触摸路由在 MOVE 时调用，判断是否应该进入滑动模式。
     */
    fun shouldInterceptMove(event: MotionEvent): Boolean {
        if (isScrolling || isLongPressing) return true
        val totalDeltaX = event.x - downX
        val totalDeltaY = event.y - downY
        return abs(totalDeltaX) > touchSlop || abs(totalDeltaY) > touchSlop
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleDown(event)
            MotionEvent.ACTION_MOVE -> handleMove(event)
            MotionEvent.ACTION_UP -> handleUp(event)
            MotionEvent.ACTION_CANCEL -> handleCancel()
        }
        return true
    }

    private fun handleDown(event: MotionEvent) {
        currentDownEvent?.recycle()
        currentDownEvent = MotionEvent.obtain(event)
        downX = event.x
        downY = event.y
        lastX = event.x
        lastY = event.y
        isLongPressing = false
        isScrolling = false
        isDoubleTapInProgress = false
        scrollDirection = null

        // 双击检测
        val now = event.eventTime
        if (hasPendingTap &&
            now - lastTapTime < doubleTapTimeout &&
            abs(event.x - lastTapX) < touchSlop * 2 &&
            abs(event.y - lastTapY) < touchSlop * 2
        ) {
            cancelSingleTap()
            cancelLongPress()
            hasPendingTap = false
            clearPendingTapEvents()
            listener.onDoubleTap()
            isDoubleTapInProgress = true
        } else if (hasPendingTap) {
            cancelSingleTap()
            hasPendingTap = false
            confirmSingleTap()
        } else {
            hasPendingTap = false
        }

        // 长按计时
        if (!isDoubleTapInProgress) {
            longPressRunnable = Runnable {
                if (!isScrolling) {
                    isLongPressing = true
                    listener.onLongPressStart()
                }
            }
            handler.postDelayed(longPressRunnable!!, longPressTimeout)
        }
    }

    private fun handleMove(event: MotionEvent) {
        val deltaX = event.x - lastX
        // Android video gestures conventionally treat an upward drag as positive.
        val deltaY = lastY - event.y
        val totalDeltaX = event.x - downX
        val totalDeltaY = event.y - downY

        if (!isScrolling && !isLongPressing) {
            if (abs(totalDeltaX) > touchSlop || abs(totalDeltaY) > touchSlop) {
                // 移动超过阈值，取消长按，进入滑动模式
                cancelLongPress()
                isScrolling = true
                scrollDirection = if (abs(totalDeltaX) > abs(totalDeltaY)) {
                    ScrollDirection.HORIZONTAL
                } else {
                    if (downX < viewportWidth() / 2f) ScrollDirection.VERTICAL_LEFT
                    else ScrollDirection.VERTICAL_RIGHT
                }
            }
        }

        val direction = scrollDirection
        if (isScrolling && direction != null) {
            when (direction) {
                ScrollDirection.HORIZONTAL -> listener.onHorizontalScroll(deltaX)
                ScrollDirection.VERTICAL_LEFT -> listener.onLeftVerticalScroll(deltaY)
                ScrollDirection.VERTICAL_RIGHT -> listener.onRightVerticalScroll(deltaY)
            }
        }

        lastX = event.x
        lastY = event.y
    }

    private fun handleUp(event: MotionEvent) {
        val wasLongPressing = isLongPressing
        val wasScrolling = isScrolling
        val wasDoubleTap = isDoubleTapInProgress
        cancelLongPress()
        if (wasLongPressing) {
            isLongPressing = false
            listener.onLongPressEnd()
        }
        if (wasScrolling) {
            isScrolling = false
            scrollDirection = null
            listener.onScrollEnd()
        }
        // 记录点击（未滑动且未长按）用于双击检测
        if (!wasScrolling && !wasLongPressing && !wasDoubleTap) {
            lastTapTime = event.eventTime
            lastTapX = event.x
            lastTapY = event.y
            hasPendingTap = true
            pendingTapDownEvent?.recycle()
            pendingTapDownEvent = currentDownEvent
            currentDownEvent = null
            pendingTapUpEvent?.recycle()
            pendingTapUpEvent = MotionEvent.obtain(event)
            singleTapRunnable = Runnable {
                if (hasPendingTap) {
                    hasPendingTap = false
                    singleTapRunnable = null
                    confirmSingleTap()
                }
            }
            handler.postDelayed(singleTapRunnable!!, doubleTapTimeout)
        } else {
            currentDownEvent?.recycle()
            currentDownEvent = null
        }
        isDoubleTapInProgress = false
    }

    private fun handleCancel() {
        val wasLongPressing = isLongPressing
        val wasScrolling = isScrolling
        cancelLongPress()
        if (wasLongPressing) {
            isLongPressing = false
            listener.onLongPressEnd()
        }
        if (wasScrolling) {
            isScrolling = false
            scrollDirection = null
            listener.onScrollEnd()
        }
        hasPendingTap = false
        cancelSingleTap()
        clearPendingTapEvents()
        currentDownEvent?.recycle()
        currentDownEvent = null
        isDoubleTapInProgress = false
    }

    private fun cancelLongPress() {
        longPressRunnable?.let { handler.removeCallbacks(it) }
        longPressRunnable = null
    }

    private fun cancelSingleTap() {
        singleTapRunnable?.let { handler.removeCallbacks(it) }
        singleTapRunnable = null
    }

    private fun confirmSingleTap() {
        val down = pendingTapDownEvent
        val up = pendingTapUpEvent
        pendingTapDownEvent = null
        pendingTapUpEvent = null
        if (down != null && up != null) {
            listener.onSingleTapConfirmed(down, up)
        }
        down?.recycle()
        up?.recycle()
    }

    private fun clearPendingTapEvents() {
        pendingTapDownEvent?.recycle()
        pendingTapUpEvent?.recycle()
        pendingTapDownEvent = null
        pendingTapUpEvent = null
    }

    fun cancel() {
        handleCancel()
    }

    /** Fragment/View 销毁时调用，避免主线程 Handler 持有 listener。 */
    fun dispose() {
        cancelLongPress()
        cancelSingleTap()
        handler.removeCallbacksAndMessages(null)
        hasPendingTap = false
        clearPendingTapEvents()
        currentDownEvent?.recycle()
        currentDownEvent = null
        isLongPressing = false
        isScrolling = false
        isDoubleTapInProgress = false
        scrollDirection = null
    }
}
