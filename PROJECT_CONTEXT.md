# Yobrowser Project Context

## 目标与范围

Yobrowser 是一个极简 Android 手机浏览器，重点是网页浏览、移动/桌面 UA 切换和网页视频手势增强。当前范围不包括多标签、书签/历史、下载管理、广告拦截、无痕模式、深色模式和多搜索引擎。

原始设计文档是 2026-08-19 版本，目前标记为“待评审”。以下内容以当前源码为准；设计文档只用于了解最初目标。

## 当前技术基线

| 项目 | 当前值 |
|---|---|
| Package/Application ID | `com.yohuohuohuo.browser` |
| Module | `app` |
| Language | Kotlin 1.9.24 |
| AGP | 8.4.2 |
| Gradle | Wrapper 8.6 |
| minSdk | 26 |
| targetSdk / compileSdk | 35 / 35 |
| JVM target | 17 |
| UI | Android Views + ViewBinding |
| State | Lifecycle `StateFlow` |
| Browser engine | System WebView |
| Release | R8 minification + resource shrinking enabled |
| Permission | `INTERNET` |

## 架构

```text
MainActivity
  └── BrowserFragment
       ├── BrowserViewModel (StateFlow<UiState>)
       ├── BrowserWebView + WebViewManager
       ├── BrowserWebViewClient / BrowserWebChromeClient
       ├── UrlBarView / StartupView / ErrorView
       └── VideoGestureOverlay
             ├── VideoGestureDetector
             └── VideoJsBridge <-> assets/video_bridge.js
```

### 生命周期与页面状态

1. `MainActivity` 创建 `BrowserFragment`，Fragment 显示启动页。
2. 输入 URL 或搜索词后，`BrowserFragment.loadUrl()` 委托 `WebViewManager` 加载。
3. `BrowserWebViewClient.onPageStarted()` 推进 `VideoJsBridge.pageGeneration`、清理视频状态并更新 ViewModel。
4. `onPageFinished()` 更新 URL/title/loading 状态，再注入 `video_bridge.js` 并注册带 generation 的 JS 回调。
5. 页面错误只对主 frame 更新 `BrowserError`；SSL 错误直接拒绝并报告。
6. renderer gone 时旧 WebView 被停载、移除、销毁，创建 `BrowserWebView`，重新装配 client/bridge/overlay，并尝试恢复 URL。
7. Fragment view 销毁时隐藏全屏 custom view、detach bridge/overlay、销毁 WebView；Fragment 销毁时 close bridge scope。

### 视频触摸数据流

```text
DOM video events / MutationObserver / scroll / resize / 1s polling
  -> findActiveVideo(): 视口内最大播放视频（无播放时仅作为控制目标）
  -> getBoundingClientRect()
  -> AndroidBridge.onVideoBounds(generation, left, top, width, height)
  -> RectF * WebView.scale
  -> VideoGestureOverlay.activeVideoBounds
  -> BrowserWebView.gestureHandler
```

普通模式的关键规则：

- 没有 bounds 时，不启用视频 touch handler。
- `ACTION_DOWN` 在 bounds 外返回 `false`，WebView 获得完整普通事件流。
- `ACTION_DOWN` 在 bounds 内后，后续 MOVE/UP/CANCEL 即使移出 bounds 仍由 detector 处理。
- 普通单击先等待 double-tap 窗口，确认后用 `dispatchNativeTap()` 回放给 WebView。
- 全屏 custom view 将 overlay 移至 decor，进入 fullscreen touch mode，整个全屏区域接收手势。

## 重要实现边界

- `VideoJsBridge` 的 active/inactive/bounds 回调必须验证 page generation 和 attach generation。
- `VideoGestureOverlay` 的 feedback runnable、detector pending tap、bounds 和 touch ownership 都要在 reset/detach 时清理。
- `BrowserWebView` 不应重新变成一个全屏拦截层；网页普通点击必须经过 `dispatchToWebView()`。
- `WebViewManager` 的 JavaScript、DOM storage、media playback 和 mixed content 设置是浏览器兼容性与安全的平衡点。
- `android:usesCleartextTraffic="true"` 目前仍在 Manifest 中，但 WebView 设置为 `MIXED_CONTENT_NEVER_ALLOW`；这需要设备和产品策略共同确认。
- Release APK 当前是 unsigned artifact，不等于可安装发布包。

## 测试分层

- JVM/Robolectric：`app/src/test`，覆盖 ViewModel、WebView touch routing、视频 detector/bridge/overlay、UA、URL/search、WebChrome/WebViewClient。
- 静态：JavaScript `node --check`、Kotlin/Android Lint、`git diff --check`。
- 构建：Debug APK、R8 Release APK。
- 缺失层：当前没有 `app/src/androidTest`，也没有真实设备/emulator WebView 网站交互证据。
