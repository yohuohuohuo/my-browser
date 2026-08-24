package com.yohuohuohuo.browser

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BrowserViewModelTest {

    private lateinit var viewModel: BrowserViewModel

    @Before
    fun setUp() {
        viewModel = BrowserViewModel()
    }

    @Test
    fun `initial state has default values`() {
        val state = viewModel.uiState.value
        assertEquals("", state.currentUrl)
        assertEquals("", state.pageTitle)
        assertEquals(0, state.loadingProgress)
        assertFalse(state.isLoading)
        assertEquals(UaMode.MOBILE, state.uaMode)
        assertTrue(state.showStartup)
        assertTrue(state.toolbarVisible)
        assertFalse(state.videoActive)
        assertNull(state.error)
    }

    @Test
    fun `onPageStarted updates loading state and hides startup`() {
        viewModel.onPageStarted("https://example.com")

        val state = viewModel.uiState.value
        assertEquals("https://example.com", state.currentUrl)
        assertTrue(state.isLoading)
        assertEquals(0, state.loadingProgress)
        assertFalse(state.showStartup)
        assertNull(state.error)
    }

    @Test
    fun `onPageStarted clears previous error`() {
        viewModel.onReceivedError("Network error", "https://bad.com")
        assertNotNull(viewModel.uiState.value.error)

        viewModel.onPageStarted("https://good.com")

        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `onPageStarted clears active video state from the previous page`() {
        viewModel.setVideoActive(true)

        viewModel.onPageStarted("https://example.com/next")

        assertFalse(viewModel.uiState.value.videoActive)
    }

    @Test
    fun `onPageFinished updates title and progress`() {
        viewModel.onPageStarted("https://example.com")
        viewModel.onReceivedError("temporary error", "https://example.com")
        viewModel.onPageFinished("https://example.com", "Example Domain")

        val state = viewModel.uiState.value
        assertEquals("https://example.com", state.currentUrl)
        assertEquals("Example Domain", state.pageTitle)
        assertFalse(state.isLoading)
        assertEquals(100, state.loadingProgress)
        assertNull(state.error)
    }

    @Test
    fun `onProgressChanged updates loading progress`() {
        viewModel.onPageStarted("https://example.com")
        viewModel.onProgressChanged(50)

        assertEquals(50, viewModel.uiState.value.loadingProgress)
    }

    @Test
    fun `onReceivedError sets error and stops loading`() {
        viewModel.onPageStarted("https://bad.com")
        viewModel.onReceivedError("net::ERR_NAME_NOT_RESOLVED", "https://bad.com")

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.error)
        assertEquals("net::ERR_NAME_NOT_RESOLVED", state.error?.message)
        assertEquals("https://bad.com", state.error?.failingUrl)
    }

    @Test
    fun `showStartup resets url and title`() {
        viewModel.onPageStarted("https://example.com")
        viewModel.onPageFinished("https://example.com", "Title")
        viewModel.onReceivedError("Network error", "https://example.com")
        viewModel.setVideoActive(true)

        viewModel.showStartup()

        val state = viewModel.uiState.value
        assertTrue(state.showStartup)
        assertEquals("", state.currentUrl)
        assertEquals("", state.pageTitle)
        assertNull(state.error)
        assertFalse(state.videoActive)
    }

    @Test
    fun `setUaMode updates ua mode`() {
        viewModel.setUaMode(UaMode.DESKTOP)
        assertEquals(UaMode.DESKTOP, viewModel.uiState.value.uaMode)

        viewModel.setUaMode(UaMode.MOBILE)
        assertEquals(UaMode.MOBILE, viewModel.uiState.value.uaMode)
    }

    @Test
    fun `setToolbarVisible updates toolbar state`() {
        viewModel.setToolbarVisible(false)
        assertFalse(viewModel.uiState.value.toolbarVisible)

        viewModel.setToolbarVisible(true)
        assertTrue(viewModel.uiState.value.toolbarVisible)
    }

    @Test
    fun `setVideoActive updates video state`() {
        viewModel.setVideoActive(true)
        assertTrue(viewModel.uiState.value.videoActive)

        viewModel.setVideoActive(false)
        assertFalse(viewModel.uiState.value.videoActive)
    }

    @Test
    fun `state value updates synchronously through each lifecycle step`() {
        viewModel.onPageStarted("https://example.com")
        assertEquals("https://example.com", viewModel.uiState.value.currentUrl)
        assertTrue(viewModel.uiState.value.isLoading)

        viewModel.onProgressChanged(50)
        assertEquals(50, viewModel.uiState.value.loadingProgress)

        viewModel.onPageFinished("https://example.com", "Title")
        assertEquals("Title", viewModel.uiState.value.pageTitle)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(100, viewModel.uiState.value.loadingProgress)
    }
}
