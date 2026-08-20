package com.yohuohuohuo.browser

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class UaMode { MOBILE, DESKTOP }

data class UiState(
    val currentUrl: String = "",
    val pageTitle: String = "",
    val loadingProgress: Int = 0,
    val isLoading: Boolean = false,
    val uaMode: UaMode = UaMode.MOBILE,
    val showStartup: Boolean = true,
    val toolbarVisible: Boolean = true,
    val videoActive: Boolean = false,
    val error: BrowserError? = null
)

data class BrowserError(
    val message: String,
    val failingUrl: String
)

class BrowserViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun onPageStarted(url: String) {
        _uiState.value = _uiState.value.copy(
            currentUrl = url,
            isLoading = true,
            loadingProgress = 0,
            error = null,
            showStartup = false
        )
    }

    fun onPageFinished(url: String, title: String) {
        _uiState.value = _uiState.value.copy(
            currentUrl = url,
            pageTitle = title,
            isLoading = false,
            loadingProgress = 100
        )
    }

    fun onProgressChanged(progress: Int) {
        _uiState.value = _uiState.value.copy(loadingProgress = progress)
    }

    fun onReceivedError(message: String, failingUrl: String) {
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            error = BrowserError(message, failingUrl)
        )
    }

    fun showStartup() {
        _uiState.value = _uiState.value.copy(showStartup = true, currentUrl = "", pageTitle = "")
    }

    fun setToolbarVisible(visible: Boolean) {
        _uiState.value = _uiState.value.copy(toolbarVisible = visible)
    }

    fun setVideoActive(active: Boolean) {
        _uiState.value = _uiState.value.copy(videoActive = active)
    }

    fun setUaMode(mode: UaMode) {
        _uiState.value = _uiState.value.copy(uaMode = mode)
    }
}
