package com.yohuohuohuo.browser.ui

import android.content.Context
import android.view.ContextThemeWrapper
import android.widget.EditText
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
}
