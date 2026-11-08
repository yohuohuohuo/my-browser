package com.yohuohuohuo.browser

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
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
import com.yohuohuohuo.browser.ui.FullscreenVideoTouchHost
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
    private var fullscreenTouchHost: FullscreenVideoTouchHost? = null
    private var webChromeClient: BrowserWebChromeClient? = null
    private var toolbarHidden = false
    private var toolbarHiddenBeforeFullscreen: Boolean? = null
    private var statusBarInset = 0
    private var fullscreenOverlayParent: ViewGroup? = null
    private var fullscreenOverlayIndex = -1
    private val toolbarVisibilityHandler = Handler(Looper.getMainLooper())
    private var toolbarVisibilityRunnable: Runnable? = null
    private var webViewTouchActive = false
    private var webViewPaused = false
    private var currentDocumentUrl: String? = null
    private var savedBrowserState: Bundle? = null
    private var restoringPage = false

    private val currentWebView: BrowserWebView
        get() = activeWebView ?: binding.webView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBrowserBinding.inflate(inflater, container, false)
        activeWebView = binding.webView
        webViewPaused = false
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupWebView()
        restoreBrowserState(savedBrowserState ?: savedInstanceState?.getBundle(KEY_BROWSER_STATE))
        savedBrowserState = null
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
                if (webView === activeWebView) {
                    currentDocumentUrl = url
                    if (!viewModel.uiState.value.showStartup) videoBridge.pauseAllMedia()
                    cancelPendingToolbarVisibility()
                    webViewTouchActive = false
                    videoBridge.beginNavigation()
                    resetVideoGestureState()
                    if (url.equals(ABOUT_BLANK, ignoreCase = true)) {
                        viewModel.showStartup()
                    } else {
                        viewModel.onPageStarted(url)
                        resumeWebViewIfVisible()
                    }
                }
            },
            onPageFinishedCallback = { finishedWebView, url ->
                if (finishedWebView === activeWebView) {
                    val state = viewModel.uiState.value
                    if (url.equals(ABOUT_BLANK, ignoreCase = true)) {
                        if (state.currentUrl.isBlank()) viewModel.showStartup()
                    } else if (!state.showStartup && state.error == null &&
                        (url == currentDocumentUrl || url == state.currentUrl)
                    ) {
                        viewModel.onPageFinished(state.currentUrl, finishedWebView.title ?: "")
                        videoBridge.injectScript()
                        if (restoringPage) {
                            videoBridge.pauseAllMedia()
                            restoringPage = false
                        }
                    }
                }
            },
            onHistoryUpdatedCallback = { historyWebView, url ->
                val state = viewModel.uiState.value
                if (historyWebView === activeWebView && url == historyWebView.url &&
                    url.isNotBlank() && !url.equals(ABOUT_BLANK, ignoreCase = true) &&
                    !state.showStartup && state.error == null
                ) {
                    viewModel.onHistoryUpdated(url)
                }
            },
            onReceivedErrorCallback = { message, url ->
                if (webView === activeWebView && url == viewModel.uiState.value.currentUrl) {
                    viewModel.onReceivedError(message, url)
                    pauseHiddenPage()
                }
            },
            onSslErrorCallback = { url ->
                if (webView === activeWebView && url == viewModel.uiState.value.currentUrl) {
                    viewModel.onReceivedError("SSL certificate error", url)
                    pauseHiddenPage()
                }
            },
            onRenderProcessGoneCallback = { didCrash ->
                if (webView === activeWebView) recreateWebViewAfterRendererGone(didCrash)
            }
        )

        val chromeClient = BrowserWebChromeClient(
            onProgressChangedCallback = { progress ->
                if (webView === activeWebView) viewModel.onProgressChanged(progress)
            },
            onShowCustomViewCallback = show@{ view ->
                if (webView !== activeWebView) return@show
                cancelPendingToolbarVisibility()
                webViewTouchActive = false
                customView = view
                toolbarHiddenBeforeFullscreen = toolbarHidden
                val decor = activity?.window?.decorView as? FrameLayout
                (view.parent as? ViewGroup)?.removeView(view)
                val host = FullscreenVideoTouchHost(requireContext(), view)
                fullscreenTouchHost = host
                decor?.addView(
                    host,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
                val overlay = binding.videoGestureOverlay
                fullscreenOverlayParent = overlay.parent as? ViewGroup
                fullscreenOverlayIndex = fullscreenOverlayParent?.indexOfChild(overlay) ?: -1
                fullscreenOverlayParent?.removeView(overlay)
                host.addView(
                    overlay,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
                overlay.visibility = View.VISIBLE
                overlay.enterFullscreenTouchMode()
                host.gestureHandler = { event -> overlay.handleWebViewTouch(event) }
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
            onHideCustomViewCallback = hide@{
                if (webView !== activeWebView) return@hide
                cancelPendingToolbarVisibility()
                webViewTouchActive = false
                val overlay = _binding?.videoGestureOverlay
                fullscreenTouchHost?.gestureHandler = null
                fullscreenTouchHost?.cancelActiveTouch()
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
                fullscreenTouchHost?.let { host ->
                    (activity?.window?.decorView as? FrameLayout)?.removeView(host)
                    customView?.let(host::removeView)
                }
                fullscreenTouchHost = null
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
                    binding.videoGestureOverlay.resetGesture()
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
        binding.videoGestureOverlay.onGestureTakeover = {
            fullscreenTouchHost?.cancelActiveTouch() ?: webView.cancelActiveTouch()
        }
        // Keep the original page touch until a video gesture takes ownership.
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

        replaceWebView(oldWebView)
        if (urlToRestore.isNotBlank() && !state.showStartup) {
            loadUrl(urlToRestore)
        }
    }

    private fun replaceWebView(oldWebView: BrowserWebView) {
        webChromeClient?.hideCustomViewFromHost()
        resetVideoGestureState()
        currentDocumentUrl = null
        restoringPage = false
        videoBridge.detach()
        binding.videoGestureOverlay.detach()
        oldWebView.onTouchStateChanged = null
        webViewTouchActive = false
        binding.webViewContainer.removeView(oldWebView)
        webViewManager.destroy(oldWebView)

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
        webViewPaused = false
        setupWebView()
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
        val focusedView = activity?.currentFocus
        val windowToken = focusedView?.windowToken ?: binding.root.windowToken
        focusedView?.clearFocus()
        val inputMethod = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethod.hideSoftInputFromWindow(windowToken, 0)
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
        restoringPage = false
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && webViewPaused) {
            currentWebView.onResume()
            webViewPaused = false
        }
        if (!viewModel.uiState.value.showStartup) videoBridge.pauseAllMedia()
        webViewManager.loadUrl(currentWebView, url)
    }

    fun canGoBack(): Boolean = currentWebView.canGoBack()

    fun goBack(): Boolean {
        if (!currentWebView.canGoBack()) return false
        restoringPage = false
        videoBridge.pauseAllMedia()
        return webViewManager.goBack(currentWebView)
    }

    fun onBackPressed(): Boolean {
        // 全屏优先退出，不能让返回键直接离开页面/Activity。
        if (webChromeClient?.hasCustomView == true) {
            webChromeClient?.hideCustomViewFromHost()
            return true
        }
        if (currentWebView.canGoBack()) {
            goBack()
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
        webChromeClient?.hideCustomViewFromHost()
        binding.urlBarView.clearUrl()
        videoBridge.beginNavigation()
        resetVideoGestureState()
        // Home 是离开页面，不保留旧页面、子 frame 或媒体播放上下文。
        replaceWebView(currentWebView)
        viewModel.showStartup()
        showToolbar()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBundle(
            KEY_BROWSER_STATE,
            if (_binding != null) saveBrowserState() else savedBrowserState
        )
    }

    private fun saveBrowserState(): Bundle = Bundle().apply {
        val state = viewModel.uiState.value
        if (!state.showStartup && state.currentUrl.isNotBlank()) {
            putString(KEY_CURRENT_URL, state.currentUrl)
            val history = Bundle()
            currentWebView.saveState(history)
            putBundle(KEY_WEBVIEW_STATE, history)
        }
    }

    private fun restoreBrowserState(state: Bundle?) {
        if (state == null) return
        val url = state.getString(KEY_CURRENT_URL).orEmpty()
        if (url.isBlank()) {
            viewModel.showStartup()
            return
        }
        currentDocumentUrl = url
        restoringPage = true
        viewModel.onPageStarted(url)
        val restored = state.getBundle(KEY_WEBVIEW_STATE)?.let(currentWebView::restoreState)
        if (restored?.currentItem?.url != url) {
            // 无历史或保存时仍在导航中时，保留可恢复的历史并重载最新 URL。
            webViewManager.loadUrl(currentWebView, url)
        }
    }

    private fun pauseHiddenPage() {
        if (_binding == null || viewModel.uiState.value.showStartup) return
        videoBridge.pauseAllMedia()
        if (!webViewPaused) {
            currentWebView.onPause()
            webViewPaused = true
        }
    }

    private fun resumeWebViewIfVisible(fromOnResume: Boolean = false) {
        if (_binding == null || !webViewPaused) return
        val state = viewModel.uiState.value
        if ((fromOnResume || lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) &&
            !state.showStartup && state.error == null
        ) {
            currentWebView.onResume()
            webViewPaused = false
        }
    }

    override fun onPause() {
        pauseHiddenPage()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        resumeWebViewIfVisible(fromOnResume = true)
    }

    override fun onDestroyView() {
        savedBrowserState = saveBrowserState()
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
        const val KEY_BROWSER_STATE = "browserState"
        const val KEY_CURRENT_URL = "currentUrl"
        const val KEY_WEBVIEW_STATE = "webViewState"
        const val TOOLBAR_SCROLL_THRESHOLD = 10
        const val TOOLBAR_SCROLL_IDLE_DELAY_MS = 300L
    }
}
