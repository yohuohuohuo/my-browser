package com.yohuohuohuo.browser

import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.yohuohuohuo.browser.gesture.VideoOrientation
import com.yohuohuohuo.browser.ua.UaManager
import com.yohuohuohuo.browser.ui.BrowserWebView
import com.yohuohuohuo.browser.ui.BrowserLayoutMetrics
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
    private var toolbarHiddenBeforeFullscreen: Boolean? = null
    private var statusBarInset = 0
    private var fullscreenOverlayParent: ViewGroup? = null
    private var fullscreenOverlayIndex = -1
    private val toolbarVisibilityHandler = Handler(Looper.getMainLooper())
    private var toolbarVisibilityRunnable: Runnable? = null
    private var webViewTouchActive = false

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
            binding.startupView.clearInput()
            handleInput(query)
        }
        binding.urlBarView.onHomeButtonClicked = {
            showStartupPage()
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
            statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            binding.urlBarView.setTopPadding(statusBarInset)
            updateWebViewContentTopInset()
            insets
        }
        binding.urlBarView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateWebViewContentTopInset()
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
        webView.onTouchStateChanged = { active ->
            webViewTouchActive = active
            if (active) {
                cancelPendingToolbarVisibility()
            } else {
                reschedulePendingToolbarVisibility()
            }
        }
        webView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            val delta = scrollY - oldScrollY
            if (delta > TOOLBAR_SCROLL_THRESHOLD) {
                requestToolbarVisibility(hidden = true)
            } else if (delta < -TOOLBAR_SCROLL_THRESHOLD) {
                requestToolbarVisibility(hidden = false)
            }
        }
        webView.webViewClient = BrowserWebViewClient(
            onPageStartedCallback = { url ->
                cancelPendingToolbarVisibility()
                webViewTouchActive = false
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
                cancelPendingToolbarVisibility()
                webViewTouchActive = false
                customView = view
                toolbarHiddenBeforeFullscreen = toolbarHidden
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
                videoBridge.getActiveVideoDimensions { width, height ->
                    if (customView === view) {
                        val orientation = VideoOrientation.fromDimensions(width, height)
                        (activity as? MainActivity)?.setFullscreen(true, orientation)
                    }
                }
            },
            onHideCustomViewCallback = {
                cancelPendingToolbarVisibility()
                webViewTouchActive = false
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
                    if (toolbarHiddenBeforeFullscreen == true) {
                        hideToolbar()
                    } else {
                        showToolbar()
                    }
                }
                toolbarHiddenBeforeFullscreen = null
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
        cancelPendingToolbarVisibility()
        val oldWebView = activeWebView ?: return
        val state = viewModel.uiState.value
        val urlToRestore = state.currentUrl
        viewModel.onReceivedError(
            if (didCrash) "WebView renderer crashed" else "WebView renderer exited",
            urlToRestore
        )

        videoBridge.detach()
        binding.videoGestureOverlay.detach()
        oldWebView.onTouchStateChanged = null
        webViewTouchActive = false
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
                    binding.urlBarView.visibility =
                        if (state.showStartup) View.GONE else View.VISIBLE
                    if (state.showStartup) {
                        binding.urlBarView.clearUrl()
                        binding.startupView.focusInput()
                    } else {
                        binding.urlBarView.setUrl(state.currentUrl)
                    }
                    binding.loadingProgress.progress = state.loadingProgress.coerceIn(0, 100)
                    binding.loadingProgress.visibility =
                        if (state.isLoading) View.VISIBLE else View.GONE
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
        updateWebViewContentTopInset()
        binding.urlBarView.animate()
            .translationY(BrowserLayoutMetrics.toolbarHideTranslation(binding.urlBarView.height))
            .setDuration(200)
            .start()
        viewModel.setToolbarVisible(false)
    }

    private fun requestToolbarVisibility(hidden: Boolean) {
        if (hidden == toolbarHidden) {
            cancelPendingToolbarVisibility()
            return
        }

        toolbarVisibilityRunnable?.let(toolbarVisibilityHandler::removeCallbacks)
        val runnable = object : Runnable {
            override fun run() {
                if (toolbarVisibilityRunnable !== this) return
                if (webViewTouchActive) {
                    toolbarVisibilityHandler.postDelayed(this, TOOLBAR_SCROLL_IDLE_DELAY_MS)
                    return
                }
                toolbarVisibilityRunnable = null
                if (hidden && !toolbarHidden) {
                    hideToolbar()
                } else if (!hidden && toolbarHidden) {
                    showToolbar()
                }
            }
        }
        toolbarVisibilityRunnable = runnable
        toolbarVisibilityHandler.postDelayed(runnable, TOOLBAR_SCROLL_IDLE_DELAY_MS)
    }

    private fun reschedulePendingToolbarVisibility() {
        val runnable = toolbarVisibilityRunnable ?: return
        toolbarVisibilityHandler.removeCallbacks(runnable)
        toolbarVisibilityHandler.postDelayed(runnable, TOOLBAR_SCROLL_IDLE_DELAY_MS)
    }

    private fun cancelPendingToolbarVisibility() {
        toolbarVisibilityRunnable?.let(toolbarVisibilityHandler::removeCallbacks)
        toolbarVisibilityRunnable = null
    }

    private fun showToolbar() {
        toolbarHidden = false
        updateWebViewContentTopInset()
        binding.urlBarView.animate()
            .translationY(0f)
            .setDuration(200)
            .start()
        viewModel.setToolbarVisible(true)
    }

    private fun updateWebViewContentTopInset() {
        if (_binding == null) return
        val topInset = BrowserLayoutMetrics.contentTopInset(
            statusBarInset = statusBarInset,
            toolbarHeight = binding.urlBarView.height,
            toolbarHidden = toolbarHidden
        )
        val container = binding.webViewContainer
        val layoutParams = container.layoutParams as? FrameLayout.LayoutParams ?: return
        if (layoutParams.topMargin != topInset) {
            layoutParams.topMargin = topInset
            container.layoutParams = layoutParams
        }
        val progressLayoutParams = binding.loadingProgress.layoutParams as? FrameLayout.LayoutParams
            ?: return
        if (progressLayoutParams.topMargin != topInset) {
            progressLayoutParams.topMargin = topInset
            binding.loadingProgress.layoutParams = progressLayoutParams
        }
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
            showStartupPage()
            return true
        }
        return false
    }

    private fun showStartupPage() {
        cancelPendingToolbarVisibility()
        binding.urlBarView.clearUrl()
        // 启动页只是覆盖当前 WebView，旧页面的 JS 仍可能异步回调；
        // 先推进 generation，避免它重新激活视频手势层。
        videoBridge.beginNavigation()
        resetVideoGestureState()
        viewModel.showStartup()
        // 启动页覆盖 WebView 即可，不再 load about:blank，避免 onPageStarted
        // 把刚写入的 showStartup 状态改回 false。
        currentWebView.stopLoading()
        showToolbar()
    }

    override fun onDestroyView() {
        cancelPendingToolbarVisibility()
        webChromeClient?.hideCustomViewFromHost()
        webChromeClient = null
        customView = null
        activeWebView?.onTouchStateChanged = null
        webViewTouchActive = false
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
        const val TOOLBAR_SCROLL_THRESHOLD = 10
        const val TOOLBAR_SCROLL_IDLE_DELAY_MS = 300L
    }
}
