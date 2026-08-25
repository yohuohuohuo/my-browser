package com.yohuohuohuo.browser.gesture

import android.content.Context
import android.webkit.ValueCallback
import android.webkit.WebView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoJsBridgeDimensionsTest {

    @Test
    fun `active video dimensions are read from the page and parsed`() {
        val webView = RecordingWebView(RuntimeEnvironment.getApplication())
        val bridge = VideoJsBridge(RuntimeEnvironment.getApplication())
        bridge.attach(webView)

        var dimensions: Pair<Float, Float>? = null
        bridge.getActiveVideoDimensions { width, height ->
            dimensions = width to height
        }

        assertEquals(1920f to 1080f, dimensions)
        assertTrue(webView.lastScript.contains("getActiveVideoDimensions"))
        bridge.close()
    }

    @Test
    fun `dimensions from an older page generation are ignored`() {
        val webView = DeferredWebView(RuntimeEnvironment.getApplication())
        val bridge = VideoJsBridge(RuntimeEnvironment.getApplication())
        bridge.attach(webView)

        var dimensions: Pair<Float, Float>? = null
        bridge.getActiveVideoDimensions { width, height ->
            dimensions = width to height
        }
        bridge.beginNavigation()
        webView.reply("\"1920,1080\"")

        assertNull(dimensions)
        bridge.close()
    }

    private class RecordingWebView(context: Context) : WebView(context) {
        var lastScript: String = ""

        override fun evaluateJavascript(
            script: String,
            resultCallback: ValueCallback<String>?
        ) {
            lastScript = script
            resultCallback?.onReceiveValue("\"1920,1080\"")
        }
    }

    private class DeferredWebView(context: Context) : WebView(context) {
        private var callback: ValueCallback<String>? = null

        override fun evaluateJavascript(
            script: String,
            resultCallback: ValueCallback<String>?
        ) {
            callback = resultCallback
        }

        fun reply(result: String) {
            callback?.onReceiveValue(result)
        }
    }
}
