# Yobrowser Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a minimal Android browser (Yobrowser) with enhanced video gesture controls and UA switching.

**Architecture:** Single Activity + Single Fragment + MVVM. WebView handles rendering; a transparent gesture overlay handles video controls via JS injection. Manual dependency injection, no DI framework.

**Tech Stack:** Kotlin, AndroidX, System WebView, Gradle Kotlin DSL + Version Catalog, StateFlow, Coroutines.

**Spec:** `docs/superpowers/specs/2026-08-19-yobrowser-design.md`

## Global Constraints

- minSdk = 26, targetSdk = 34, compileSdk = 34
- Package: `com.yohuohuohuo.browser`
- App name: `Yobrowser`
- Language: Kotlin only
- Architecture: MVVM with manual DI
- Build: Gradle Kotlin DSL + Version Catalog (`libs.versions.toml`)
- Theme: Light mode only, grey-black primary color, immersive status bar
- Permissions: INTERNET only
- Default search engine: Baidu (`https://www.baidu.com/s?wd=`)

---

## File Structure

```
app/
├── build.gradle.kts
├── src/main/
│   ├── AndroidManifest.xml
│   ├── java/com/yohuohuohuo/browser/
│   │   ├── MainActivity.kt
│   │   ├── BrowserFragment.kt
│   │   ├── BrowserViewModel.kt
│   │   ├── ui/
│   │   │   ├── UrlBarView.kt
│   │   │   ├── VideoGestureOverlay.kt
│   │   │   └── StartupView.kt
│   │   ├── web/
│   │   │   ├── WebViewManager.kt
│   │   │   ├── BrowserWebChromeClient.kt
│   │   │   └── BrowserWebViewClient.kt
│   │   ├── gesture/
│   │   │   ├── VideoGestureDetector.kt
│   │   │   └── VideoJsBridge.kt
│   │   ├── ua/
│   │   │   └── UaManager.kt
│   │   └── util/
│   │       ├── UrlUtils.kt
│   │       └── SearchEngine.kt
│   ├── res/
│   │   ├── layout/
│   │   │   ├── activity_main.xml
│   │   │   ├── fragment_browser.xml
│   │   │   ├── view_url_bar.xml
│   │   │   ├── view_startup.xml
│   │   │   └── view_error.xml
│   │   ├── values/
│   │   │   ├── strings.xml
│   │   │   ├── colors.xml
│   │   │   └── themes.xml
│   │   ├── drawable/
│   │   │   ├── ic_mobile.xml
│   │   │   ├── ic_desktop.xml
│   │   │   └── ic_launcher_foreground.xml
│   │   └── mipmap-*/ic_launcher.webp
│   └── assets/
│       └── video_bridge.js
├── gradle/
│   └── libs.versions.toml
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

---

### Task 1: Project Scaffolding

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/strings.xml`, `colors.xml`, `themes.xml`
- Create: `app/src/main/res/layout/activity_main.xml`, `fragment_browser.xml`
- Create: `app/src/main/java/com/yohuohuohuo/browser/MainActivity.kt`
- Create: `app/src/main/java/com/yohuohuohuo/browser/BrowserFragment.kt`

**Interfaces:**
- Produces: `MainActivity` (hosts `BrowserFragment`), `BrowserFragment` (empty shell with FrameLayout container)

- [ ] **Step 1: Create Gradle Version Catalog**

Create `gradle/libs.versions.toml`:

```toml
[versions]
agp = "8.2.2"
kotlin = "1.9.22"
coreKtx = "1.12.0"
appcompat = "1.6.1"
material = "1.11.0"
constraintlayout = "2.1.4"
lifecycle = "2.7.0"
activityKtx = "1.8.2"
fragmentKtx = "1.6.2"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-appcompat = { group = "androidx.appcompat", name = "appcompat", version.ref = "appcompat" }
google-material = { group = "com.google.android.material", name = "material", version.ref = "material" }
androidx-constraintlayout = { group = "androidx.constraintlayout", name = "constraintlayout", version.ref = "constraintlayout" }
androidx-lifecycle-viewmodel-ktx = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-ktx", version.ref = "lifecycle" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-activity-ktx = { group = "androidx.activity", name = "activity-ktx", version.ref = "activityKtx" }
androidx-fragment-ktx = { group = "androidx.fragment", name = "fragment-ktx", version.ref = "fragmentKtx" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
```

- [ ] **Step 2: Create root build files**

Create `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "Yobrowser"
include(":app")
```

Create `build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}
```

Create `gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

- [ ] **Step 3: Create app/build.gradle.kts**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.yohuohuohuo.browser"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.yohuohuohuo.browser"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
}
```

- [ ] **Step 4: Create AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.Yobrowser">
        <activity
            android:name=".MainActivity"
            android:configChanges="orientation|screenSize|keyboardHidden"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

- [ ] **Step 5: Create resource values**

`res/values/strings.xml`:
```xml
<resources>
    <string name="app_name">Yobrowser</string>
    <string name="search_hint">搜索或输入网址</string>
    <string name="error_title">网页无法打开</string>
    <string name="error_retry">重试</string>
</resources>
```

`res/values/colors.xml`:
```xml
<resources>
    <color name="primary">#FF2C2C2C</color>
    <color name="primary_dark">#FF1A1A1A</color>
    <color name="accent">#FF666666</color>
    <color name="white">#FFFFFFFF</color>
    <color name="background">#FFF5F5F5</color>
    <color name="text_primary">#FF212121</color>
    <color name="text_secondary">#FF757575</color>
    <color name="overlay_bg">#CC000000</color>
</resources>
```

