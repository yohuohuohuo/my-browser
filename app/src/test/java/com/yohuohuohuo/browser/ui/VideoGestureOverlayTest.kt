package com.yohuohuohuo.browser.ui

import android.content.Context
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.TextView
import android.webkit.ValueCallback
import android.webkit.WebView
import com.yohuohuohuo.browser.R
import com.yohuohuohuo.browser.gesture.VideoJsBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoGestureOverlayTest {

    @Test
    fun `vertical sides follow actual touch container width after window resizing`() {
        val controller = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup()
        val activity = controller.get()
        val metrics = activity.resources.displayMetrics
        val originalWidth = metrics.widthPixels
        metrics.widthPixels = 320
        val bridge = VideoJsBridge(activity)
        val overlay = VideoGestureOverlay(activity)
        overlay.attach(bridge)
        overlay.enterFullscreenTouchMode()
        activity.window.attributes = activity.window.attributes.apply { screenBrightness = 0.5f }
        try {
            overlay.layout(0, 0, 320, 600)
            overlay.layout(0, 0, 600, 600)
            overlay.handleWebViewTouch(event(MotionEvent.ACTION_DOWN, 200f, 300f, 0))
            overlay.handleWebViewTouch(event(MotionEvent.ACTION_MOVE, 200f, 100f, 20))
            overlay.handleWebViewTouch(event(MotionEvent.ACTION_UP, 200f, 100f, 40))
            assertEquals(0.7f, activity.window.attributes.screenBrightness, 0.001f)

            // Same local x moves to the right half when the window narrows again.
            overlay.layout(0, 0, 300, 600)
            overlay.handleWebViewTouch(event(MotionEvent.ACTION_DOWN, 200f, 300f, 500))
            overlay.handleWebViewTouch(event(MotionEvent.ACTION_MOVE, 200f, 100f, 520))
            overlay.handleWebViewTouch(event(MotionEvent.ACTION_UP, 200f, 100f, 540))
            assertEquals(0.7f, activity.window.attributes.screenBrightness, 0.001f)
        } finally {
            metrics.widthPixels = originalWidth
            overlay.detach()
            bridge.close()
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `single tap keeps the original touch sequence for the page`() {
        val context: Context = RuntimeEnvironment.getApplication()
        val webView = RecordingWebView(context)
        val bridge = VideoJsBridge(context)
        bridge.attach(webView)
        val overlay = VideoGestureOverlay(context)
        overlay.attach(bridge)
        overlay.enterFullscreenTouchMode()

        assertEquals(false, overlay.handleWebViewTouch(event(MotionEvent.ACTION_DOWN, 10f, 10f)))
        assertEquals(false, overlay.handleWebViewTouch(event(MotionEvent.ACTION_UP, 10f, 10f)))

        assertEquals(0, overlay.childCount)
        shadowOf(android.os.Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS)
        assertEquals(0, overlay.childCount)
        assertTrue(webView.scripts.none { it.contains("togglePlay") })
        overlay.detach()
        bridge.close()
    }

    @Test
    fun `late second touch starts another native page gesture`() {
        val context: Context = RuntimeEnvironment.getApplication()
        val overlay = VideoGestureOverlay(context)
        overlay.attach(VideoJsBridge(context))
        overlay.enterFullscreenTouchMode()

        assertEquals(false, overlay.handleWebViewTouch(event(MotionEvent.ACTION_DOWN, 10f, 10f, 0)))
        assertEquals(false, overlay.handleWebViewTouch(event(MotionEvent.ACTION_UP, 10f, 10f, 10)))
        assertEquals(false, overlay.handleWebViewTouch(event(MotionEvent.ACTION_DOWN, 30f, 30f, 400)))

        assertEquals(0, overlay.childCount)
        overlay.detach()
    }

    @Test
    fun `double tap passes first tap and consumes the second tap`() {
        val context: Context = RuntimeEnvironment.getApplication()
        val webView = RecordingWebView(context)
        val bridge = VideoJsBridge(context)
        bridge.attach(webView)
        val overlay = VideoGestureOverlay(context)
        overlay.attach(bridge)
        overlay.enterFullscreenTouchMode()

        assertEquals(false, overlay.handleWebViewTouch(event(MotionEvent.ACTION_DOWN, 10f, 10f, 0)))
        assertEquals(false, overlay.handleWebViewTouch(event(MotionEvent.ACTION_UP, 10f, 10f, 10)))
        assertEquals(true, overlay.handleWebViewTouch(event(MotionEvent.ACTION_DOWN, 10f, 10f, 100)))
        assertEquals(true, overlay.handleWebViewTouch(event(MotionEvent.ACTION_UP, 10f, 10f, 110)))
        shadowOf(android.os.Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS)

        assertEquals(1, webView.scripts.count { it.contains("togglePlay") })
        assertEquals(0, overlay.childCount)
        overlay.detach()
        bridge.close()
    }

    @Test
    fun `horizontal swipe takes over after the native down event`() {
        val context: Context = RuntimeEnvironment.getApplication()
        val webView = RecordingWebView(context)
        val bridge = VideoJsBridge(context)
        bridge.attach(webView)
        val overlay = VideoGestureOverlay(context)
        overlay.attach(bridge)
        overlay.enterFullscreenTouchMode()
        assertEquals(false, overlay.handleWebViewTouch(event(MotionEvent.ACTION_DOWN, 10f, 10f)))
        assertEquals(true, overlay.handleWebViewTouch(event(MotionEvent.ACTION_MOVE, 80f, 10f)))
        assertEquals(true, overlay.handleWebViewTouch(event(MotionEvent.ACTION_UP, 80f, 10f)))
        assertEquals(listOf(35f), webView.seekDeltas())
        overlay.resetGesture()
        val feedback = overlay.findViewById<View>(R.id.feedbackText).parent as View
        assertEquals(View.GONE, feedback.visibility)
        overlay.detach()
        bridge.close()
    }

    @Test
    fun `long press hint uses a restrained text treatment`() {
        val context: Context = RuntimeEnvironment.getApplication()
        val overlay = VideoGestureOverlay(context)
        overlay.attach(VideoJsBridge(context))
        overlay.enterFullscreenTouchMode()

        overlay.handleWebViewTouch(event(MotionEvent.ACTION_DOWN, 100f, 100f))
        shadowOf(android.os.Looper.getMainLooper()).idleFor(
            ViewConfiguration.getLongPressTimeout().toLong() + 50L,
            TimeUnit.MILLISECONDS
        )

        val feedbackText = overlay.findViewById<TextView>(R.id.feedbackText)
        @Suppress("DEPRECATION")
        val textSizeSp = feedbackText.textSize / context.resources.displayMetrics.scaledDensity
        assertTrue(textSizeSp <= 14f)
        assertFalse(feedbackText.typeface.isBold)
        overlay.detach()
    }

    @Test
    fun `one horizontal swipe caps forward seek at 180 seconds`() {
        val context: Context = RuntimeEnvironment.getApplication()
        val webView = RecordingWebView(context)
        val bridge = VideoJsBridge(context)
        bridge.attach(webView)
        val overlay = VideoGestureOverlay(context)
        overlay.attach(bridge)
        overlay.enterFullscreenTouchMode()

        overlay.onTouchEvent(event(MotionEvent.ACTION_DOWN, 100f, 100f, 0))
        overlay.onTouchEvent(event(MotionEvent.ACTION_MOVE, 300f, 100f, 10))
        overlay.onTouchEvent(event(MotionEvent.ACTION_MOVE, 500f, 100f, 20))
        overlay.onTouchEvent(event(MotionEvent.ACTION_MOVE, 600f, 100f, 30))
        overlay.onTouchEvent(event(MotionEvent.ACTION_MOVE, 550f, 100f, 40))
        overlay.onTouchEvent(event(MotionEvent.ACTION_MOVE, 450f, 100f, 50))

        assertEquals(listOf(100f, 80f, -5f), webView.seekDeltas())
        overlay.detach()
        bridge.close()
    }

    @Test
    fun `a new swipe gets a new 180 second forward budget`() {
        val context: Context = RuntimeEnvironment.getApplication()
        val webView = RecordingWebView(context)
        val bridge = VideoJsBridge(context)
        bridge.attach(webView)
        val overlay = VideoGestureOverlay(context)
        overlay.attach(bridge)
        overlay.enterFullscreenTouchMode()

        overlay.onTouchEvent(event(MotionEvent.ACTION_DOWN, 100f, 100f, 0))
        overlay.onTouchEvent(event(MotionEvent.ACTION_MOVE, 500f, 100f, 10))
        overlay.onTouchEvent(event(MotionEvent.ACTION_UP, 500f, 100f, 20))
        overlay.onTouchEvent(event(MotionEvent.ACTION_DOWN, 100f, 100f, 100))
        overlay.onTouchEvent(event(MotionEvent.ACTION_MOVE, 500f, 100f, 110))

        assertEquals(listOf(180f, 180f), webView.seekDeltas())
        overlay.detach()
        bridge.close()
    }

    @Test
    fun `page touches outside inline video bounds are not intercepted`() {
        val context: Context = RuntimeEnvironment.getApplication()
        val parent = FrameLayout(context)
        var pageTouches = 0
        val page = View(context).apply {
            isClickable = true
            setOnTouchListener { _, _ ->
                pageTouches++
                true
            }
        }
        val overlay = VideoGestureOverlay(context)
        parent.addView(page, FrameLayout.LayoutParams(320, 600))
        parent.addView(overlay, FrameLayout.LayoutParams(320, 600))
        parent.measure(
            View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY)
        )
        parent.layout(0, 0, 320, 600)
        overlay.attach(VideoJsBridge(context))
        overlay.visibility = View.VISIBLE
        overlay.setActiveVideoBounds(RectF(20f, 20f, 300f, 220f))
        overlay.handleWebViewTouch(event(MotionEvent.ACTION_DOWN, 100f, 100f))
        overlay.handleWebViewTouch(event(MotionEvent.ACTION_UP, 100f, 100f))
        shadowOf(android.os.Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS)
        parent.measure(
            View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY)
        )
        parent.layout(0, 0, 320, 600)
        assertEquals(0, overlay.childCount)
        parent.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 50f, 580f))
        parent.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 50f, 580f))
        assertEquals(2, pageTouches)
        overlay.setActiveVideoBounds(null)
        overlay.detach()
    }

    @Test
    fun `touch outside active video is passed through while touch inside video is owned`() {
        val context: Context = RuntimeEnvironment.getApplication()
        val overlay = VideoGestureOverlay(context)
        overlay.attach(VideoJsBridge(context))
        overlay.visibility = View.VISIBLE
        overlay.setActiveVideoBounds(RectF(100f, 100f, 300f, 300f))

        assertEquals(
            false,
            overlay.handleWebViewTouch(event(MotionEvent.ACTION_DOWN, 20f, 20f))
        )

        assertEquals(
            false,
            overlay.handleWebViewTouch(event(MotionEvent.ACTION_DOWN, 150f, 150f))
        )
        assertEquals(
            true,
            overlay.handleWebViewTouch(event(MotionEvent.ACTION_MOVE, 20f, 20f))
        )
        assertEquals(
            true,
            overlay.handleWebViewTouch(event(MotionEvent.ACTION_UP, 20f, 20f))
        )
        overlay.detach()
    }

    @Test
    fun `drag near the bottom of a video stays with the page controls`() {
        val context: Context = RuntimeEnvironment.getApplication()
        val webView = RecordingWebView(context)
        val bridge = VideoJsBridge(context)
        bridge.attach(webView)
        val overlay = VideoGestureOverlay(context)
        overlay.attach(bridge)
        overlay.visibility = View.VISIBLE
        overlay.setActiveVideoBounds(RectF(0f, 0f, 320f, 240f))

        assertEquals(false, overlay.handleWebViewTouch(event(MotionEvent.ACTION_DOWN, 100f, 225f)))
        assertEquals(false, overlay.handleWebViewTouch(event(MotionEvent.ACTION_MOVE, 200f, 225f)))
        assertEquals(false, overlay.handleWebViewTouch(event(MotionEvent.ACTION_UP, 200f, 225f)))
        assertTrue(webView.seekDeltas().isEmpty())
        overlay.detach()
        bridge.close()
    }

    @Test
    fun `fullscreen bottom control drag stays with the custom view`() {
        val context: Context = RuntimeEnvironment.getApplication()
        val overlay = VideoGestureOverlay(context)
        overlay.attach(VideoJsBridge(context))
        overlay.enterFullscreenTouchMode()
        overlay.layout(0, 0, 320, 600)

        assertEquals(false, overlay.handleWebViewTouch(event(MotionEvent.ACTION_DOWN, 100f, 585f)))
        assertEquals(false, overlay.handleWebViewTouch(event(MotionEvent.ACTION_MOVE, 200f, 585f)))
        assertEquals(false, overlay.handleWebViewTouch(event(MotionEvent.ACTION_UP, 200f, 585f)))
        overlay.detach()
    }

    private fun event(action: Int, x: Float, y: Float, time: Long = 0): MotionEvent =
        MotionEvent.obtain(time, time, action, x, y, 0)

    private class RecordingWebView(context: Context) : WebView(context) {
        val scripts = mutableListOf<String>()

        fun seekDeltas(): List<Float> = scripts
            .filter { it.startsWith("window.__videoBridge.seekBy(") }
            .map { it.substringAfter('(').substringBefore(')').toFloat() }

        override fun evaluateJavascript(script: String, resultCallback: ValueCallback<String>?) {
            scripts += script
            resultCallback?.onReceiveValue("0")
        }
    }
}
