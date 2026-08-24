# Yobrowser Handoff

## 快照

- 更新时间：2026-08-24
- 仓库：`/Users/qiuyupan/git_workspace/my-browser`
- 分支：`feature/yobrowser-impl`
- 最后提交：`c1e6973 test: add unit tests for UrlUtils and SearchEngine`
- 工作树：非 clean。当前工作树包含上一轮项目实现、修复、测试和本次接管文档，尚未提交。
- 接管结论：当前实现可构建和通过 JVM/Lint，但还不能称为已发布或已完成真机验收的 Android 浏览器。

## 当前已完成能力

代码当前已经包含以下能力，后续 agent 不要把它们误判为“尚未实现”：

- 单 Activity + 单 Fragment 的浏览器主流程。
- 启动页、URL/search 输入、百度搜索 URL、地址栏编辑态和 UA 移动端/桌面端切换。
- WebView JavaScript/DOM storage、媒体用户手势限制、缓存、缩放和生命周期销毁。
- 主 frame 加载错误、HTTP 错误、SSL 错误拒绝、错误页重试。
- WebView renderer gone 后重建 WebView 并恢复当前 URL。
- 全屏视频 custom view、系统栏隐藏/恢复、返回键优先退出全屏。
- 视频长按 3x、水平 seek、双击播放切换、左右垂直亮度/音量调节、反馈层和 pending gesture 清理。
- Release R8/resource shrinking、JavaScript bridge ProGuard 保留规则。
- `UrlUtils`、`BrowserViewModel`、`BrowserWebView`、视频 detector/bridge/overlay、UA、WebChrome/WebViewClient 的 JVM 单测。

## 最近修复的触摸问题

此前 `VideoGestureOverlay.isGestureEnabled()` 只判断 overlay 可见和 detector 存在，导致活动视频出现后整个 WebView 的触摸都被交给视频手势层。

当前实现已改为：

1. `video_bridge.js` 上报活动视频的 `getBoundingClientRect()`，并在 scroll、resize、轮询时刷新。
2. `VideoJsBridge` 携带 page generation 接收 bounds，过滤旧页面回调，并在 inactive 时清理 bounds。
3. `BrowserFragment` 按 `WebView.scale` 将 CSS 坐标转换为触摸坐标。
4. `VideoGestureOverlay` 只在 `ACTION_DOWN` 位于活动视频矩形内时接管；开始后移出矩形仍保持当前手势；矩形外完整透传给 WebView。
5. 全屏模式仍接管整个 fullscreen custom view。

相关文件：

- `app/src/main/assets/video_bridge.js`
- `app/src/main/java/com/yohuohuohuo/browser/gesture/VideoJsBridge.kt`
- `app/src/main/java/com/yohuohuohuo/browser/BrowserFragment.kt`
- `app/src/main/java/com/yohuohuohuo/browser/ui/VideoGestureOverlay.kt`
- `app/src/test/java/com/yohuohuohuo/browser/gesture/VideoJsBridgeTest.kt`
- `app/src/test/java/com/yohuohuohuo/browser/ui/VideoGestureOverlayTest.kt`

## 当前工作树边界

`git status --short` 显示的未提交内容覆盖：

- `app/build.gradle.kts`、Manifest、Gradle properties/version catalog/wrapper 配置。
- `BrowserFragment`、`BrowserViewModel`、`MainActivity`、视频 gesture、URL、WebView/Chrome client 和 WebView manager。
- 浏览器布局、启动页、地址栏、主题、字符串、launcher 资源。
- 新增 `BrowserWebView.kt`、`app/proguard-rules.pro` 和多组 JVM 测试。
- 旧 `mipmap-anydpi-v26` launcher XML 被删除，新 launcher 资源位于 `mipmap-anydpi`。

不要只提交本次触摸修复的几个文件而忽略同一工作树中的依赖改动；提交前应先按功能拆分并人工审阅完整 diff。也不要为了生成文档而 reset、stash 或清理这些文件。

## 当前架构入口

| 责任 | 文件 |
|---|---|
| Activity、系统栏、返回键 | `app/src/main/java/com/yohuohuohuo/browser/MainActivity.kt` |
| 页面装配、WebView、全屏、视频生命周期 | `app/src/main/java/com/yohuohuohuo/browser/BrowserFragment.kt` |
| 页面状态 | `app/src/main/java/com/yohuohuohuo/browser/BrowserViewModel.kt` |
| WebView 配置/销毁 | `app/src/main/java/com/yohuohuohuo/browser/web/WebViewManager.kt` |
| 页面加载/错误/SSL/renderer | `app/src/main/java/com/yohuohuohuo/browser/web/BrowserWebViewClient.kt` |
| 全屏和进度 | `app/src/main/java/com/yohuohuohuo/browser/web/BrowserWebChromeClient.kt` |
| 视频状态和 bounds bridge | `app/src/main/java/com/yohuohuohuo/browser/gesture/VideoJsBridge.kt` + `app/src/main/assets/video_bridge.js` |
| 手势识别和触摸过滤 | `VideoGestureDetector.kt` + `VideoGestureOverlay.kt` + `BrowserWebView.kt` |
| URL/搜索/UA | `util/UrlUtils.kt`、`util/SearchEngine.kt`、`ua/UaManager.kt` |

## 下一步建议

按优先级执行 `TASKS.md`。最先做设备/emulator smoke test 和当前未提交 diff 的分组审阅；不要先做无依据的架构重构。

## 证据索引

- 最新构建和单测证据：`VERIFICATION.md`
- 风险边界：`KNOWN_ISSUES.md`
- 架构事实和数据流：`PROJECT_CONTEXT.md`
- 架构决策：`DECISIONS.md`
- 原始目标：`docs/superpowers/specs/2026-08-19-yobrowser-design.md`
- 原始实施计划：`docs/superpowers/plans/2026-08-19-yobrowser.md`
