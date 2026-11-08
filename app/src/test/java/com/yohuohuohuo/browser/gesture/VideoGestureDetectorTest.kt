package com.yohuohuohuo.browser.gesture

import android.content.Context
import android.os.Looper
import android.view.MotionEvent
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoGestureDetectorTest {

    private lateinit var context: Context
    private lateinit var detector: VideoGestureDetector
    private val events = mutableListOf<String>()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        events.clear()
        detector = VideoGestureDetector(context, object : VideoGestureDetector.GestureListener {
            override fun onLongPressStart() { events.add("longPressStart") }
            override fun onLongPressEnd() { events.add("longPressEnd") }
            override fun onDoubleTap() { events.add("doubleTap") }
            override fun onSingleTapConfirmed() { events.add("singleTap") }
            override fun onHorizontalScroll(deltaX: Float) { events.add("horizontal:$deltaX") }
            override fun onLeftVerticalScroll(deltaY: Float) { events.add("leftVertical:$deltaY") }
            override fun onRightVerticalScroll(deltaY: Float) { events.add("rightVertical:$deltaY") }
            override fun onScrollEnd() { events.add("scrollEnd") }
        }, viewportWidth = { context.resources.displayMetrics.widthPixels })
    }

    private fun down(x: Float, y: Float, time: Long = 0): MotionEvent =
        MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0)

    private fun move(x: Float, y: Float, time: Long = 10): MotionEvent =
        MotionEvent.obtain(time, time, MotionEvent.ACTION_MOVE, x, y, 0)

    private fun up(x: Float, y: Float, time: Long = 20): MotionEvent =
        MotionEvent.obtain(time, time, MotionEvent.ACTION_UP, x, y, 0)

    @Test
    fun `tap does not trigger scroll or long press`() {
        detector.onTouchEvent(down(100f, 100f))
        detector.onTouchEvent(up(100f, 100f))

        assertTrue(events.isEmpty())
        assertFalse(detector.isScrolling())
        assertFalse(detector.isLongPressing())
    }

    @Test
    fun `horizontal scroll triggers onHorizontalScroll`() {
        detector.onTouchEvent(down(100f, 100f))
        // 移动超过 touchSlop（约 8-24px）
        detector.onTouchEvent(move(200f, 100f))
        detector.onTouchEvent(up(200f, 100f))

        assertTrue(events.any { it.startsWith("horizontal:") })
        assertTrue(events.contains("scrollEnd"))
    }

    @Test
    fun `left side vertical scroll triggers onLeftVerticalScroll`() {
        val width = context.resources.displayMetrics.widthPixels
        val leftX = (width * 0.25f).toInt().toFloat()

        detector.onTouchEvent(down(leftX, 100f))
        detector.onTouchEvent(move(leftX, 300f))
        detector.onTouchEvent(up(leftX, 300f))

        assertTrue(events.any { it.startsWith("leftVertical:") })
        assertTrue(events.contains("scrollEnd"))
    }

    @Test
    fun `upward left side vertical scroll reports positive distance`() {
        val width = context.resources.displayMetrics.widthPixels
        val leftX = (width * 0.25f).toInt().toFloat()

        detector.onTouchEvent(down(leftX, 300f))
        detector.onTouchEvent(move(leftX, 100f))

        assertTrue(events.contains("leftVertical:200.0"))
    }

    @Test
    fun `right side vertical scroll triggers onRightVerticalScroll`() {
        val width = context.resources.displayMetrics.widthPixels
        val rightX = (width * 0.75f).toInt().toFloat()

        detector.onTouchEvent(down(rightX, 100f))
        detector.onTouchEvent(move(rightX, 300f))
        detector.onTouchEvent(up(rightX, 300f))

        assertTrue(events.any { it.startsWith("rightVertical:") })
        assertTrue(events.contains("scrollEnd"))
    }

    @Test
    fun `small movement does not trigger scroll`() {
        detector.onTouchEvent(down(100f, 100f))
        // 移动 2px，小于 touchSlop
        detector.onTouchEvent(move(102f, 100f))
        detector.onTouchEvent(up(102f, 100f))

        assertFalse(events.any {
            it.startsWith("horizontal:") || it.startsWith("leftVertical:") || it.startsWith("rightVertical:")
        })
        assertFalse(detector.isScrolling())
    }

    @Test
    fun `shouldInterceptMove returns false before slop threshold`() {
        detector.onTouchEvent(down(100f, 100f, 0))

        val smallMove = move(101f, 100f, 5)
        assertFalse(detector.shouldInterceptMove(smallMove))
    }

    @Test
    fun `shouldInterceptMove returns true after slop threshold`() {
        detector.onTouchEvent(down(100f, 100f, 0))

        val largeMove = move(300f, 100f, 5)
        assertTrue(detector.shouldInterceptMove(largeMove))
    }

    @Test
    fun `cancel resets state without triggering scrollEnd`() {
        detector.onTouchEvent(down(100f, 100f))
        detector.onTouchEvent(move(200f, 100f))

        val cancel = MotionEvent.obtain(30, 30, MotionEvent.ACTION_CANCEL, 200f, 100f, 0)
        detector.onTouchEvent(cancel)

        assertFalse(detector.isScrolling())
        assertFalse(detector.isLongPressing())
    }

    @Test
    fun `double tap triggers onDoubleTap`() {
        // 第一次点击
        detector.onTouchEvent(down(100f, 100f, 0))
        detector.onTouchEvent(up(100f, 100f, 10))

        // 第二次快速点击（在 doubleTapTimeout 内，约 300ms）
        detector.onTouchEvent(down(102f, 102f, 50))
        detector.onTouchEvent(up(102f, 102f, 60))

        assertTrue(events.contains("doubleTap"))
        shadowOf(Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS)
        assertFalse(events.contains("singleTap"))
    }

    @Test
    fun `single tap is confirmed after double tap window`() {
        detector.onTouchEvent(down(100f, 100f, 0))
        detector.onTouchEvent(up(100f, 100f, 10))

        assertFalse(events.contains("singleTap"))
        shadowOf(Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS)
        assertTrue(events.contains("singleTap"))
    }

    @Test
    fun `double tap does not start long press while second tap is held`() {
        detector.onTouchEvent(down(100f, 100f, 0))
        detector.onTouchEvent(up(100f, 100f, 10))
        detector.onTouchEvent(down(102f, 102f, 50))

        shadowOf(Looper.getMainLooper()).idleFor(600, TimeUnit.MILLISECONDS)

        assertTrue(events.contains("doubleTap"))
        assertFalse(events.contains("longPressStart"))
    }

    @Test
    fun `long press release is not treated as a pending tap`() {
        detector.onTouchEvent(down(100f, 100f, 0))
        shadowOf(Looper.getMainLooper()).idleFor(600, TimeUnit.MILLISECONDS)
        detector.onTouchEvent(up(100f, 100f, 600))

        // A subsequent ordinary tap must not be interpreted as the second tap
        // of a double tap caused by the preceding long press.
        detector.onTouchEvent(down(100f, 100f, 700))
        detector.onTouchEvent(up(100f, 100f, 710))

        assertTrue(events.contains("longPressStart"))
        assertTrue(events.contains("longPressEnd"))
        assertFalse(events.contains("doubleTap"))
    }

    @Test
    fun `dispose cancels pending long press callback`() {
        detector.onTouchEvent(down(100f, 100f, 0))
        detector.dispose()
        shadowOf(Looper.getMainLooper()).idleFor(600, TimeUnit.MILLISECONDS)

        assertFalse(events.contains("longPressStart"))
        assertFalse(detector.isLongPressing())
    }
}
