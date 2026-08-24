package com.yohuohuohuo.browser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
import com.yohuohuohuo.browser.ui.BrowserWebView
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
    private var activeWebView: BrowserWebView? = null
    private var customView: View? = null
    private var webChromeClient: BrowserWebChromeClient? = null
    private var toolbarHidden = false
    private var fullscreenOverlayParent: ViewGroup? = null
    private var fullscreenOverlayIndex = -1

    private val currentWebView: BrowserWebView
        get() = activeWebView ?: binding.webView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBrowserBinding.inflate(inflater, container, false)
        activeWebView = binding.webView
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
            binding.urlBarView.exitEditing()
            handleInput(text)
        }
        binding.urlBarView.setOnClickListener {
            binding.urlBarView.focusInput()
        }
        binding.urlBarView.onUaButtonClicked = {
            val isDesktop = uaManager.toggle()
            currentWebView.settings.userAgentString = uaManager.getUa()
            viewModel.setUaMode(if (isDesktop) UaMode.DESKTOP else UaMode.MOBILE)
            if (!viewModel.uiState.value.showStartup) {
                currentWebView.reload()
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            binding.urlBarView.setTopPadding(statusBarHeight)
            insets
        }

        binding.errorView.retryButton.setOnClickListener {
            val url = viewModel.uiState.value.error?.failingUrl ?: viewModel.uiState.value.currentUrl
            if (url.isNotBlank()) {
                loadUrl(url)
            }
        }
    }

    private fun setupWebView() {
        val webView = currentWebView
        webViewManager.configure(webView)
        webView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            val delta = scrollY - oldScrollY
            if (delta > 10 && !toolbarHidden) {
                hideToolbar()
            } else if (delta < -10 && toolbarHidden) {
                showToolbar()
            }
        }
        webView.webViewClient = BrowserWebViewClient(
            onPageStartedCallback = { url ->
                videoBridge.beginNavigation()
                resetVideoGestureState()
                if (url.equals(ABOUT_BLANK, ignoreCase = true)) {
                    viewModel.showStartup()
                } else {
                    viewModel.onPageStarted(url)
                }
            },
            onPageFinishedCallback = { finishedWebView, url ->
                if (url.equals(ABOUT_BLANK, ignoreCase = true)) {
                    viewModel.showStartup()
                } else {
                    viewModel.onPageFinished(url, finishedWebView.title ?: "")
                    videoBridge.injectScript()
                }
            },
            onReceivedErrorCallback = { message, url ->
                viewModel.onReceivedError(message, url)
            },
            onSslErrorCallback = { url ->
                viewModel.onReceivedError("SSL certificate error", url)
            },
            onRenderProcessGoneCallback = { didCrash ->
                recreateWebViewAfterRendererGone(didCrash)
            }
        )

        val chromeClient = BrowserWebChromeClient(
            onProgressChangedCallback = { progress -> viewModel.onProgressChanged(progress) },
            onShowCustomViewCallback = { view ->
                customView = view
                val decor = activity?.window?.decorView as? FrameLayout
                (view.parent as? ViewGroup)?.removeView(view)
                decor?.addView(
                    view,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
                val overlay = binding.videoGestureOverlay
                fullscreenOverlayParent = overlay.parent as? ViewGroup
                fullscreenOverlayIndex = fullscreenOverlayParent?.indexOfChild(overlay) ?: -1
                fullscreenOverlayParent?.removeView(overlay)
                decor?.addView(
                    overlay,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
                overlay.visibility = View.VISIBLE
                overlay.enterFullscreenTouchMode()
                binding.urlBarView.animate().cancel()
                binding.urlBarView.visibility = View.GONE
                (activity as? MainActivity)?.setFullscreen(true)
            },
            onHideCustomViewCallback = {
                val overlay = _binding?.videoGestureOverlay
                overlay?.exitFullscreenTouchMode()
                overlay?.visibility =
                    if (viewModel.uiState.value.videoActive) View.VISIBLE else View.GONE
                (overlay?.parent as? ViewGroup)?.removeView(overlay)
                fullscreenOverlayParent?.let { parent ->
                    if (overlay != null && overlay.parent == null) {
                        val index = fullscreenOverlayIndex.coerceIn(0, parent.childCount)
                        parent.addView(
                            overlay,
                            index,
                            FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                            )
                        )
                    }
                }
                fullscreenOverlayParent = null
                fullscreenOverlayIndex = -1
                customView?.let { view ->
                    (activity?.window?.decorView as? FrameLayout)?.removeView(view)
                }
                customView = null
                _binding?.let {
                    it.urlBarView.visibility = View.VISIBLE
                    showToolbar()
                }
                (activity as? MainActivity)?.setFullscreen(false)
            }
        )
        webChromeClient = chromeClient
        webView.webChromeClient = chromeClient
        webView.settings.userAgentString = uaManager.getUa()
        viewModel.setUaMode(if (uaManager.isDesktop()) UaMode.DESKTOP else UaMode.MOBILE)

        videoBridge.attach(webView)
        videoBridge.onVideoActiveChanged = { active ->
            if (isAdded && _binding != null) {
                viewModel.setVideoActive(active)
                if (!active) {
                    binding.videoGestureOverlay.setActiveVideoBounds(null)
                }
                binding.videoGestureOverlay.visibility =
                    if (active) View.VISIBLE else View.GONE
            }
        }
        videoBridge.onVideoBoundsChanged = { bounds ->
            if (isAdded && _binding != null) {
                @Suppress("DEPRECATION")
                val contentScale = webView.scale
                binding.videoGestureOverlay.setActiveVideoBounds(bounds, contentScale)
            }
        }
        binding.videoGestureOverlay.attach(videoBridge)
        binding.videoGestureOverlay.onSingleTapConfirmed = { down, up ->
            val fullscreenView = customView
            if (fullscreenView != null) {
                fullscreenView.dispatchTouchEvent(down)
                fullscreenView.dispatchTouchEvent(up)
            } else {
                webView.dispatchNativeTap(down, up)
            }
        }

        // 活动视频的点击先由 overlay 延迟确认双击；确认单击后再回放给 WebView。
        webView.gestureHandler = { event ->
            if (binding.videoGestureOverlay.isGestureEnabled()) {
                binding.videoGestureOverlay.handleWebViewTouch(event)
            } else {
                false
            }
        }
    }

    private fun resetVideoGestureState() {
        _binding?.videoGestureOverlay?.resetGesture()
        _binding?.videoGestureOverlay?.setActiveVideoBounds(null)
        _binding?.videoGestureOverlay?.visibility = View.GONE
        viewModel.setVideoActive(false)
    }

    private fun recreateWebViewAfterRendererGone(didCrash: Boolean) {
        if (!isAdded || _binding == null) return
        val oldWebView = activeWebView ?: return
        val state = viewModel.uiState.value
        val urlToRestore = state.currentUrl
        viewModel.onReceivedError(
            if (didCrash) "WebView renderer crashed" else "WebView renderer exited",
            urlToRestore
        )

        videoBridge.detach()
        binding.videoGestureOverlay.detach()
        webViewManager.destroy(oldWebView)
        binding.webViewContainer.removeView(oldWebView)

        val replacement = BrowserWebView(requireContext())
        replacement.id = R.id.webView
        binding.webViewContainer.addView(
            replacement,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        activeWebView = replacement
        setupWebView()
        if (urlToRestore.isNotBlank() && !state.showStartup) {
            loadUrl(urlToRestore)
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
                    val errorBinding = binding.errorView
                    if (state.error != null) {
                        errorBinding.root.visibility = View.VISIBLE
                        errorBinding.errorMessage.text = state.error.message
                    } else {
                        errorBinding.root.visibility = View.GONE
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

    private fun hideToolbar() {
        toolbarHidden = true
        binding.urlBarView.animate()
            .translationY(-binding.urlBarView.height.toFloat())
            .setDuration(200)
            .start()
        viewModel.setToolbarVisible(false)
    }

    private fun showToolbar() {
        toolbarHidden = false
        binding.urlBarView.animate()
            .translationY(0f)
            .setDuration(200)
            .start()
        viewModel.setToolbarVisible(true)
    }

    fun loadUrl(url: String) {
        webViewManager.loadUrl(currentWebView, url)
    }

    fun canGoBack(): Boolean = currentWebView.canGoBack()

    fun goBack(): Boolean = webViewManager.goBack(currentWebView)

    fun onBackPressed(): Boolean {
        // 全屏优先退出，不能让返回键直接离开页面/Activity。
        if (webChromeClient?.hasCustomView == true) {
            webChromeClient?.hideCustomViewFromHost()
            return true
        }
        if (currentWebView.canGoBack()) {
            currentWebView.goBack()
            return true
        }
        if (!viewModel.uiState.value.showStartup) {
            // 启动页只是覆盖当前 WebView，旧页面的 JS 仍可能异步回调；
            // 先推进 generation，避免它重新激活视频手势层。
            videoBridge.beginNavigation()
            resetVideoGestureState()
            viewModel.showStartup()
            // 启动页覆盖 WebView 即可，不再 load about:blank，避免 onPageStarted
            // 把刚写入的 showStartup 状态改回 false。
            currentWebView.stopLoading()
            return true
        }
        return false
    }

    override fun onDestroyView() {
        webChromeClient?.hideCustomViewFromHost()
        webChromeClient = null
        customView = null
        binding.videoGestureOverlay.detach()
        videoBridge.detach()
        webViewManager.destroy(currentWebView)
        activeWebView = null
        _binding = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        videoBridge.close()
        super.onDestroy()
    }

    private companion object {
        const val ABOUT_BLANK = "about:blank"
    }
}