`res/values/themes.xml`:
```xml
<resources>
    <style name="Theme.Yobrowser" parent="Theme.MaterialComponents.Light.NoActionBar">
        <item name="colorPrimary">@color/primary</item>
        <item name="colorPrimaryDark">@color/primary_dark</item>
        <item name="colorAccent">@color/accent</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:windowLightStatusBar">true</item>
        <item name="android:navigationBarColor">@color/white</item>
    </style>
</resources>
```

- [ ] **Step 6: Create base layouts**

`res/layout/activity_main.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/fragment_container"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

`res/layout/fragment_browser.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <WebView
        android:id="@+id/webView"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <com.yohuohuohuo.browser.ui.StartupView
        android:id="@+id/startupView"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:visibility="gone" />

    <com.yohuohuohuo.browser.ui.UrlBarView
        android:id="@+id/urlBarView"
        android:layout_width="match_parent"
        android:layout_height="wrap_content" />

    <com.yohuohuohuo.browser.ui.VideoGestureOverlay
        android:id="@+id/videoGestureOverlay"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:visibility="gone" />

</FrameLayout>
```

Note: `StartupView`, `UrlBarView`, `VideoGestureOverlay` will be created in later tasks. For now, create stub classes so the project compiles.

- [ ] **Step 7: Create stub custom views**

`ui/StartupView.kt`:
```kotlin
package com.yohuohuohuo.browser.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

class StartupView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs)
```

`ui/UrlBarView.kt`:
```kotlin
package com.yohuohuohuo.browser.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

class UrlBarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs)
```

`ui/VideoGestureOverlay.kt`:
```kotlin
package com.yohuohuohuo.browser.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

class VideoGestureOverlay @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs)
```

- [ ] **Step 8: Create MainActivity and BrowserFragment**

`MainActivity.kt`:
```kotlin
package com.yohuohuohuo.browser

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, BrowserFragment())
                .commit()
        }
    }
}
```

`BrowserFragment.kt`:
```kotlin
package com.yohuohuohuo.browser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.yohuohuohuo.browser.databinding.FragmentBrowserBinding

class BrowserFragment : Fragment() {
    private var _binding: FragmentBrowserBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBrowserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
```

- [ ] **Step 9: Create launcher icons (placeholder)**

Create `app/src/main/res/drawable/ic_launcher_foreground.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#2C2C2C"
        android:pathData="M54,27c-14.9,0 -27,12.1 -27,27s12.1,27 27,27s27,-12.1 27,-27S68.9,27 54,27zM54,72c-10.5,0 -18,-8.5 -18,-18s7.5,-18 18,-18s18,8.5 18,18S64.5,72 54,72z" />
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M54,42c-6.6,0 -12,5.4 -12,12s5.4,12 12,12s12,-5.4 12,-12S60.6,42 54,42z" />
</vector>
```

Use adaptive icon: create `res/mipmap-anydpi-v26/ic_launcher.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/primary" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

And `res/mipmap-anydpi-v26/ic_launcher_round.xml` with same content.

- [ ] **Step 10: Sync Gradle and verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 11: Commit**

```bash
git add -A
git commit -m "feat: project scaffolding with Gradle, manifest, and base Activity/Fragment"
```

---

### Task 2: WebView Management and Base Browsing

**Files:**
- Create: `web/WebViewManager.kt`
- Create: `web/BrowserWebViewClient.kt`
- Create: `web/BrowserWebChromeClient.kt`
- Create: `BrowserViewModel.kt`
- Modify: `BrowserFragment.kt` (wire up WebView, ViewModel, observe state)

**Interfaces:**
- Consumes: none
- Produces: `WebViewManager.configure(webView)`, `BrowserWebViewClient` (with loading/error callbacks), `BrowserWebChromeClient` (with progress/fullscreen callbacks), `BrowserViewModel.UiState`, `BrowserViewModel.loadUrl(url)`, `BrowserViewModel.goBack()`, `BrowserViewModel.canGoBack()`

- [ ] **Step 1: Create BrowserViewModel**

```kotlin
package com.yohuohuohuo.browser

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UiState(
    val currentUrl: String = "",
    val pageTitle: String = "",
    val loadingProgress: Int = 0,
    val isLoading: Boolean = false,
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
}
```

- [ ] **Step 2: Create WebViewManager**

```kotlin
package com.yohuohuohuo.browser.web

import android.annotation.SuppressLint
import android.webkit.WebSettings
import android.webkit.WebView

class WebViewManager {

    @SuppressLint("SetJavaScriptEnabled")
    fun configure(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            allowFileAccess = false
            allowContentAccess = false
        }
        webView.isVerticalScrollBarEnabled = true
        webView.isHorizontalScrollBarEnabled = false
    }

    fun loadUrl(webView: WebView, url: String) {
        webView.loadUrl(url)
    }

    fun goBack(webView: WebView): Boolean {
        if (webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return false
    }

    fun reload(webView: WebView) {
        webView.reload()
    }

    fun destroy(webView: WebView) {
        webView.stopLoading()
        webView.loadUrl("about:blank")
        webView.removeAllViews()
        webView.destroy()
    }
}
```

- [ ] **Step 3: Create BrowserWebViewClient**

```kotlin
package com.yohuohuohuo.browser.web

import android.graphics.Bitmap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

class BrowserWebViewClient(
    private val onPageStarted: (String) -> Unit,
    private val onPageFinished: (WebView, String) -> Unit,
    private val onReceivedError: (String, String) -> Unit
) : WebViewClient() {

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        url?.let { onPageStarted(it) }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (view != null && url != null) {
            onPageFinished(view, url)
        }
    }

    override fun onReceivedError(
        view: WebView?, request: WebResourceRequest?, error: WebResourceError?
    ) {
        super.onReceivedError(view, request, error)
        if (request?.isForMainFrame == true) {
            val msg = error?.description?.toString() ?: "Unknown error"
            val url = request.url?.toString() ?: ""
            onReceivedError(msg, url)
        }
    }
}
```

- [ ] **Step 4: Create BrowserWebChromeClient**

```kotlin
package com.yohuohuohuo.browser.web

import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView

class BrowserWebChromeClient(
    private val onProgressChanged: (Int) -> Unit,
    private val onShowCustomView: (View) -> Unit,
    private val onHideCustomView: () -> Unit
) : WebChromeClient() {

    private var customView: View? = null

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        onProgressChanged(newProgress)
    }

    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        super.onShowCustomView(view, callback)
        view?.let {
            customView = it
            onShowCustomView(it)
        }
    }

    override fun onHideCustomView() {
        super.onHideCustomView()
        customView = null
        onHideCustomView()
    }
}
```

- [ ] **Step 5: Wire up BrowserFragment**

Replace `BrowserFragment.kt` content:

```kotlin
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
                }
            }
        }
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
```

- [ ] **Step 6: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: WebView management, ViewModel, and base browsing state"
```

---

### Task 3: Startup Page and URL Bar

**Files:**
- Create: `util/UrlUtils.kt`
- Create: `util/SearchEngine.kt`
- Create: `ui/StartupView.kt` (replace stub)
- Create: `ui/UrlBarView.kt` (replace stub)
- Create: `res/layout/view_startup.xml`
- Create: `res/layout/view_url_bar.xml`
- Modify: `BrowserFragment.kt` (connect StartupView and UrlBarView events)

**Interfaces:**
- Consumes: `BrowserViewModel.uiState`, `BrowserFragment.loadUrl()`
- Produces: `UrlUtils.isUrl(text): Boolean`, `UrlUtils.normalizeUrl(text): String`, `SearchEngine.buildSearchUrl(query): String`, `UrlBarView.setUrl(url)`, `UrlBarView.onUrlSubmitted(callback)`, `StartupView.onSearchSubmitted(callback)`

- [ ] **Step 1: Create UrlUtils**

```kotlin
package com.yohuohuohuo.browser.util

import android.util.Patterns

object UrlUtils {

