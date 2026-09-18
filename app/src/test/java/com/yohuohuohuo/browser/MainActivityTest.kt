package com.yohuohuohuo.browser

import android.content.pm.ActivityInfo
import android.content.Context
import android.net.Uri
import android.os.Looper
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.yohuohuohuo.browser.ui.BrowserWebView
import com.yohuohuohuo.browser.ui.VideoGestureOverlay
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityTest {

    @Test
    fun `fullscreen applies video orientation and restores unspecified orientation`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()

        activity.setFullscreen(
            fullscreen = true,
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        )
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, activity.requestedOrientation)

        activity.setFullscreen(false)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, activity.requestedOrientation)
    }

    @Test
    fun `inline video long press cancels the native touch before release`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val webView = activity.findViewById<BrowserWebView>(R.id.webView)
        val overlay = activity.findViewById<VideoGestureOverlay>(R.id.videoGestureOverlay)
        overlay.setActiveVideoBounds(RectF(0f, 0f, 200f, 200f))
        overlay.visibility = View.VISIBLE
        val touchStates = mutableListOf<Boolean>()
        webView.onTouchStateChanged = { touchStates += it }

        webView.onTouchEvent(videoTouch(MotionEvent.ACTION_DOWN, 100f, 100f, 0))
        shadowOf(Looper.getMainLooper()).idleFor(
            ViewConfiguration.getLongPressTimeout().toLong() + 50L,
            TimeUnit.MILLISECONDS
        )

        assertEquals(listOf(true, false), touchStates)
    }

    @Test
    fun `fullscreen first tap reaches the original custom view without replay`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val webView = activity.findViewById<BrowserWebView>(R.id.webView)
        val nativeView = RecordingVideoView(activity)
        webView.webChromeClient!!.onShowCustomView(nativeView, object : WebChromeClient.CustomViewCallback {
            override fun onCustomViewHidden() = Unit
        })
        val touchHost = nativeView.parent as ViewGroup
        touchHost.measure(
            View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY)
        )
        touchHost.layout(0, 0, 320, 600)

        touchHost.dispatchTouchEvent(videoTouch(MotionEvent.ACTION_DOWN, 100f, 100f, 0))
        touchHost.dispatchTouchEvent(videoTouch(MotionEvent.ACTION_UP, 100f, 100f, 20))

        assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP), nativeView.actions)
        assertEquals(0, nativeView.actions.count { it == MotionEvent.ACTION_CANCEL })
        webView.webChromeClient!!.onHideCustomView()
    }

    @Test
    fun `fullscreen horizontal swipe cancels the native touch after down`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val webView = activity.findViewById<BrowserWebView>(R.id.webView)
        val nativeView = RecordingVideoView(activity)
        webView.webChromeClient!!.onShowCustomView(nativeView, object : WebChromeClient.CustomViewCallback {
            override fun onCustomViewHidden() = Unit
        })
        val touchHost = nativeView.parent as ViewGroup
        touchHost.measure(
            View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY)
        )
        touchHost.layout(0, 0, 320, 600)

        touchHost.dispatchTouchEvent(videoTouch(MotionEvent.ACTION_DOWN, 100f, 100f, 0))
        touchHost.dispatchTouchEvent(videoTouch(MotionEvent.ACTION_MOVE, 200f, 100f, 20))
        touchHost.dispatchTouchEvent(videoTouch(MotionEvent.ACTION_UP, 200f, 100f, 40))

        assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL), nativeView.actions)
        webView.webChromeClient!!.onHideCustomView()
    }

    private fun videoTouch(action: Int, x: Float, y: Float, time: Long): MotionEvent =
        MotionEvent.obtain(0, time, action, x, y, 0)

    private class RecordingVideoView(context: Context) : View(context) {
        val actions = mutableListOf<Int>()

        override fun onTouchEvent(event: MotionEvent): Boolean {
            actions += event.actionMasked
            return true
        }
    }

    @Test
    fun `startup page hides the address bar and page state restores it`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val fragment = activity.supportFragmentManager
            .findFragmentById(R.id.fragment_container) as BrowserFragment
        val root = fragment.requireView()
        val urlBar = root.findViewById<View>(R.id.urlBarView)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(View.GONE, urlBar.visibility)

        val webView = root.findViewById<BrowserWebView>(R.id.webView)
        webView.webViewClient.onPageStarted(webView, "https://example.com", null)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(View.VISIBLE, urlBar.visibility)
    }

    @Test
    fun `startup search clears its input before navigating`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val fragment = activity.supportFragmentManager
            .findFragmentById(R.id.fragment_container) as BrowserFragment
        val searchInput = fragment.requireView().findViewById<EditText>(R.id.searchInput)
        searchInput.setText("typed query")

        searchInput.onEditorAction(EditorInfo.IME_ACTION_SEARCH)

        assertEquals("", searchInput.text.toString())
    }

    @Test
    fun `startup search submission hides the keyboard`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val searchInput = activity.findViewById<EditText>(R.id.searchInput)
        val inputMethod = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        searchInput.requestFocus()
        inputMethod.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
        assertTrue(shadowOf(inputMethod).isSoftInputVisible)
        searchInput.setText("example.com")

        searchInput.onEditorAction(EditorInfo.IME_ACTION_SEARCH)

        assertFalse(shadowOf(inputMethod).isSoftInputVisible)
    }

    @Test
    fun `address bar submission hides the keyboard`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val webView = activity.findViewById<BrowserWebView>(R.id.webView)
        webView.webViewClient.onPageStarted(webView, "https://old.example", null)
        shadowOf(Looper.getMainLooper()).idle()
        val urlInput = activity.findViewById<EditText>(R.id.urlInput)
        val inputMethod = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        urlInput.requestFocus()
        inputMethod.showSoftInput(urlInput, InputMethodManager.SHOW_IMPLICIT)
        assertTrue(shadowOf(inputMethod).isSoftInputVisible)
        urlInput.setText("https://new.example")

        urlInput.onEditorAction(EditorInfo.IME_ACTION_GO)

        assertFalse(shadowOf(inputMethod).isSoftInputVisible)
    }

    @Test
    fun `web content starts below the visible status-safe address bar`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val fragment = activity.supportFragmentManager
            .findFragmentById(R.id.fragment_container) as BrowserFragment
        val root = fragment.requireView()
        val webView = root.findViewById<BrowserWebView>(R.id.webView)
        webView.webViewClient.onPageStarted(webView, "https://example.com", null)
        shadowOf(Looper.getMainLooper()).idle()
        val widthSpec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)

        root.measure(widthSpec, heightSpec)
        root.layout(0, 0, 1080, 1920)
        ViewCompat.dispatchApplyWindowInsets(
            root,
            WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 24, 0, 0))
                .build()
        )
        root.measure(widthSpec, heightSpec)
        root.layout(0, 0, 1080, 1920)

        val urlBar = root.findViewById<View>(R.id.urlBarView)
        val container = root.findViewById<FrameLayout>(R.id.webViewContainer)

        assertTrue(urlBar.height > 24)
        assertEquals(
            urlBar.bottom,
            (container.layoutParams as FrameLayout.LayoutParams).topMargin
        )
        assertEquals(0, webView.top)
        assertTrue((container.layoutParams as FrameLayout.LayoutParams).topMargin >= 24)
    }

    @Test
    fun `loading progress is positioned at the bottom of the visible address bar`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val fragment = activity.supportFragmentManager
            .findFragmentById(R.id.fragment_container) as BrowserFragment
        val root = fragment.requireView()
        val webView = root.findViewById<BrowserWebView>(R.id.webView)
        webView.webViewClient.onPageStarted(webView, "https://example.com", null)
        shadowOf(Looper.getMainLooper()).idle()
        val widthSpec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)

        root.measure(widthSpec, heightSpec)
        root.layout(0, 0, 1080, 1920)
        ViewCompat.dispatchApplyWindowInsets(
            root,
            WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 24, 0, 0))
                .build()
        )
        root.measure(widthSpec, heightSpec)
        root.layout(0, 0, 1080, 1920)

        val progressId = root.resources.getIdentifier(
            "loadingProgress",
            "id",
            activity.packageName
        )
        assertTrue("A loading progress view must be present", progressId != 0)
        val progress = root.findViewById<ProgressBar>(progressId)
        assertTrue(progress != null)

        val urlBar = root.findViewById<View>(R.id.urlBarView)
        assertEquals(
            urlBar.height,
            (progress.layoutParams as FrameLayout.LayoutParams).topMargin
        )
        assertEquals(100, progress.max)
    }

    @Test
    fun `home button clears the address bar and shows the startup page`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val fragment = activity.supportFragmentManager
            .findFragmentById(R.id.fragment_container) as BrowserFragment
        val root = fragment.requireView()
        val urlInput = root.findViewById<EditText>(R.id.urlInput)
        urlInput.setText("https://stale.example")

        root.findViewById<ImageButton>(R.id.homeButton).performClick()

        assertEquals("", urlInput.text.toString())
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.startupView).visibility)
    }

    @Test
    fun `home unloads the old page and next navigation uses a fresh WebView`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val fragment = activity.supportFragmentManager
            .findFragmentById(R.id.fragment_container) as BrowserFragment
        val root = fragment.requireView()
        val oldWebView = root.findViewById<BrowserWebView>(R.id.webView)
        val oldClient = oldWebView.webViewClient
        val oldChromeClient = oldWebView.webChromeClient!!
        oldWebView.webViewClient.onPageStarted(oldWebView, "https://playing.example", null)
        shadowOf(Looper.getMainLooper()).idle()

        root.findViewById<ImageButton>(R.id.homeButton).performClick()

        val freshWebView = root.findViewById<BrowserWebView>(R.id.webView)
        assertFalse("Home must replace the loaded WebView", freshWebView === oldWebView)
        assertEquals(null, oldWebView.parent)
        assertTrue(shadowOf(oldWebView).wasDestroyCalled())
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.startupView).visibility)

        oldClient.onPageStarted(oldWebView, "https://stale-callback.example", null)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.startupView).visibility)
        val staleFullscreenView = RecordingVideoView(activity)
        oldChromeClient.onShowCustomView(staleFullscreenView, object : WebChromeClient.CustomViewCallback {
            override fun onCustomViewHidden() = Unit
        })
        assertEquals(null, staleFullscreenView.parent)

        fragment.loadUrl("https://next.example")
        assertEquals("https://next.example", shadowOf(freshWebView).lastLoadedUrl)
    }

    @Test
    fun `back to startup also unloads the old page`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val fragment = activity.supportFragmentManager
            .findFragmentById(R.id.fragment_container) as BrowserFragment
        val oldWebView = fragment.requireView().findViewById<BrowserWebView>(R.id.webView)
        oldWebView.webViewClient.onPageStarted(oldWebView, "https://playing.example", null)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(fragment.onBackPressed())

        assertFalse(fragment.requireView().findViewById<BrowserWebView>(R.id.webView) === oldWebView)
        assertEquals(null, oldWebView.parent)
    }

    @Test
    fun `background pauses page media and foreground does not restart playback`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val webView = activity.findViewById<BrowserWebView>(R.id.webView)
        webView.webViewClient.onPageStarted(webView, "https://playing.example", null)

        controller.pause()

        assertTrue(shadowOf(webView).wasOnPauseCalled())
        assertTrue(shadowOf(webView).lastEvaluatedJavascript.contains("querySelectorAll('audio,video')"))

        controller.resume()
        assertTrue(shadowOf(webView).wasOnResumeCalled())
        assertFalse(shadowOf(webView).lastEvaluatedJavascript.contains(".play("))
    }

    @Test
    fun `main frame error pauses hidden media and retry resumes WebView processing`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val webView = activity.findViewById<BrowserWebView>(R.id.webView)
        val url = "https://failed.example"
        webView.webViewClient.onPageStarted(webView, url, null)
        val request = object : WebResourceRequest {
            override fun getUrl(): Uri = Uri.parse(url)
            override fun isForMainFrame(): Boolean = true
            override fun isRedirect(): Boolean = false
            override fun hasGesture(): Boolean = false
            override fun getMethod(): String = "GET"
            override fun getRequestHeaders(): Map<String, String> = emptyMap()
        }
        val response = WebResourceResponse(
            "text/html", "utf-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(byteArrayOf())
        )

        webView.webViewClient.onReceivedHttpError(webView, request, response)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(shadowOf(webView).wasOnPauseCalled())
        assertTrue(shadowOf(webView).lastEvaluatedJavascript.contains("querySelectorAll('audio,video')"))
        webView.webViewClient.onPageFinished(webView, url)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.errorView).visibility)
        activity.findViewById<View>(R.id.retryButton).performClick()
        assertTrue(shadowOf(webView).wasOnResumeCalled())
        assertEquals(url, shadowOf(webView).lastLoadedUrl)
    }

    @Test
    fun `late completion from previous navigation does not replace current URL`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val webView = activity.findViewById<BrowserWebView>(R.id.webView)
        val client = webView.webViewClient
        client.onPageStarted(webView, "https://first.example", null)
        client.onPageStarted(webView, "https://second.example", null)

        client.onPageFinished(webView, "https://first.example")
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("https://second.example", activity.findViewById<EditText>(R.id.urlInput).text.toString())
    }

    @Test
    fun `web viewport stays stable during scroll until toolbar visibility settles`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val fragment = activity.supportFragmentManager
            .findFragmentById(R.id.fragment_container) as BrowserFragment
        val root = fragment.requireView()
        val webView = root.findViewById<BrowserWebView>(R.id.webView)
        webView.webViewClient.onPageStarted(webView, "https://example.com", null)
        shadowOf(Looper.getMainLooper()).idle()
        val widthSpec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)

        root.measure(widthSpec, heightSpec)
        root.layout(0, 0, 1080, 1920)
        ViewCompat.dispatchApplyWindowInsets(
            root,
            WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 24, 0, 0))
                .build()
        )
        root.measure(widthSpec, heightSpec)
        root.layout(0, 0, 1080, 1920)

        val container = root.findViewById<FrameLayout>(R.id.webViewContainer)
        val visibleTopInset = (container.layoutParams as FrameLayout.LayoutParams).topMargin

        webView.scrollTo(0, 120)
        shadowOf(Looper.getMainLooper()).idleFor(80, TimeUnit.MILLISECONDS)
        webView.scrollTo(0, 240)

        assertEquals(
            "The WebView viewport must not move while the scroll callback stream is active",
            visibleTopInset,
            (container.layoutParams as FrameLayout.LayoutParams).topMargin
        )
        shadowOf(Looper.getMainLooper()).idleFor(200, TimeUnit.MILLISECONDS)
        assertEquals(
            visibleTopInset,
            (container.layoutParams as FrameLayout.LayoutParams).topMargin
        )
        shadowOf(Looper.getMainLooper()).idleFor(40, TimeUnit.MILLISECONDS)
        assertEquals(
            "The WebView viewport remains stable until the touch lifecycle ends",
            visibleTopInset,
            (container.layoutParams as FrameLayout.LayoutParams).topMargin
        )

        // Robolectric's WebView may retain a synthetic forwarded touch state;
        // invoke the production callback to model the finger being released.
        webView.onTouchStateChanged?.invoke(false)
        shadowOf(Looper.getMainLooper()).idleFor(301, TimeUnit.MILLISECONDS)
        assertEquals(
            24,
            (container.layoutParams as FrameLayout.LayoutParams).topMargin
        )
    }
}
