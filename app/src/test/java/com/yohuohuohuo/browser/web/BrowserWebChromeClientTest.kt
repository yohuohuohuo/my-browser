package com.yohuohuohuo.browser.web

import android.content.Context
import android.view.View
import android.webkit.WebChromeClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BrowserWebChromeClientTest {

    @Test
    fun `host hide clears custom view and notifies callback once`() {
        val context: Context = RuntimeEnvironment.getApplication()
        var hideNotifications = 0
        var callbackNotifications = 0
        val client = BrowserWebChromeClient(
            onProgressChangedCallback = {},
            onShowCustomViewCallback = {},
            onHideCustomViewCallback = { hideNotifications++ }
        )
        val callback = object : WebChromeClient.CustomViewCallback {
            override fun onCustomViewHidden() {
                callbackNotifications++
            }
        }

        client.onShowCustomView(View(context), callback)
        assertTrue(client.hasCustomView)

        client.hideCustomViewFromHost()
        client.onHideCustomView()

        assertFalse(client.hasCustomView)
        assertEquals(1, hideNotifications)
        assertEquals(1, callbackNotifications)
    }
}
