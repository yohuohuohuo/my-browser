package com.yohuohuohuo.browser.ui

import android.content.Context
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.webkit.ValueCallback
import android.webkit.WebView
import com.yohuohuohuo.browser.R
import com.yohuohuohuo.browser.gesture.VideoJsBridge
import org.junit.Assert.assertEquals
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
    fun `single tap shows browser controls without toggling playback`() {
        val context: Context = RuntimeEnvironment.getApplication()
        val webView = RecordingWebView(context)
        val bridge = VideoJsBridge(context)
        bridge.attach(webView)
        val overlay = VideoGestureOverlay(context)
        overlay.attach(bridge)
        overlay.enterFullscreenTouchMode()

        overlay.onTouchEvent(event(MotionEvent.ACTION_DOWN, 10f, 10f))
        overlay.onTouchEvent(event(MotionEvent.ACTION_UP, 10f, 10f))

        assertEquals(0, overlay.childCount)
        shadowOf(android.os.Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS)
        assertTrue(overlay.childCount > 0)
        assertTrue(webView.scripts.none { it.contains("togglePlay") })
        overlay.detach()
        bridge.close()
    }

    @Test
    fun `late second touch still confirms the first tap before starting a new gesture`() {
        val context: Context = RuntimeEnvironment.getApplication()
        val overlay = VideoGestureOverlay(context)
        overlay.attach(VideoJsBridge(context))
        overlay.enterFullscreenTouchMode()

        overlay.onTouchEvent(event(MotionEvent.ACTION_DOWN, 10f, 10f, 0))
        overlay.onTouchEvent(event(MotionEvent.ACTION_UP, 10f, 10f, 10))
        overlay.onTouchEvent(event(MotionEvent.ACTION_DOWN, 30f, 30f, 400))

        assertTrue(overlay.childCount > 0)
        overlay.detach()
    }

    @Test
    fun `double tap toggles playback without showing single tap controls`() {
        val context: Context = RuntimeEnvironment.getApplication()
        val webView = RecordingWebView(context)
        val bridge = VideoJsBridge(context)
        bridge.attach(webView)
        val overlay = VideoGestureOverlay(context)
        overlay.attach(bridge)
        overlay.enterFullscreenTouchMode()

        overlay.onTouchEvent(event(MotionEvent.ACTION_DOWN, 10f, 10f, 0))
        overlay.onTouchEvent(event(MotionEvent.ACTION_UP, 10f, 10f, 10))
        overlay.onTouchEvent(event(MotionEvent.ACTION_DOWN, 10f, 10f, 100))
        overlay.onTouchEvent(event(MotionEvent.ACTION_UP, 10f, 10f, 110))
        shadowOf(android.os.Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS)

        assertEquals(1, webView.scripts.count { it.contains("togglePlay") })
        assertEquals(0, overlay.childCount)
        overlay.detach()
        bridge.close()
    }

    @Test
    fun `browser controls can toggle playback and are hidden on reset`() {
        val context: Context = RuntimeEnvironment.getApplication()
        val webView = RecordingWebView(context)
        val bridge = VideoJsBridge(context)
        bridge.attach(webView)
        val overlay = VideoGestureOverlay(context)
        overlay.attach(bridge)
        overlay.enterFullscreenTouchMode()
        overlay.onTouchEvent(event(MotionEvent.ACTION_DOWN, 10f, 10f))
        overlay.onTouchEvent(event(MotionEvent.ACTION_UP, 10f, 10f))
        shadowOf(android.os.Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS)

        val controls = overlay.findViewById<View>(R.id.videoControls)
        assertEquals(View.VISIBLE, controls.visibility)
        controls.findViewById<View>(R.id.videoTogglePlayback).performClick()
        assertEquals(1, webView.scripts.count { it.contains("togglePlay") })

        overlay.resetGesture()
        assertEquals(View.GONE, controls.visibility)
        overlay.detach()
        bridge.close()
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
    fun `controls do not intercept page touches outside inline video bounds`() {
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
        val controls = overlay.findViewById<View>(R.id.videoControls)
        assertEquals(20, controls.left)
        assertEquals(300, controls.right)
        assertEquals(220, controls.bottom)
        overlay.setInlineContentTopInset(60)
        parent.measure(
            View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY)
        )
        parent.layout(0, 0, 320, 600)
        assertEquals(280, controls.bottom)

        parent.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, 50f, 580f))
        parent.dispatchTouchEvent(event(MotionEvent.ACTION_UP, 50f, 580f))
        assertEquals(2, pageTouches)
        overlay.setActiveVideoBounds(null)
        assertEquals(View.GONE, controls.visibility)
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
            true,
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
