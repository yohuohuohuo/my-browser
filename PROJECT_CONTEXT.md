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

1. `MainActivity` 创建 `BrowserFragment`；新会话显示启动页，有保存状态时恢复浏览上下文。
2. 输入 URL 或搜索词后，`BrowserFragment.loadUrl()` 委托 `WebViewManager` 加载。
   两个输入入口共用 `handleInput()`，提交非空内容时先清除焦点并隐藏 IME，再发起加载。
3. `BrowserWebViewClient.onPageStarted()` 推进 `VideoJsBridge.pageGeneration`、清理视频状态并更新 ViewModel。
4. `doUpdateVisitedHistory()` 同步当前 WebView 的 history 地址，不推进 bridge generation；`onPageFinished()` 以单独记录的文档加载 URL 过滤旧完成回调，保留最新 history URL 并更新 title/loading，再注入带 generation 的视频回调。
5. 页面错误只对主 frame 更新 `BrowserError`；SSL 错误直接拒绝并报告。
6. renderer gone 时先退出全屏，再停载、移除、销毁旧 WebView；创建新 `BrowserWebView`，重新装配 client/bridge/overlay，并恢复最新 URL。
7. Fragment view 销毁时隐藏全屏 custom view、detach bridge/overlay、销毁 WebView；Fragment 销毁时 close bridge scope。
8. Activity/Fragment view 重建使用 WebView `saveState/restoreState` 恢复前后导航历史，失败则重载最新 URL；恢复完成暂停媒体，Home 的空白新 WebView 不复活旧页面。

### 视频触摸数据流

```text
DOM video events / MutationObserver / scroll / resize / 1s polling
  -> findActiveVideo(): 全屏 video 优先；普通模式保持已选且可见的视频，首次选择优先播放视频，无播放时仅选曾播放过的可见暂停视频
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
- 视频内普通单击的原始触摸立即交给网页播放器；双击从第二次 `DOWN` 起由浏览器消费，并通过 JS bridge 切换播放/暂停。
- 水平/垂直拖动越过阈值才向原接收方发送 `ACTION_CANCEL` 并接管；长按计时触发时也立即取消原接收方触摸。视频底部最多 56dp、且不超过视频高度 25% 的区域优先保留给网页控件。
- 水平滑动按 1px = 0.5s 增量 seek；单次手势的正向净快进量封顶 180s，反向滑动可扣回，手势结束后重置。
- 尚未启动的视频不激活手势层，首次点击交给网页播放器初始化；已播放过的视频暂停后仍保持活动态，以便下一次双击恢复播放。
- 浏览器不叠加统一控制栏；网页控件的显隐和操作仍由站点负责。提示层只在长按、滑动时短暂显示。
- 全屏 custom view 和 overlay 一起装入 `FullscreenVideoTouchHost`；host 先将原始事件交给 custom view，识别手势后取消并接管。

## 重要实现边界

- `VideoJsBridge` 的 active/inactive/bounds 回调必须验证 page generation 和 attach generation。
- Home 返回启动页必须销毁旧 WebView 并重建空白实例；旧 WebView 的页面回调不得再修改当前状态。后台和错误页用 `media_pause.js` 暂停可访问媒体，并暂停 WebView 处理；恢复可见不自动播放。
- `VideoGestureOverlay` 的 feedback runnable、detector pending tap、bounds 和 touch ownership 都要在 reset/detach 时清理。
- `BrowserWebView` 不应重新变成一个全屏拦截层；网页普通点击必须经过 `dispatchToWebView()`。
- `WebViewManager` 的 JavaScript、DOM storage、media playback 和 mixed content 设置是浏览器兼容性与安全的平衡点。
- `android:usesCleartextTraffic="true"` 目前仍在 Manifest 中，但 WebView 设置为 `MIXED_CONTENT_NEVER_ALLOW`；这需要设备和产品策略共同确认。
- Release APK 当前是 unsigned artifact，不等于可安装发布包。

## 测试分层

- JVM/Robolectric：`app/src/test`，覆盖 ViewModel、WebView touch routing、视频 detector/bridge/overlay、UA、URL/search、WebChrome/WebViewClient。
- 静态：JavaScript `node --check`、Kotlin/Android Lint、`git diff --check`。
- 构建：Debug APK、R8 Release APK。
- 缺失层：当前没有 `app/src/androidTest`。API 31 emulator 已有有限本地夹具 smoke，真机、真实站点和 WebView 版本矩阵仍缺失。