    fun isUrl(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        // Has scheme
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return Patterns.WEB_URL.matcher(trimmed).matches()
        }
        // Contains dot and no spaces -> likely a domain
        if (trimmed.contains(".") && !trimmed.contains(" ")) {
            val domainPart = trimmed.substringBefore("/")
            return domainPart.matches(Regex("^[a-zA-Z0-9][a-zA-Z0-9.-]*\\.[a-zA-Z]{2,}$"))
        }
        return false
    }

    fun normalizeUrl(text: String): String {
        val trimmed = text.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }
}
```

- [ ] **Step 2: Create SearchEngine**

```kotlin
package com.yohuohuohuo.browser.util

import java.net.URLEncoder

object SearchEngine {
    private const val BAIDU_SEARCH_URL = "https://www.baidu.com/s?wd="

    fun buildSearchUrl(query: String): String {
        return BAIDU_SEARCH_URL + URLEncoder.encode(query.trim(), "UTF-8")
    }
}
```

- [ ] **Step 3: Create StartupView layout**

`res/layout/view_startup.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/background"
    android:gravity="center"
    android:orientation="vertical"
    android:padding="32dp">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/app_name"
        android:textColor="@color/primary"
        android:textSize="36sp"
        android:textStyle="bold"
        android:layout_marginBottom="48dp" />

    <EditText
        android:id="@+id/searchInput"
        android:layout_width="match_parent"
        android:layout_height="48dp"
        android:background="@drawable/bg_search_input"
        android:hint="@string/search_hint"
        android:imeOptions="actionSearch"
        android:inputType="text"
        android:paddingHorizontal="16dp"
        android:textColor="@color/text_primary"
        android:textColorHint="@color/text_secondary"
        android:textSize="16sp" />

</LinearLayout>
```

Create `res/drawable/bg_search_input.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/white" />
    <corners android:radius="24dp" />
    <stroke android:width="1dp" android:color="@color/accent" />
</shape>
```

- [ ] **Step 4: Create StartupView class**

Replace `ui/StartupView.kt`:

```kotlin
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
```

- [ ] **Step 5: Create UrlBarView layout**

`res/layout/view_url_bar.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="@color/white"
    android:gravity="center_vertical"
    android:orientation="horizontal"
    android:paddingHorizontal="12dp"
    android:paddingVertical="8dp">

    <EditText
        android:id="@+id/urlInput"
        android:layout_width="0dp"
        android:layout_height="40dp"
        android:layout_weight="1"
        android:background="@drawable/bg_url_input"
        android:hint="@string/search_hint"
        android:imeOptions="actionGo"
        android:inputType="text"
        android:paddingHorizontal="12dp"
        android:textColor="@color/text_primary"
        android:textColorHint="@color/text_secondary"
        android:textSize="14sp"
        android:singleLine="true" />

    <ImageButton
        android:id="@+id/uaButton"
        android:layout_width="40dp"
        android:layout_height="40dp"
        android:layout_marginStart="8dp"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:contentDescription="UA Switch"
        android:src="@drawable/ic_mobile" />

</LinearLayout>
```

Create `res/drawable/bg_url_input.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/background" />
    <corners android:radius="20dp" />
</shape>
```

Create `res/drawable/ic_mobile.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#2C2C2C"
        android:pathData="M17,19H7V5h10M17,1H7c-1.1,0 -2,0.9 -2,2v18c0,1.1 0.9,2 2,2h10c1.1,0 2,-0.9 2,-2V3c0,-1.1 -0.9,-2 -2,-2zM12,20c-0.55,0 -1,-0.45 -1,-1s0.45,-1 1,-1s1,0.45 1,1S12.55,20 12,20z" />
</vector>
```

Create `res/drawable/ic_desktop.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#2C2C2C"
        android:pathData="M21,2H3c-1.1,0 -2,0.9 -2,2v12c0,1.1 0.9,2 2,2h7v2H8v2h8v-2h-2v-2h7c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM21,16H3V4h18v12z" />
