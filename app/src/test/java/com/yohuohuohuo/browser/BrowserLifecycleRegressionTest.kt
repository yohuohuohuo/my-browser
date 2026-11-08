package com.yohuohuohuo.browser

import android.content.pm.ActivityInfo
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.widget.EditText
import com.yohuohuohuo.browser.gesture.VideoJsBridge
import com.yohuohuohuo.browser.ui.BrowserWebView
import com.yohuohuohuo.browser.ui.VideoGestureOverlay
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@Suppress("DEPRECATION")
class BrowserLifecycleRegressionTest {
    @Test
    fun `renderer replacement exits fullscreen once and permits the next fullscreen session`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val old = activity.findViewById<BrowserWebView>(R.id.webView)
        navigate(old, "https://playing.example")
        val overlay = activity.findViewById<VideoGestureOverlay>(R.id.videoGestureOverlay)
        val originalParent = overlay.parent
        val chrome = old.webChromeClient!!
        val custom = View(activity)
        var hidden = 0
        chrome.onShowCustomView(custom, WebChromeClient.CustomViewCallback { hidden++ })
        val host = custom.parent as ViewGroup
        activity.setFullscreen(true, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)

        old.webViewClient.onRenderProcessGone(old, object : RenderProcessGoneDetail() {
            override fun didCrash() = true
            override fun rendererPriorityAtExit() = 0
        })

