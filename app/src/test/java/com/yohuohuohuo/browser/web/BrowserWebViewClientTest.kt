package com.yohuohuohuo.browser.web

import android.content.Context
import android.net.Uri
import android.net.http.SslError
import android.view.ContextThemeWrapper
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import com.yohuohuohuo.browser.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@Suppress("DEPRECATION")
class BrowserWebViewClientTest {

    @Test
    fun `renderer gone is consumed and reports crash state`() {
        var crashed: Boolean? = null
        val client = BrowserWebViewClient(
            onPageStartedCallback = {},
            onPageFinishedCallback = { _, _ -> },
            onReceivedErrorCallback = { _, _ -> },
            onRenderProcessGoneCallback = { crashed = it }
        )
        val detail = object : RenderProcessGoneDetail() {
            override fun didCrash(): Boolean = true
            override fun rendererPriorityAtExit(): Int = 0
        }

        assertTrue(client.onRenderProcessGone(null, detail))
        assertEquals(true, crashed)
    }

    @Test
    fun `main frame http errors are reported but successful responses are ignored`() {
        val errors = mutableListOf<String>()
        val client = BrowserWebViewClient(
            onPageStartedCallback = {},
            onPageFinishedCallback = { _, _ -> },
            onReceivedErrorCallback = { message, url -> errors += "$message@$url" }
        )
        val request = object : WebResourceRequest {
            override fun getUrl(): Uri = Uri.parse("https://example.com")
            override fun isForMainFrame(): Boolean = true
            override fun isRedirect(): Boolean = false
            override fun hasGesture(): Boolean = false
            override fun getMethod(): String = "GET"
            override fun getRequestHeaders(): Map<String, String> = emptyMap()
        }
        val success = WebResourceResponse(
            "text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0))
        )
        val failure = WebResourceResponse(
            "text/plain", "UTF-8", 500, "Server Error",
            emptyMap(), ByteArrayInputStream(ByteArray(0))
        )

        client.onReceivedHttpError(null, request, success)
        client.onReceivedHttpError(null, request, failure)

        assertEquals(listOf("HTTP 500 error@https://example.com"), errors)
    }

    @Test
    fun `ssl errors from subresources are cancelled without replacing the page`() {
        val errors = mutableListOf<String>()
        val client = BrowserWebViewClient(
            onPageStartedCallback = {},
            onPageFinishedCallback = { _, _ -> },
            onReceivedErrorCallback = { _, _ -> },
            onSslErrorCallback = { errors += it }
        )
        val context: Context = ContextThemeWrapper(
            org.robolectric.RuntimeEnvironment.getApplication(),
            R.style.Theme_Yobrowser
        )
        val view = object : WebView(context) {
            override fun getUrl(): String = "https://example.com"
        }
        val subresourceError = SslError(
            SslError.SSL_UNTRUSTED,
            android.net.http.SslCertificate("", "", "", ""),
            "https://cdn.example.com/video.mp4"
        )

        client.onReceivedSslError(view, null, subresourceError)

        assertEquals(emptyList<String>(), errors)
    }
}