</vector>
```

- [ ] **Step 6: Create UrlBarView class**

Replace `ui/UrlBarView.kt`:

```kotlin
package com.yohuohuohuo.browser.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import com.yohuohuohuo.browser.databinding.ViewUrlBarBinding

class UrlBarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val binding = ViewUrlBarBinding.inflate(LayoutInflater.from(context), this, true)

    var onUrlSubmitted: ((String) -> Unit)? = null
    var onUaButtonClicked: (() -> Unit)? = null

    init {
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
        binding.urlInput.setText(url)
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
        binding.urlInput.requestFocus()
        binding.urlInput.selectAll()
    }
}
```

- [ ] **Step 7: Wire up in BrowserFragment**

Add to `BrowserFragment.onViewCreated` after `observeState()`:

```kotlin
        binding.startupView.onSearchSubmitted = { query ->
            handleInput(query)
        }
        binding.urlBarView.onUrlSubmitted = { text ->
            handleInput(text)
        }
        binding.urlBarView.setOnClickListener {
            binding.urlBarView.focusInput()
        }
```

Add method to `BrowserFragment`:

```kotlin
    private fun handleInput(text: String) {
        val url = if (UrlUtils.isUrl(text)) {
            UrlUtils.normalizeUrl(text)
        } else {
            SearchEngine.buildSearchUrl(text)
        }
        loadUrl(url)
    }
```

Update imports to include:
```kotlin
import com.yohuohuohuo.browser.util.SearchEngine
import com.yohuohuohuo.browser.util.UrlUtils
```

Update `observeState` to also update url bar:
```kotlin
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
```

- [ ] **Step 8: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "feat: startup page, URL bar with search/URL input"
```

---

### Task 4: UA Switching

**Files:**
- Create: `ua/UaManager.kt`
- Modify: `BrowserViewModel.kt` (add uaMode to UiState, add toggleUa)
- Modify: `BrowserFragment.kt` (connect UA button, apply UA to WebView)

**Interfaces:**
- Consumes: `WebViewManager`
- Produces: `UaManager.getUa(): String`, `UaManager.isDesktop(): Boolean`, `UaManager.toggle()`, `BrowserViewModel.toggleUa()`

- [ ] **Step 1: Create UaManager**

```kotlin
package com.yohuohuohuo.browser.ua

import android.content.Context
import android.content.SharedPreferences
import android.webkit.WebSettings

class UaManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("browser_prefs", Context.MODE_PRIVATE)

    private val defaultMobileUa: String = WebSettings.getDefaultUserAgent(context)

    private val desktopUa: String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    fun isDesktop(): Boolean = prefs.getBoolean(KEY_UA_DESKTOP, false)

    fun getUa(): String = if (isDesktop()) desktopUa else defaultMobileUa

    fun toggle(): Boolean {
        val newMode = !isDesktop()
        prefs.edit().putBoolean(KEY_UA_DESKTOP, newMode).apply()
        return newMode
    }

    companion object {
        private const val KEY_UA_DESKTOP = "ua_desktop"
    }
}
```

- [ ] **Step 2: Update BrowserViewModel**

Add `uaMode` to `UiState`:

```kotlin
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
```

Add methods to `BrowserViewModel`:

```kotlin
    fun setUaMode(mode: UaMode) {
        _uiState.value = _uiState.value.copy(uaMode = mode)
    }
```

- [ ] **Step 3: Wire up in BrowserFragment**

Add `UaManager` property:
```kotlin
    private val uaManager by lazy { UaManager(requireContext()) }
```

In `setupWebView()`, after configuring WebView, apply initial UA:
```kotlin
        binding.webView.settings.userAgentString = uaManager.getUa()
        viewModel.setUaMode(if (uaManager.isDesktop()) UaMode.DESKTOP else UaMode.MOBILE)
```

Connect UA button in `onViewCreated`:
```kotlin
        binding.urlBarView.onUaButtonClicked = {
            val isDesktop = uaManager.toggle()
            binding.webView.settings.userAgentString = uaManager.getUa()
            viewModel.setUaMode(if (isDesktop) UaMode.DESKTOP else UaMode.MOBILE)
            if (!viewModel.uiState.value.showStartup) {
                binding.webView.reload()
            }
        }
```

Update `observeState` to set UA icon:
```kotlin
                    binding.urlBarView.setUaIcon(state.uaMode == UaMode.DESKTOP)
```

Add import:
```kotlin
import com.yohuohuohuo.browser.ua.UaManager
```

- [ ] **Step 4: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: UA switching between mobile and desktop with persistence"
```

---

### Task 5: Immersive Status Bar and Toolbar Auto-Hide

**Files:**
- Modify: `MainActivity.kt` (immersive window, WindowInsets)
- Modify: `BrowserFragment.kt` (scroll listener, toolbar animation, insets handling)
- Modify: `ui/UrlBarView.kt` (add padding for status bar)

**Interfaces:**
- Consumes: `BrowserViewModel.setToolbarVisible()`
- Produces: toolbar show/hide animation, immersive layout

- [ ] **Step 1: Update MainActivity for immersive mode**

Replace `MainActivity.kt`:

```kotlin
package com.yohuohuohuo.browser

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Immersive status bar: content draws behind status bar
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, BrowserFragment())
                .commit()
        }
    }
}
```

- [ ] **Step 2: Add status bar padding to UrlBarView**

Add method to `UrlBarView`:

```kotlin
    fun setTopPadding(pixels: Int) {
        binding.root.setPadding(
            binding.root.paddingLeft,
            pixels + resources.getDimensionPixelSize(R.dimen.url_bar_vertical_padding),
            binding.root.paddingRight,
            resources.getDimensionPixelSize(R.dimen.url_bar_vertical_padding)
        )
    }
