package com.yohuohuohuo.browser.gesture

import android.content.Context
import android.graphics.RectF
import android.webkit.WebView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoJsBridgeTest {

    private lateinit var bridge: VideoJsBridge

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        bridge = VideoJsBridge(context)
        bridge.attach(WebView(context))
    }

    @After
    fun tearDown() {
        bridge.close()
    }

    @Test
    fun `callback from an older page generation is ignored`() {
        var activeCallbacks = 0
        bridge.onVideoActiveChanged = { activeCallbacks++ }

        bridge.beginNavigation()
        bridge.onVideoActive(1L)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(0, activeCallbacks)

        bridge.onVideoActive(2L)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(1, activeCallbacks)
    }

    @Test
    fun `current page video bounds are forwarded`() {
        var observedBounds: RectF? = null
        bridge.onVideoBoundsChanged = { observedBounds = it }

        bridge.onVideoBounds(1L, 10f, 20f, 100f, 200f)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals(RectF(10f, 20f, 110f, 220f), observedBounds)
    }
}
