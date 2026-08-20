package com.yohuohuohuo.browser.ui

import android.content.Context
import android.media.AudioManager
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.yohuohuohuo.browser.gesture.VideoGestureDetector
import com.yohuohuohuo.browser.gesture.VideoJsBridge

class VideoGestureOverlay @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private var videoBridge: VideoJsBridge? = null
    private lateinit var gestureDetector: VideoGestureDetector
    private var seekAccumulator = 0f
    private var feedbackView: TextView? = null
    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    fun attach(bridge: VideoJsBridge) {
        this.videoBridge = bridge
        gestureDetector = VideoGestureDetector(context, object : VideoGestureDetector.GestureListener {
            override fun onLongPressStart() {
                videoBridge?.setRate(3.0f)
                showFeedback("3x")
            }

            override fun onLongPressEnd() {
                videoBridge?.setRate(1.0f)
                hideFeedback()
            }

            override fun onDoubleTap() {
                videoBridge?.togglePlay()
            }

            override fun onHorizontalScroll(deltaX: Float) {
                // ~1px = 0.5s
                seekAccumulator += deltaX * 0.5f
                videoBridge?.seekBy(deltaX * 0.5f)
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
                hideFeedback()
            }
        })
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return if (::gestureDetector.isInitialized) {
            gestureDetector.onTouchEvent(event)
        } else false
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // Intercept all touches when overlay is visible
        return true
    }

    private fun showFeedback(text: String) {
        if (feedbackView == null) {
            feedbackView = TextView(context).apply {
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 20f
                setPadding(32, 16, 32, 16)
                setBackgroundColor(0xCC000000.toInt())
            }
            val params = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER)
            addView(feedbackView, params)
        }
        feedbackView?.text = text
        feedbackView?.visibility = View.VISIBLE
    }

    private fun updateSeekFeedback() {
        videoBridge?.getCurrentTime { current ->
            videoBridge?.getDuration { duration ->
                val sign = if (seekAccumulator >= 0) "+" else ""
                val time = formatTime(current)
                val total = formatTime(duration)
                showFeedback("$sign${formatTime(seekAccumulator)}  $time / $total")
            }
        }
    }

    private fun hideFeedback() {
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
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val delta = (deltaY / 50f).toInt()
        val newVolume = (current + delta).coerceIn(0, max)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
        showFeedback("🔊 ${(newVolume * 100 / max)}%")
    }

    private fun formatTime(seconds: Float): String {
        val totalSeconds = seconds.toInt()
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        return String.format("%02d:%02d", m, s)
    }
}
