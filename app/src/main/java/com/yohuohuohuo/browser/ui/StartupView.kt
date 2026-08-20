package com.yohuohuohuo.browser.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import com.yohuohuohuo.browser.databinding.ViewStartupBinding

class StartupView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val binding = ViewStartupBinding.inflate(LayoutInflater.from(context), this, true)

    var onSearchSubmitted: ((String) -> Unit)? = null

    init {
        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.searchInput.text.toString()
                if (query.isNotBlank()) {
                    onSearchSubmitted?.invoke(query)
                }
                true
            } else false
        }
    }

    fun focusInput() {
        binding.searchInput.requestFocus()
    }

    fun clearInput() {
        binding.searchInput.text.clear()
    }
}
