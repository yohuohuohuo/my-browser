package com.yohuohuohuo.browser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.yohuohuohuo.browser.databinding.FragmentBrowserBinding
import com.yohuohuohuo.browser.util.SearchEngine
import com.yohuohuohuo.browser.util.UrlUtils
import com.yohuohuohuo.browser.web.BrowserWebChromeClient
import com.yohuohuohuo.browser.web.BrowserWebViewClient
import com.yohuohuohuo.browser.web.WebViewManager
import kotlinx.coroutines.launch

class BrowserFragment : Fragment() {
    private var _binding: FragmentBrowserBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BrowserViewModel by viewModels()
    private val webViewManager = WebViewManager()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBrowserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupWebView()
        observeState()
        binding.startupView.onSearchSubmitted = { query ->
            handleInput(query)
        }
        binding.urlBarView.onUrlSubmitted = { text ->
            handleInput(text)
        }
        binding.urlBarView.setOnClickListener {
            binding.urlBarView.focusInput()
        }
    }

    private fun setupWebView() {
        webViewManager.configure(binding.webView)
        binding.webView.webViewClient = BrowserWebViewClient(
            onPageStarted = { url -> viewModel.onPageStarted(url) },
            onPageFinished = { webView, url ->
                viewModel.onPageFinished(url, webView.title ?: "")
            },
            onReceivedError = { msg, url -> viewModel.onReceivedError(msg, url) }
        )
        binding.webView.webChromeClient = BrowserWebChromeClient(
            onProgressChanged = { progress -> viewModel.onProgressChanged(progress) },
            onShowCustomView = { /* handled in Task 7 */ },
            onHideCustomView = { /* handled in Task 7 */ }
        )
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.startupView.visibility =
                        if (state.showStartup) View.VISIBLE else View.GONE
                    if (state.showStartup) {
                        binding.startupView.focusInput()
                    } else {
                        binding.urlBarView.setUrl(state.currentUrl)
                    }
                }
            }
        }
    }

    private fun handleInput(text: String) {
        val url = if (UrlUtils.isUrl(text)) {
            UrlUtils.normalizeUrl(text)
        } else {
            SearchEngine.buildSearchUrl(text)
        }
        loadUrl(url)
    }

    fun loadUrl(url: String) {
        webViewManager.loadUrl(binding.webView, url)
    }

    fun canGoBack(): Boolean = binding.webView.canGoBack()

    fun goBack(): Boolean = webViewManager.goBack(binding.webView)

    override fun onDestroyView() {
        webViewManager.destroy(binding.webView)
        _binding = null
        super.onDestroyView()
    }
}
