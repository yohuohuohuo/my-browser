package com.yohuohuohuo.browser

import android.content.pm.ActivityInfo
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.yohuohuohuo.browser.ui.BrowserWebView
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit

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
