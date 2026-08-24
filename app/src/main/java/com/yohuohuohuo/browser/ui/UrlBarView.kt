package com.yohuohuohuo.browser.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import com.yohuohuohuo.browser.R
import com.yohuohuohuo.browser.databinding.ViewUrlBarBinding

class UrlBarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val binding = ViewUrlBarBinding.inflate(LayoutInflater.from(context), this, true)
    private var isEditing = false

    var onUrlSubmitted: ((String) -> Unit)? = null
    var onUaButtonClicked: (() -> Unit)? = null

    init {
        binding.urlInput.setOnFocusChangeListener { _, hasFocus ->
            isEditing = hasFocus
        }
        binding.urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                val text = binding.urlInput.text.toString()
                if (text.isNotBlank()) {
                    onUrlSubmitted?.invoke(text)
                }
                true
            } else false
        }
        binding.uaButton.setOnClickListener {
            onUaButtonClicked?.invoke()
        }
    }

    fun setUrl(url: String) {
        if (!isEditing) {
            binding.urlInput.setText(url)
        }
    }

    fun setTitle(title: String) {
        if (title.isNotBlank()) {
            binding.urlInput.setText(title)
        }
    }

    fun setUaIcon(isDesktop: Boolean) {
        binding.uaButton.setImageResource(
            if (isDesktop) R.drawable.ic_desktop else R.drawable.ic_mobile
        )
    }

    fun focusInput() {
        isEditing = true
        binding.urlInput.requestFocus()
        binding.urlInput.selectAll()
    }

    fun exitEditing() {
        isEditing = false
        binding.urlInput.clearFocus()
    }

    fun setTopPadding(pixels: Int) {
        // 使用 setPaddingRelative 以支持 RTL（paddingStart / paddingEnd）
        binding.root.setPaddingRelative(
            binding.root.paddingStart,
            pixels + resources.getDimensionPixelSize(R.dimen.url_bar_vertical_padding),
            binding.root.paddingEnd,
            resources.getDimensionPixelSize(R.dimen.url_bar_vertical_padding)
        )
    }
}