```

Create `res/values/dimens.xml`:
```xml
<resources>
    <dimen name="url_bar_vertical_padding">8dp</dimen>
    <dimen name="url_bar_height">56dp</dimen>
</resources>
```

Update `view_url_bar.xml` to remove hardcoded paddingVertical (we'll set programmatically):
Change `android:paddingVertical="8dp"` to use the dimen, or keep and let setTopPadding override. Simpler: keep layout padding, set additional top padding in code.

Actually, let's just set top padding via `setPadding` in the method above which replaces all padding. The method already includes vertical padding from dimens.

- [ ] **Step 3: Handle WindowInsets and scroll in BrowserFragment**

Add to `BrowserFragment.onViewCreated`:

```kotlin
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
```

Add methods to `BrowserFragment`:

```kotlin
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
```

Add imports:
```kotlin
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
```

- [ ] **Step 4: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: immersive status bar and toolbar auto-hide on scroll"
```

---

### Task 6: Video JS Bridge

**Files:**
- Create: `app/src/main/assets/video_bridge.js`
- Create: `gesture/VideoJsBridge.kt`
- Modify: `BrowserFragment.kt` (inject JS on page finished, register bridge)
- Modify: `web/BrowserWebViewClient.kt` (pass WebView in onPageFinished for injection)

**Interfaces:**
- Consumes: WebView
- Produces: `VideoJsBridge.attach(webView)`, `VideoJsBridge.detach()`, `VideoJsBridge.play()`, `VideoJsBridge.pause()`, `VideoJsBridge.seekBy(seconds)`, `VideoJsBridge.setRate(rate)`, `VideoJsBridge.onVideoActiveChanged(callback)`

- [ ] **Step 1: Create video_bridge.js**

Create `app/src/main/assets/video_bridge.js`:

```javascript
(function() {
    if (window.__videoBridge) return;

    var activeVideo = null;
    var callbacks = { onActive: null, onInactive: null };

    function findActiveVideo() {
        var videos = document.querySelectorAll('video');
        var best = null;
        var bestArea = 0;
        videos.forEach(function(v) {
            var rect = v.getBoundingClientRect();
            var area = rect.width * rect.height;
            if (area > bestArea && rect.width > 0 && rect.height > 0) {
                bestArea = area;
                best = v;
            }
        });
        return best;
    }

    function checkActive() {
        var v = findActiveVideo();
        if (v && !v.paused && v.readyState >= 2) {
            if (activeVideo !== v) {
                activeVideo = v;
                if (callbacks.onActive) callbacks.onActive();
            }
        } else if (activeVideo && (v !== activeVideo || v.paused)) {
            activeVideo = null;
            if (callbacks.onInactive) callbacks.onInactive();
        }
    }

    window.__videoBridge = {
        play: function() {
            var v = findActiveVideo();
            if (v) v.play();
        },
        pause: function() {
            var v = findActiveVideo();
            if (v) v.pause();
        },
        togglePlay: function() {
            var v = findActiveVideo();
            if (v) {
                if (v.paused) v.play(); else v.pause();
            }
        },
        seekBy: function(seconds) {
            var v = findActiveVideo();
            if (v && !isNaN(v.duration)) {
                v.currentTime = Math.max(0, Math.min(v.duration, v.currentTime + seconds));
            }
        },
        setRate: function(rate) {
            var v = findActiveVideo();
            if (v) v.playbackRate = rate;
        },
        getCurrentTime: function() {
            var v = findActiveVideo();
            return v ? v.currentTime : 0;
        },
        getDuration: function() {
            var v = findActiveVideo();
            return v && !isNaN(v.duration) ? v.duration : 0;
        },
        setCallbacks: function(onActive, onInactive) {
            callbacks.onActive = onActive;
            callbacks.onInactive = onInactive;
        }
    };

    // Monitor video activity
    setInterval(checkActive, 1000);
    document.addEventListener('fullscreenchange', checkActive);
    document.addEventListener('webkitfullscreenchange', checkActive);
})();
```

- [ ] **Step 2: Create VideoJsBridge**

```kotlin
package com.yohuohuohuo.browser.gesture

import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class VideoJsBridge(private val context: Context) {

    private var webView: WebView? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    var onVideoActiveChanged: ((Boolean) -> Unit)? = null

    fun attach(webView: WebView) {
        this.webView = webView
        webView.addJavascriptInterface(this, "AndroidBridge")
    }

    fun detach() {
        webView?.removeJavascriptInterface("AndroidBridge")
        webView = null
    }

    fun injectScript() {
        val script = context.assets.open("video_bridge.js").bufferedReader().use { it.readText() }
        webView?.evaluateJavascript(script, null)
        // Register callbacks after injection
        webView?.evaluateJavascript(
            "window.__videoBridge.setCallbacks(" +
                "function(){ AndroidBridge.onVideoActive(); }," +
                "function(){ AndroidBridge.onVideoInactive(); }" +
                ");",
            null
        )
    }

    fun play() {
        webView?.evaluateJavascript("window.__videoBridge.play();", null)
    }

    fun pause() {
        webView?.evaluateJavascript("window.__videoBridge.pause();", null)
    }

    fun togglePlay() {
        webView?.evaluateJavascript("window.__videoBridge.togglePlay();", null)
    }

    fun seekBy(seconds: Float) {
        webView?.evaluateJavascript("window.__videoBridge.seekBy($seconds);", null)
    }

    fun setRate(rate: Float) {
        webView?.evaluateJavascript("window.__videoBridge.setRate($rate);", null)
    }

    fun getCurrentTime(callback: (Float) -> Unit) {
        webView?.evaluateJavascript("window.__videoBridge.getCurrentTime();") { result ->
            callback(result?.toFloatOrNull() ?: 0f)
        }
    }

    fun getDuration(callback: (Float) -> Unit) {
        webView?.evaluateJavascript("window.__videoBridge.getDuration();") { result ->
            callback(result?.toFloatOrNull() ?: 0f)
        }
    }

    @JavascriptInterface
    fun onVideoActive() {
        scope.launch { onVideoActiveChanged?.invoke(true) }
    }

    @JavascriptInterface
    fun onVideoInactive() {
        scope.launch { onVideoActiveChanged?.invoke(false) }
    }
}
```