        val replacement = activity.findViewById<BrowserWebView>(R.id.webView)
        assertNull(custom.parent)
        assertNull(host.parent)
        assertSame(originalParent, overlay.parent)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, activity.requestedOrientation)
        assertNotSame(old, replacement)
        assertEquals("https://playing.example", shadowOf(replacement).lastLoadedUrl)
        chrome.onHideCustomView()
        assertEquals(1, hidden)

        val nextCustom = View(activity)
        replacement.webChromeClient!!.onShowCustomView(
            nextCustom, WebChromeClient.CustomViewCallback { hidden++ }
        )
        val fragment = activity.supportFragmentManager.findFragmentById(R.id.fragment_container) as BrowserFragment
        assertTrue(fragment.onBackPressed())
        assertNull(nextCustom.parent)
        assertSame(originalParent, overlay.parent)
        assertEquals(2, hidden)
        assertFalse(overlay.isGestureEnabled())
        controller.pause().stop().destroy()
    }

    @Test
    fun `activity recreation restores current page with back and forward history`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val old = controller.get().findViewById<BrowserWebView>(R.id.webView)
        navigate(old, "https://example.com/first")
        navigate(old, "https://example.com/second")

        controller.recreate()
        shadowOf(Looper.getMainLooper()).idle()

        val activity = controller.get()
        val restored = activity.findViewById<BrowserWebView>(R.id.webView)
        assertNotSame(old, restored)
        assertTrue(shadowOf(old).wasDestroyCalled())
        assertEquals("https://example.com/second", restored.url)
        assertEquals("https://example.com/second", activity.findViewById<EditText>(R.id.urlInput).text.toString())
        assertTrue(restored.canGoBack())
        restored.webViewClient.onPageFinished(restored, restored.url)
        assertTrue(shadowOf(restored).lastEvaluatedJavascript.contains("querySelectorAll('audio,video')"))
        assertFalse(shadowOf(restored).lastEvaluatedJavascript.contains(".play("))
        val fragment = activity.supportFragmentManager.findFragmentById(R.id.fragment_container) as BrowserFragment
        assertTrue(fragment.goBack())
        assertEquals("https://example.com/first", restored.url)
        assertTrue(restored.canGoForward())
        assertTrue(restored.settings.mediaPlaybackRequiresUserGesture)
        restored.webViewClient.onPageStarted(restored, restored.url, null)
        restored.webViewClient.onPageFinished(restored, restored.url)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("https://example.com/first", activity.findViewById<EditText>(R.id.urlInput).text.toString())
        controller.pause().stop().destroy()
    }

    @Test
    fun `recreation reloads the current URL when WebView has no restorable history`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val old = controller.get().findViewById<BrowserWebView>(R.id.webView)
        old.webViewClient.onPageStarted(old, "https://example.com/pending", null)

        controller.recreate()

        val restored = controller.get().findViewById<BrowserWebView>(R.id.webView)
        assertEquals("https://example.com/pending", shadowOf(restored).lastLoadedUrl)
        controller.pause().stop().destroy()
    }

    @Test
    fun `Home before recreation does not resurrect a saved page or its history`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        navigate(activity.findViewById(R.id.webView), "https://example.com/old")
        activity.findViewById<View>(R.id.homeButton).performClick()

        controller.recreate()
        shadowOf(Looper.getMainLooper()).idle()

        val restoredActivity = controller.get()
        val restored = restoredActivity.findViewById<BrowserWebView>(R.id.webView)
        assertNull(restored.url)
        assertFalse(restored.canGoBack())
        assertEquals("", restoredActivity.findViewById<EditText>(R.id.urlInput).text.toString())
        assertEquals(View.VISIBLE, restoredActivity.findViewById<View>(R.id.startupView).visibility)
        controller.pause().stop().destroy()
    }

    @Test
    fun `same document history keeps bridge generation and updates address and recovery URL`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val webView = activity.findViewById<BrowserWebView>(R.id.webView)
        navigate(webView, "https://example.com/article")
        val bridge = shadowOf(webView).getJavascriptInterface("AndroidBridge") as VideoJsBridge
        val generation = VideoJsBridge::class.java.getDeclaredField("pageGeneration").apply { isAccessible = true }.getLong(bridge)
        val updated = "https://example.com/article#chapter2"
        // Robolectric does not execute location/hash/history JS; model the platform URL and history callback.
        webView.loadUrl(updated)
        webView.webViewClient.doUpdateVisitedHistory(webView, updated, false)
        bridge.onVideoActive(generation)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(updated, activity.findViewById<EditText>(R.id.urlInput).text.toString())
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.videoGestureOverlay).visibility)
        webView.webViewClient.onRenderProcessGone(webView, null)
        assertEquals(updated, shadowOf(activity.findViewById<BrowserWebView>(R.id.webView)).lastLoadedUrl)
        controller.pause().stop().destroy()
    }

    @Test
    fun `history update during loading does not reject document completion or replace its new URL`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val webView = activity.findViewById<BrowserWebView>(R.id.webView)
        val original = "https://example.com/article"
        webView.loadUrl(original)
        webView.webViewClient.onPageStarted(webView, original, null)
        val updated = "https://example.com/renamed?part=2#chapter"
        webView.loadUrl(updated)
        webView.webViewClient.doUpdateVisitedHistory(webView, updated, false)
        webView.webViewClient.onPageFinished(webView, original)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(updated, activity.findViewById<EditText>(R.id.urlInput).text.toString())
        assertEquals(View.GONE, activity.findViewById<View>(R.id.loadingProgress).visibility)
        assertTrue(shadowOf(webView).lastEvaluatedJavascript.contains("setCallbacks"))
        controller.pause().stop().destroy()
    }

    @Test
    fun `late history callback from old URL or replaced WebView cannot change current address`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val old = activity.findViewById<BrowserWebView>(R.id.webView)
        navigate(old, "https://example.com/first")
        val oldClient = old.webViewClient
        navigate(old, "https://example.com/second")
        oldClient.doUpdateVisitedHistory(old, "https://example.com/first", false)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("https://example.com/second", activity.findViewById<EditText>(R.id.urlInput).text.toString())
        activity.findViewById<View>(R.id.homeButton).performClick()
        oldClient.doUpdateVisitedHistory(old, "https://example.com/second", false)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("", activity.findViewById<EditText>(R.id.urlInput).text.toString())
        controller.pause().stop().destroy()
    }

    private fun navigate(webView: BrowserWebView, url: String) {
        webView.loadUrl(url)
        // ShadowWebView's default loadUrl does not commit a history entry or dispatch callbacks.
        shadowOf(webView).pushEntryToHistory(url)
        webView.webViewClient.onPageStarted(webView, url, null)
        webView.webViewClient.onPageFinished(webView, url)
        shadowOf(Looper.getMainLooper()).idle()
    }
}
