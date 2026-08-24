package com.yohuohuohuo.browser.ui

import android.content.Context
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.yohuohuohuo.browser.gesture.VideoJsBridge
import org.junit.Assert.assertEquals
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
    fun `fullscreen touch mode confirms a single tap after the double tap window`() {
        val context: Context = RuntimeEnvironment.getApplication()
        val overlay = VideoGestureOverlay(context)
        overlay.attach(VideoJsBridge(context))
        var confirmed = 0
        overlay.onSingleTapConfirmed = { _, _ -> confirmed++ }
        overlay.enterFullscreenTouchMode()

        overlay.onTouchEvent(event(MotionEvent.ACTION_DOWN, 10f, 10f))
        overlay.onTouchEvent(event(MotionEvent.ACTION_UP, 10f, 10f))

        assertEquals(0, confirmed)
        shadowOf(android.os.Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS)
        assertEquals(1, confirmed)
    }

    @Test
    fun `late second touch still confirms the first tap before starting a new gesture`() {
        val context: Context = RuntimeEnvironment.getApplication()
        val overlay = VideoGestureOverlay(context)
        overlay.attach(VideoJsBridge(context))
        var confirmed = 0
        overlay.onSingleTapConfirmed = { _, _ -> confirmed++ }
        overlay.enterFullscreenTouchMode()

        overlay.onTouchEvent(event(MotionEvent.ACTION_DOWN, 10f, 10f, 0))
        overlay.onTouchEvent(event(MotionEvent.ACTION_UP, 10f, 10f, 10))
        overlay.onTouchEvent(event(MotionEvent.ACTION_DOWN, 30f, 30f, 400))

        assertEquals(1, confirmed)
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
}
