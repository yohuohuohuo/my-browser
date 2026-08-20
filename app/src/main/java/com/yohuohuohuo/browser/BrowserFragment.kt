package com.yohuohuohuo.browser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.yohuohuohuo.browser.databinding.FragmentBrowserBinding
import com.yohuohuohuo.browser.gesture.VideoJsBridge
import com.yohuohuohuo.browser.ua.UaManager
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
    private val uaManager by lazy { UaManager(requireContext()) }
    private val videoBridge by lazy { VideoJsBridge(requireContext()) }

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
        binding.urlBarView.onUaButtonClicked = {
            val isDesktop = uaManager.toggle()
            binding.webView.settings.userAgentString = uaManager.getUa()
            viewModel.setUaMode(if (isDesktop) UaMode.DESKTOP else UaMode.MOBILE)
            if (!viewModel.uiState.value.showStartup) {
                binding.webView.reload()
            }
        }

        // Handle window insets for immersive status bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            binding.urlBarView.setTopPadding(statusBarHeight)
            insets
        }

        // Toolbar auto-hide on scroll
        binding.webView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            val delta = scrollY - oldScrollY
            if (delta > 10 && binding.urlBarView.translationY == 0f) {
                hideToolbar()
            } else if (delta < -10 && scrollY <= 0) {
                showToolbar()
            }
        }
    }

    private fun setupWebView() {
        webViewManager.configure(binding.webView)
        binding.webView.webViewClient = BrowserWebViewClient(
            onPageStarted = { url -> viewModel.onPageStarted(url) },
            onPageFinished = { webView, url ->
                viewModel.onPageFinished(url, webView.title ?: "")
                videoBridge.injectScript()
            },
            onReceivedError = { msg, url -> viewModel.onReceivedError(msg, url) }
        )
        binding.webView.webChromeClient = BrowserWebChromeClient(
            onProgressChanged = { progress -> viewModel.onProgressChanged(progress) },
            onShowCustomView = { /* handled in Task 7 */ },
            onHideCustomView = { /* handled in Task 7 */ }
        )
        binding.webView.settings.userAgentString = uaManager.getUa()
        viewModel.setUaMode(if (uaManager.isDesktop()) UaMode.DESKTOP else UaMode.MOBILE)

        videoBridge.attach(binding.webView)
        videoBridge.onVideoActiveChanged = { active ->
            viewModel.setVideoActive(active)
            binding.videoGestureOverlay.visibility =
                if (active) View.VISIBLE else View.GONE
        }
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
                    binding.urlBarView.setUaIcon(state.uaMode == UaMode.DESKTOP)
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

    private fun hideToolbar() {
        binding.urlBarView.animate()
            .translationY(-binding.urlBarView.height.toFloat())
            .setDuration(200)
            .start()
        viewModel.setToolbarVisible(false)
    }

    private fun showToolbar() {
        binding.urlBarView.animate()
            .translationY(0f)
            .setDuration(200)
            .start()
        viewModel.setToolbarVisible(true)
    }

    fun loadUrl(url: String) {
        webViewManager.loadUrl(binding.webView, url)
    }

    fun canGoBack(): Boolean = binding.webView.canGoBack()

    fun goBack(): Boolean = webViewManager.goBack(binding.webView)

    override fun onDestroyView() {
        videoBridge.detach()
        webViewManager.destroy(binding.webView)
        _binding = null
        super.onDestroyView()
    }
}
