package com.yohuohuohuo.browser.ui

import android.content.Context
import android.graphics.RectF
import android.media.AudioManager
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.yohuohuohuo.browser.R
import com.yohuohuohuo.browser.gesture.VideoGestureDetector
import com.yohuohuohuo.browser.gesture.VideoJsBridge
import java.util.Locale

class VideoGestureOverlay @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private var videoBridge: VideoJsBridge? = null
    private var gestureDetector: VideoGestureDetector? = null
    private var seekAccumulator = 0f
    private var hasFetchedTime = false
    private var cachedCurrentTime = 0f
    private var cachedDuration = 0f
    private var feedbackView: View? = null
    private var feedbackText: TextView? = null
    private var gestureConsumed = false
    private var activeVideoBounds: RectF? = null
    private var touchInVideo = false
    private var volumeDeltaAccumulator = 0f
    private var feedbackRequestGeneration = 0L
    private var fullscreenTouchMode = false
    private var feedbackHideRunnable: Runnable? = null

    var onSingleTapConfirmed: ((MotionEvent, MotionEvent) -> Unit)? = null

    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    init {
        // 这是反馈层，不是 WebView 的事件目标。事件由 WebView 的 touch
        // listener 统一路由，避免全屏 sibling 截断网页点击事件。
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun attach(bridge: VideoJsBridge) {
        gestureDetector?.dispose()
        this.videoBridge = bridge
        activeVideoBounds = null
        touchInVideo = false
        gestureDetector = VideoGestureDetector(context, object : VideoGestureDetector.GestureListener {
            override fun onLongPressStart() {
                bridge.setRate(3.0f)
                showFeedback("3x")
            }

            override fun onLongPressEnd() {
                bridge.setRate(1.0f)
                hideFeedback()
            }

            override fun onDoubleTap() {
                bridge.togglePlay()
            }

            override fun onSingleTapConfirmed(down: MotionEvent, up: MotionEvent) {
                onSingleTapConfirmed?.invoke(down, up)
            }

            override fun onHorizontalScroll(deltaX: Float) {
                // ~1px = 0.5s
                val delta = deltaX * 0.5f
                seekAccumulator += delta
                videoBridge?.seekBy(delta)
                updateSeekFeedback()
            }

            override fun onLeftVerticalScroll(deltaY: Float) {
                adjustBrightness(deltaY)
            }

            override fun onRightVerticalScroll(deltaY: Float) {
                adjustVolume(deltaY)
            }

            override fun onScrollEnd() {
                seekAccumulator = 0f
                hasFetchedTime = false
                cachedCurrentTime = 0f
                cachedDuration = 0f
                volumeDeltaAccumulator = 0f
                feedbackRequestGeneration++
                hideFeedback()
            }
        })
    }

    fun setActiveVideoBounds(bounds: RectF?, contentScale: Float = 1f) {
        val scale = contentScale.takeIf { it > 0f && it.isFinite() } ?: 1f
        activeVideoBounds = bounds?.let {
            RectF(
                it.left * scale,
                it.top * scale,
                it.right * scale,
                it.bottom * scale
            )
        }
    }

    /**
     * 由 WebView 的 touch 路由调用。活动视频期间先暂存点击，等待双击窗口结束；
     * 滑动、长按和双击由 detector 直接消费，普通单击确认后再回放给目标 View。
     */
    fun handleWebViewTouch(event: MotionEvent): Boolean {
        return handleGestureTouch(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!fullscreenTouchMode) return super.onTouchEvent(event)
        return handleGestureTouch(event)
    }

    private fun handleGestureTouch(event: MotionEvent): Boolean {
        val detector = gestureDetector ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!fullscreenTouchMode && !isPointInsideActiveVideo(event.x, event.y)) {
                    touchInVideo = false
                    gestureConsumed = false
                    return false
                }
                touchInVideo = true
                gestureConsumed = false
                volumeDeltaAccumulator = 0f
                detector.onTouchEvent(event)
                // Hold every active-video tap until the double-tap window closes.
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!fullscreenTouchMode && !touchInVideo) return false
                val shouldConsume = gestureConsumed || detector.shouldInterceptMove(event)
                detector.onTouchEvent(event)
                if (shouldConsume) gestureConsumed = true
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!fullscreenTouchMode && !touchInVideo) return false
                detector.onTouchEvent(event)
                touchInVideo = false
                gestureConsumed = false
                return true
            }
        }
        return false
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun isGestureEnabled(): Boolean = visibility == View.VISIBLE &&
        gestureDetector != null && (fullscreenTouchMode || activeVideoBounds != null)

    private fun isPointInsideActiveVideo(x: Float, y: Float): Boolean =
        activeVideoBounds?.contains(x, y) == true

    fun detach() {
        feedbackRequestGeneration++
        gestureDetector?.dispose()
        gestureDetector = null
        videoBridge = null
        gestureConsumed = false
        activeVideoBounds = null
        touchInVideo = false
        volumeDeltaAccumulator = 0f
        hideFeedback()
        feedbackView?.let { removeView(it) }
        feedbackView = null
        feedbackText = null
    }

    fun resetGesture() {
        gestureDetector?.cancel()
        gestureConsumed = false
        touchInVideo = false
        volumeDeltaAccumulator = 0f
        hideFeedback()
    }

    fun enterFullscreenTouchMode() {
        fullscreenTouchMode = true
        resetGesture()
    }

    fun exitFullscreenTouchMode() {
        fullscreenTouchMode = false
        resetGesture()
    }

    private fun showFeedback(text: String) {
        if (feedbackView == null) {
            feedbackView = LayoutInflater.from(context)
                .inflate(R.layout.view_gesture_feedback, this, false)
            feedbackText = feedbackView?.findViewById(R.id.feedbackText)
            addView(feedbackView)
        }
        feedbackText?.text = text
        feedbackView?.visibility = View.VISIBLE
        feedbackHideRunnable?.let { removeCallbacks(it) }
        feedbackHideRunnable = Runnable {
            feedbackView?.visibility = View.GONE
            feedbackHideRunnable = null
        }
        postDelayed(feedbackHideRunnable!!, 1_500L)
    }

    /**
     * 优化：仅在滑动开始时获取一次 currentTime / duration，
     * 后续反馈用本地累加值计算，避免每次 scroll 都触发两次异步 JS 调用。
     */
    private fun updateSeekFeedback() {
        if (!hasFetchedTime) {
            hasFetchedTime = true
            val requestGeneration = feedbackRequestGeneration
            val bridge = videoBridge ?: return
            bridge.getCurrentTime { current ->
                if (requestGeneration != feedbackRequestGeneration || gestureDetector == null) return@getCurrentTime
                cachedCurrentTime = current
                bridge.getDuration { duration ->
                    if (requestGeneration != feedbackRequestGeneration || gestureDetector == null) {
                        return@getDuration
                    }
                    cachedDuration = duration
                    renderSeekFeedback()
                }
            }
        } else {
            renderSeekFeedback()
        }
    }

    private fun renderSeekFeedback() {
        val sign = if (seekAccumulator >= 0) "+" else "-"
        val estimatedCurrent = if (cachedDuration > 0f) {
            (cachedCurrentTime + seekAccumulator).coerceIn(0f, cachedDuration)
        } else {
            (cachedCurrentTime + seekAccumulator).coerceAtLeast(0f)
        }
        val time = formatTime(estimatedCurrent)
        val total = formatTime(cachedDuration)
        showFeedback("$sign${formatTime(kotlin.math.abs(seekAccumulator))}  $time / $total")
    }

    private fun hideFeedback() {
        feedbackHideRunnable?.let { removeCallbacks(it) }
        feedbackHideRunnable = null
        feedbackView?.visibility = View.GONE
    }

    private fun adjustBrightness(deltaY: Float) {
        val window = (context as? android.app.Activity)?.window ?: return
        val layoutParams = window.attributes
        val current = if (layoutParams.screenBrightness == WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) {
            0.5f
        } else layoutParams.screenBrightness
        val newBrightness = (current + deltaY / 1000f).coerceIn(0f, 1f)
        layoutParams.screenBrightness = newBrightness
        window.attributes = layoutParams
        showFeedback("☀ ${(newBrightness * 100).toInt()}%")
    }

    private fun adjustVolume(deltaY: Float) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return
        // MotionEvent 的单次 delta 通常小于 1 个音量档位，必须累积小数
        // 距离后再取整，否则连续慢滑会永远得到 0。
        volumeDeltaAccumulator += deltaY / 50f
        val delta = volumeDeltaAccumulator.toInt()
        volumeDeltaAccumulator -= delta
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val newVolume = (current + delta).coerceIn(0, max)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
        showFeedback("🔊 ${(newVolume * 100 / max)}%")
    }

    private fun formatTime(seconds: Float): String {
        val totalSeconds = seconds.coerceAtLeast(0f).toInt()
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        return String.format(Locale.ROOT, "%02d:%02d", m, s)
    }
}