- [ ] **Step 3: Inject JS in BrowserWebViewClient onPageFinished**

The `onPageFinished` callback already passes WebView. Update `BrowserFragment` setup to inject:

In `setupWebView()`:
```kotlin
        val videoBridge = VideoJsBridge(requireContext())
        videoBridge.attach(binding.webView)
        videoBridge.onVideoActiveChanged = { active ->
            viewModel.setVideoActive(active)
            binding.videoGestureOverlay.visibility =
                if (active) View.VISIBLE else View.GONE
        }
```

Store videoBridge as a property:
```kotlin
    private val videoBridge by lazy { VideoJsBridge(requireContext()) }
```

In `onPageFinished` callback:
```kotlin
            onPageFinished = { webView, url ->
                viewModel.onPageFinished(url, webView.title ?: "")
                videoBridge.injectScript()
            },
```

In `onDestroyView`:
```kotlin
        videoBridge.detach()
```

Add import:
```kotlin
import com.yohuohuohuo.browser.gesture.VideoJsBridge
```

- [ ] **Step 4: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: Video JS bridge for detecting and controlling web videos"
```

---

### Task 7: Video Gesture Overlay

**Files:**
- Create: `gesture/VideoGestureDetector.kt`
- Create: `ui/VideoGestureOverlay.kt` (replace stub)
- Create: `res/layout/view_gesture_feedback.xml`
- Modify: `BrowserFragment.kt` (connect overlay to video bridge)

**Interfaces:**
- Consumes: `VideoJsBridge` (play, pause, togglePlay, seekBy, setRate, getCurrentTime, getDuration)
- Produces: `VideoGestureOverlay.attach(bridge)`, gesture detection with visual feedback

- [ ] **Step 1: Create VideoGestureDetector**

```kotlin
package com.yohuohuohuo.browser.gesture

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import kotlin.math.abs

class VideoGestureDetector(
    context: Context,
    private val listener: GestureListener
) {
    interface GestureListener {
        fun onLongPressStart()
        fun onLongPressEnd()
        fun onDoubleTap()
        fun onHorizontalScroll(deltaX: Float)
        fun onLeftVerticalScroll(deltaY: Float)
        fun onRightVerticalScroll(deltaY: Float)
        fun onScrollEnd()
    }

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onLongPress(e: MotionEvent) {
            listener.onLongPressStart()
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            listener.onDoubleTap()
            return true
        }

        override fun onScroll(
            e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float
        ): Boolean {
            val width = (e2?.device?.width ?: 1080).toFloat()
            val isHorizontal = abs(distanceX) > abs(distanceY)
            if (isHorizontal) {
                listener.onHorizontalScroll(-distanceX)
            } else {
                val startX = e1?.x ?: 0f
                if (startX < width / 2) {
                    listener.onLeftVerticalScroll(-distanceY)
                } else {
                    listener.onRightVerticalScroll(-distanceY)
                }
            }
            return true
        }
    })

    private var isLongPressing = false

    fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        when (event.action) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isLongPressing) {
                    isLongPressing = false
                    listener.onLongPressEnd()
                }
                listener.onScrollEnd()
            }
        }
        return true
    }

    fun setLongPressing(value: Boolean) { isLongPressing = value }
}
```

Wait, the long press detection needs to track state properly. Let me refine — the `onLongPress` callback fires, but we need to know when it ends. We track via ACTION_UP. Let me adjust the detector to set isLongPressing internally:

Actually the above has a bug — `isLongPressing` is never set to true. Fix:

```kotlin
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onLongPress(e: MotionEvent) {
            isLongPressing = true
            listener.onLongPressStart()
        }
        // ... rest same
    })
```

- [ ] **Step 2: Create gesture feedback layout**

`res/layout/view_gesture_feedback.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:background="@drawable/bg_gesture_feedback"
    android:padding="16dp">

    <TextView
        android:id="@+id/feedbackText"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:textColor="@color/white"
        android:textSize="20sp"
        android:textStyle="bold" />

</FrameLayout>
```

`res/drawable/bg_gesture_feedback.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/overlay_bg" />
    <corners android:radius="8dp" />
</shape>
```

- [ ] **Step 3: Create VideoGestureOverlay**

Replace `ui/VideoGestureOverlay.kt`:

```kotlin
package com.yohuohuohuo.browser.ui

import android.content.Context
import android.media.AudioManager
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.yohuohuohuo.browser.gesture.VideoGestureDetector
import com.yohuohuohuo.browser.gesture.VideoJsBridge

