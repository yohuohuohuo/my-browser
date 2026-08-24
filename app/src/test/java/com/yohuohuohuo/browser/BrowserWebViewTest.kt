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

    private fun event(action: Int, x: Float, y: Float): MotionEvent =
        MotionEvent.obtain(0, 0, action, x, y, 0)

    private class RecordingBrowserWebView(context: Context) : BrowserWebView(context) {
        val forwardedActions = mutableListOf<Int>()

        override fun dispatchToWebView(event: MotionEvent): Boolean {
            forwardedActions += event.actionMasked
            return true
        }
    }
}
