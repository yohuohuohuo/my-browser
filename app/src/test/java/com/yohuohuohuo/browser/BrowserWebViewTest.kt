package com.yohuohuohuo.browser

import android.content.Context
import android.view.MotionEvent
import com.yohuohuohuo.browser.ui.BrowserWebView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BrowserWebViewTest {

    @Test
    fun `gesture takeover cancels an already forwarded webview touch`() {
        val webView = RecordingBrowserWebView(RuntimeEnvironment.getApplication())
        webView.gestureHandler = { event ->
            event.actionMasked == MotionEvent.ACTION_MOVE
        }

        webView.onTouchEvent(event(MotionEvent.ACTION_DOWN, 0f, 0f))
        webView.onTouchEvent(event(MotionEvent.ACTION_MOVE, 40f, 0f))

        assertEquals(
            listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL),
            webView.forwardedActions
        )
    }

    @Test
    fun `touch state callback reports a forwarded touch lifecycle`() {
        val webView = RecordingBrowserWebView(RuntimeEnvironment.getApplication())
        val states = mutableListOf<Boolean>()
        webView.onTouchStateChanged = { states += it }

        webView.onTouchEvent(event(MotionEvent.ACTION_DOWN, 0f, 0f))
        webView.onTouchEvent(event(MotionEvent.ACTION_UP, 0f, 0f))

        assertEquals(listOf(true, false), states)
    }

    @Test
    fun `normal touch keeps the full lifecycle when webview down is not handled`() {
        val webView = RecordingBrowserWebView(RuntimeEnvironment.getApplication())
        webView.dispatchResult = { false }
        val handled = mutableListOf<Boolean>()

        handled += webView.onTouchEvent(event(MotionEvent.ACTION_DOWN, 0f, 0f))
        handled += webView.onTouchEvent(event(MotionEvent.ACTION_MOVE, 0f, -80f))
        handled += webView.onTouchEvent(event(MotionEvent.ACTION_UP, 0f, -80f))

        assertEquals(listOf(true, true, true), handled)
        assertEquals(
            listOf(
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_UP
            ),
            webView.forwardedActions
        )
    }

    private fun event(action: Int, x: Float, y: Float): MotionEvent =
        MotionEvent.obtain(0, 0, action, x, y, 0)

    private class RecordingBrowserWebView(context: Context) : BrowserWebView(context) {
        val forwardedActions = mutableListOf<Int>()
        var dispatchResult: (Int) -> Boolean = { true }

        override fun dispatchToWebView(event: MotionEvent): Boolean {
            forwardedActions += event.actionMasked
            return dispatchResult(event.actionMasked)
        }
    }
}