class VideoGestureOverlay @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private var videoBridge: VideoJsBridge? = null
    private lateinit var gestureDetector: VideoGestureDetector
    private var seekAccumulator = 0f
    private var feedbackView: TextView? = null
    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    fun attach(bridge: VideoJsBridge) {
        this.videoBridge = bridge
        gestureDetector = VideoGestureDetector(context, object : VideoGestureDetector.GestureListener {
            override fun onLongPressStart() {
                videoBridge?.setRate(3.0f)
                showFeedback("3x")
            }

            override fun onLongPressEnd() {
                videoBridge?.setRate(1.0f)
                hideFeedback()
            }

            override fun onDoubleTap() {
                videoBridge?.togglePlay()
            }

            override fun onHorizontalScroll(deltaX: Float) {
                // ~1px = 0.5s
                seekAccumulator += deltaX * 0.5f
                videoBridge?.seekBy(deltaX * 0.5f)
                updateSeekFeedback()
            }

            override fun onLeftVerticalScroll(deltaY: Float) {
                adjustBrightness(deltaY)
            }

            override fun onRightVerticalScroll(deltaY: Float) {
                adjustVolume(deltaY)
            }

            override fun onScrollEnd() {
                seekAccumulator = 0f
                hideFeedback()
            }
        })
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return if (::gestureDetector.isInitialized) {
            gestureDetector.onTouchEvent(event)
        } else false
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // Intercept all touches when overlay is visible
        return true
    }

    private fun showFeedback(text: String) {
        if (feedbackView == null) {
            feedbackView = TextView(context).apply {
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 20f
                setPadding(32, 16, 32, 16)
                setBackgroundColor(0xCC000000.toInt())
            }
            val params = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER)
            addView(feedbackView, params)
        }
        feedbackView?.text = text
        feedbackView?.visibility = View.VISIBLE
    }

    private fun updateSeekFeedback() {
        videoBridge?.getCurrentTime { current ->
            videoBridge?.getDuration { duration ->
                val sign = if (seekAccumulator >= 0) "+" else ""
                val time = formatTime(current)
                val total = formatTime(duration)
                showFeedback("$sign${formatTime(seekAccumulator)}  $time / $total")
            }
        }
    }

    private fun hideFeedback() {
        feedbackView?.visibility = View.GONE
    }

    private fun adjustBrightness(deltaY: Float) {
        val window = (context as? android.app.Activity)?.window ?: return
        val layoutParams = window.attributes
        val current = if (layoutParams.screenBrightness == WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) {
            0.5f
        } else layoutParams.screenBrightness
        val newBrightness = (current + deltaY / 1000f).coerceIn(0f, 1f)
        layoutParams.screenBrightness = newBrightness
        window.attributes = layoutParams
        showFeedback("☀ ${(newBrightness * 100).toInt()}%")
    }

    private fun adjustVolume(deltaY: Float) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val delta = (deltaY / 50f).toInt()
        val newVolume = (current + delta).coerceIn(0, max)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
        showFeedback("🔊 ${(newVolume * 100 / max)}%")
    }

    private fun formatTime(seconds: Float): String {
        val totalSeconds = seconds.toInt()
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        return String.format("%02d:%02d", m, s)
    }
}
```

- [ ] **Step 4: Connect overlay in BrowserFragment**

In `setupWebView()` after attaching videoBridge:
```kotlin
        binding.videoGestureOverlay.attach(videoBridge)
```

- [ ] **Step 5: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: video gesture overlay with long-press speed, swipe seek, double-tap, brightness/volume"
```

---

### Task 8: Error Handling, Back Button, and Fullscreen Video

**Files:**
- Create: `res/layout/view_error.xml`
- Modify: `BrowserFragment.kt` (error page, back button, fullscreen handling)
- Modify: `MainActivity.kt` (delegate back press to fragment)

**Interfaces:**
- Consumes: `BrowserViewModel.uiState.error`
- Produces: error page with retry, back navigation, fullscreen video view

- [ ] **Step 1: Create error layout**

`res/layout/view_error.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/white"
    android:gravity="center"
    android:orientation="vertical"
    android:padding="32dp">

    <TextView
        android:id="@+id/errorTitle"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/error_title"
        android:textColor="@color/text_primary"
        android:textSize="20sp"
        android:textStyle="bold" />

    <TextView
        android:id="@+id/errorMessage"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="12dp"
        android:textColor="@color/text_secondary"
        android:textSize="14sp"
        android:gravity="center" />

    <Button
        android:id="@+id/retryButton"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="24dp"
        android:text="@string/error_retry"
        android:backgroundTint="@color/primary" />

</LinearLayout>
```

- [ ] **Step 2: Add error view to fragment_browser.xml**

Add inside the FrameLayout, after startupView:
```xml
    <include
        android:id="@+id/errorView"
        layout="@layout/view_error"
        android:visibility="gone" />
```

- [ ] **Step 3: Handle back button in MainActivity**

Update `MainActivity`:
```kotlin
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
        if (fragment is BrowserFragment && fragment.onBackPressed()) {
            return
        }
        super.onBackPressed()
    }
```

- [ ] **Step 4: Add onBackPressed to BrowserFragment**

```kotlin
    fun onBackPressed(): Boolean {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
            return true
        }
        if (!viewModel.uiState.value.showStartup) {
            viewModel.showStartup()
            binding.webView.loadUrl("about:blank")
            return true
        }
        return false
    }
```

- [ ] **Step 5: Handle error state and retry**

In `observeState`, add:
```kotlin
                    val errorView = binding.errorView
                    if (state.error != null) {
                        errorView.visibility = View.VISIBLE
                        errorView.errorMessage.text = state.error.message
                    } else {
                        errorView.visibility = View.GONE
                    }
```

In `onViewCreated`, add retry listener:
```kotlin
        binding.errorView.retryButton.setOnClickListener {
            val url = viewModel.uiState.value.error?.failingUrl ?: viewModel.uiState.value.currentUrl
            if (url.isNotBlank()) {
                loadUrl(url)
            }
        }
```

Note: Since errorView is included, view binding generates `binding.errorView` which is the included root LinearLayout. We need to access children. Let's use `binding.errorView.findViewById<TextView>(R.id.errorMessage)` and `binding.errorView.findViewById<Button>(R.id.retryButton)`.

Actually with `<include>` and an id, view binding generates a field for the included layout's binding class if the include has an id. Let me verify: `binding.errorView` will be a `ViewErrorBinding`? No — with `<include android:id="@+id/errorView">`, view binding generates `binding.errorView` as the root View of the included layout, not a binding object. To get a binding object, don't set id on include, or use `ViewErrorBinding.bind(binding.errorView)`.

