package com.yohuohuohuo.browser.ui

import android.content.Context
import android.view.ContextThemeWrapper
import android.widget.EditText
import android.widget.ImageButton
import com.yohuohuohuo.browser.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UrlBarViewTest {

    @Test
    fun `state refresh does not overwrite text while user is editing`() {
        val context: Context = ContextThemeWrapper(
            RuntimeEnvironment.getApplication(),
            R.style.Theme_Yobrowser
        )
        val view = UrlBarView(context)
        view.setUrl("https://old.example")
        view.focusInput()
        val input = view.findViewById<EditText>(R.id.urlInput)
        input.setText("typed.example")

        view.setUrl("https://new.example")

        assertEquals("typed.example", input.text.toString())
    }

    @Test
    fun `first toolbar control returns home when clicked`() {
        val context: Context = ContextThemeWrapper(
            RuntimeEnvironment.getApplication(),
            R.style.Theme_Yobrowser
        )
        val view = UrlBarView(context)
        val homeButton = view.findViewById<ImageButton>(R.id.homeButton)
        var homeClicks = 0
        view.onHomeButtonClicked = { homeClicks++ }

        assertEquals("返回主页", homeButton.contentDescription)
        homeButton.performClick()
        assertEquals(1, homeClicks)
    }

    @Test
    fun `clear url removes stale text even after editing`() {
        val context: Context = ContextThemeWrapper(
            RuntimeEnvironment.getApplication(),
            R.style.Theme_Yobrowser
        )
        val view = UrlBarView(context)
        view.setUrl("https://old.example")
        view.focusInput()

        view.clearUrl()

        assertEquals("", view.findViewById<EditText>(R.id.urlInput).text.toString())
    }
}