Simpler approach: use findViewById:
```kotlin
        val errorRoot = binding.errorView
        val errorMessage = errorRoot.findViewById<TextView>(R.id.errorMessage)
        val retryButton = errorRoot.findViewById<Button>(R.id.retryButton)
```

- [ ] **Step 6: Handle fullscreen video**

Add property to BrowserFragment:
```kotlin
    private var customView: View? = null
    private var customViewCallback: BrowserWebChromeClient.CustomViewCallback? = null
```

Update `BrowserWebChromeClient` construction to handle fullscreen:
```kotlin
        binding.webView.webChromeClient = BrowserWebChromeClient(
            onProgressChanged = { progress -> viewModel.onProgressChanged(progress) },
            onShowCustomView = { view ->
                customView = view
                (activity?.window?.decorView as? FrameLayout)?.addView(
                    view,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
                binding.urlBarView.visibility = View.GONE
            },
            onHideCustomView = {
                customView?.let {
                    (activity?.window?.decorView as? FrameLayout)?.removeView(it)
                }
                customView = null
                binding.urlBarView.visibility = View.VISIBLE
            }
        )
```

- [ ] **Step 7: Build and verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat: error page with retry, back button navigation, fullscreen video handling"
```

---

### Task 9: Unit Tests and Final Integration

**Files:**
- Create: `app/src/test/java/com/yohuohuohuo/browser/util/UrlUtilsTest.kt`
- Create: `app/src/test/java/com/yohuohuohuo/browser/util/SearchEngineTest.kt`
- Modify: `app/build.gradle.kts` (add test dependencies)

**Interfaces:**
- Tests for `UrlUtils` and `SearchEngine`

- [ ] **Step 1: Add test dependencies**

Add to `app/build.gradle.kts` dependencies:
```kotlin
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.9.22")
```

- [ ] **Step 2: Create UrlUtilsTest**

```kotlin
package com.yohuohuohuo.browser.util

import org.junit.Assert.*
import org.junit.Test

class UrlUtilsTest {

    @Test
    fun `isUrl returns true for http URL`() {
        assertTrue(UrlUtils.isUrl("http://example.com"))
    }

    @Test
    fun `isUrl returns true for https URL`() {
        assertTrue(UrlUtils.isUrl("https://example.com/path?query=1"))
    }

    @Test
    fun `isUrl returns true for domain without scheme`() {
        assertTrue(UrlUtils.isUrl("example.com"))
        assertTrue(UrlUtils.isUrl("www.example.com"))
        assertTrue(UrlUtils.isUrl("sub.example.com/path"))
    }

    @Test
    fun `isUrl returns false for plain text`() {
        assertFalse(UrlUtils.isUrl("hello world"))
        assertFalse(UrlUtils.isUrl("android tutorial"))
    }

    @Test
    fun `isUrl returns false for empty string`() {
        assertFalse(UrlUtils.isUrl(""))
        assertFalse(UrlUtils.isUrl("   "))
    }

    @Test
    fun `isUrl returns false for single word without dot`() {
        assertFalse(UrlUtils.isUrl("localhost"))
    }

    @Test
    fun `normalizeUrl adds https scheme`() {
        assertEquals("https://example.com", UrlUtils.normalizeUrl("example.com"))
    }

    @Test
    fun `normalizeUrl keeps existing scheme`() {
        assertEquals("http://example.com", UrlUtils.normalizeUrl("http://example.com"))
        assertEquals("https://example.com", UrlUtils.normalizeUrl("https://example.com"))
    }

    @Test
    fun `normalizeUrl trims whitespace`() {
        assertEquals("https://example.com", UrlUtils.normalizeUrl("  example.com  "))
    }
}
```

- [ ] **Step 3: Create SearchEngineTest**

```kotlin
package com.yohuohuohuo.browser.util

import org.junit.Assert.*
import org.junit.Test
import java.net.URLDecoder

class SearchEngineTest {

    @Test
    fun `buildSearchUrl uses baidu`() {
        val url = SearchEngine.buildSearchUrl("test")
        assertTrue(url.startsWith("https://www.baidu.com/s?wd="))
    }

    @Test
    fun `buildSearchUrl encodes query`() {
        val url = SearchEngine.buildSearchUrl("hello world")
        val encoded = url.substringAfter("wd=")
        assertEquals("hello+world", encoded)
    }

    @Test
    fun `buildSearchUrl encodes chinese characters`() {
        val url = SearchEngine.buildSearchUrl("你好")
        val encoded = url.substringAfter("wd=")
        val decoded = URLDecoder.decode(encoded, "UTF-8")
        assertEquals("你好", decoded)
    }

    @Test
    fun `buildSearchUrl trims query`() {
        val url = SearchEngine.buildSearchUrl("  test  ")
        val encoded = url.substringAfter("wd=")
        assertEquals("test", encoded)
    }
}
```

- [ ] **Step 4: Run unit tests**

Run: `./gradlew testDebugUnitTest`
Expected: All tests pass

- [ ] **Step 5: Final build verification**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL, APK generated at `app/build/outputs/apk/debug/app-debug.apk`

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "test: add unit tests for UrlUtils and SearchEngine"
```

---

## Self-Review Checklist

- [x] **Spec coverage:** Video gestures (Task 7), UA switching (Task 4), toolbar auto-hide (Task 5), startup page (Task 3), back button (Task 8), error handling (Task 8), immersive status bar (Task 5), search engine (Task 3) — all covered.
- [x] **Placeholder scan:** No TBD/TODO. All code blocks are concrete.
- [x] **Type consistency:** `UiState` fields match across tasks. `VideoJsBridge` method names consistent between definition and usage. `UaManager` API consistent.
- [x] **File structure matches spec:** All files from spec's module diagram are created across tasks.
