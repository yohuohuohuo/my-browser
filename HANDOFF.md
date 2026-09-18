# Yobrowser Handoff

## 快照

- 更新时间：2026-09-18
- 仓库：`/Users/qiuyupan/git_workspace/my-browser`
- 分支：`feature/yobrowser-impl`
- 最后提交：`3e38edf`（以当前 `git log` 为准）
- 工作树：此前视频触摸路由修复及本次媒体生命周期修复均未提交；更早的历史章节仅作背景，以当前 `git status` 为准。
- 接管结论：当前实现可构建和通过 JVM/Lint，但还不能称为已发布或已完成真机验收的 Android 浏览器。

## 当前已完成能力

代码当前已经包含以下能力，后续 agent 不要把它们误判为“尚未实现”：

- 单 Activity + 单 Fragment 的浏览器主流程。
- 启动页、URL/search 输入、百度搜索 URL、地址栏编辑态和 UA 移动端/桌面端切换。
- 地址栏左侧 Home 按钮返回启动页，以及网页加载进度条在地址栏底部/网页顶部的自适应定位。
- 主页启动页不显示顶部地址栏；从主页搜索后清空启动页搜索框，进入网页后恢复地址栏。
- WebView 显式开启普通/触摸焦点能力，并保持普通网页触摸从按下到抬起的完整事件序列。
- WebView JavaScript/DOM storage、媒体用户手势限制、缓存、缩放和生命周期销毁。
- 主 frame 加载错误、HTTP 错误、SSL 错误拒绝、错误页重试。
- WebView renderer gone 后重建 WebView 并恢复当前 URL。
- 返回主页销毁旧 WebView（包含页面、子 frame 和播放上下文），后台/错误页暂停页面媒体；恢复可见时不自动播放。
- 全屏视频 custom view、系统栏隐藏/恢复、返回键优先退出全屏。
- 视频长按 3x、水平 seek、双击播放切换、左右垂直亮度/音量调节、反馈层和 pending gesture 清理。
- Release R8/resource shrinking、JavaScript bridge ProGuard 保留规则。
- `UrlUtils`、`BrowserViewModel`、`BrowserWebView`、视频 detector/bridge/overlay、UA、WebChrome/WebViewClient 的 JVM 单测。

## 2026-09-18 页面不可见时的媒体处理

- Home 按钮和无历史记录时的返回键都卸载旧 WebView 并创建空白的新 WebView。原页面历史和状态不再保留，这是用户明确要求的 Home 语义。
- 旧 WebView 的加载、错误、进度、renderer、全屏回调仅在它仍是当前 WebView 时生效；同一 WebView 上旧导航的完成回调不能覆盖新 URL，错误后的完成回调不能清掉错误页。
- 后台、主 frame 错误页和应用发起的导航会执行独立的 `media_pause.js`，暂停主文档与可访问的同源 frame 中所有 audio/video；后台和错误页还调用 WebView `onPause()`，恢复处理时不调用媒体 `play()`。
- 跨域 frame 不能由页面 JavaScript 直接遍历；后台/错误页依赖 WebView `onPause()` 的 best-effort 行为。Home 的销毁路径不受此限制。
- 新增主页卸载、旧回调隔离、后台/错误页/重试的 Robolectric 回归和媒体暂停脚本 Node 测试。全量验证见 `VERIFICATION.md`；真机媒体行为仍待验收。

## 2026-09-17 视频触摸回退与通用透传修复

- 删除叠加在网页播放器上的简化浏览器控制栏。活动视频的首次单击不延迟、不回放，原始 `DOWN/UP` 立即交给 WebView 或全屏 custom view，由网页自己的播放器决定控制栏显隐。
- 双击从第二次 `DOWN` 起由浏览器消费并调用 `togglePlay()`；拖动越过阈值时向原生接收方发送 `ACTION_CANCEL`，再接管 seek、亮度和音量；长按计时触发时立即取消原生触摸，再进入 3x 倍速。
- 全屏 custom view 使用 `FullscreenVideoTouchHost` 路由原始触摸，反馈 overlay 只负责绘制；退出全屏后恢复 overlay 原位置。视频底部最多 56dp、且不超过视频高度 25% 的区域保留给网页控制栏触摸。
- 长按和滑动提示改为 13sp 常规字重、低对比背景、紧凑内边距和 1 秒自动隐藏。1px = 0.5s 与单次快进上限 180s 不变。
- 单测与构建见 `VERIFICATION.md`；真实站点控件显隐、触摸坐标及站点自制控件仍需设备复测。

## 2026-09-17 横滑快进上限

- 保留水平滑动 1px = 0.5s 的比例；单次按下到松开，正向净快进最多 180s。达到上限后继续右滑不再推进，反向滑动按手指净位移扣回；下一次滑动重新计数。快退保持原规则。
- 修改 `VideoGestureOverlay.kt` 并在 `VideoGestureOverlayTest.kt` 增加上限、反向扣回与新手势重置回归；设备上的视频进度行为仍待实测。

## 2026-09-17 提交搜索后收起输入法

- `BrowserFragment.handleInput()` 统一处理首页搜索框与地址栏的非空提交：清除当前输入焦点、显式请求隐藏 IME，再按原有 URL/搜索规则加载网页。
- `MainActivityTest` 增加两条 Robolectric 回归，分别验证首页搜索与地址栏提交后输入法隐藏；实际输入法与 WebView 加载时序仍需设备验证。

## 本次修复：内容安全区与视频方向

- `BrowserFragment` 根据状态栏 inset 和地址栏可见状态更新 `webViewContainer` 的 `topMargin`；地址栏可见时正文从地址栏下方开始，隐藏后正文只保留状态栏安全区。
- 地址栏隐藏时整体移出屏幕，不再停留在状态栏区域；WebView 正文在隐藏态仍保留状态栏安全区。
- WebView 滚动方向触发地址栏显示/隐藏时使用 300ms 主线程空闲防抖；手指仍处于 WebView 触摸生命周期时不改变 viewport，降低慢速连续滚动重布局造成重影/抖动的风险。
- `video_bridge.js` 和 `VideoJsBridge` 提供当前活动视频的 intrinsic dimensions（无 metadata 时回退到可见 rect）；全屏进入后由 `MainActivity` 请求横屏或竖屏，退出全屏恢复 `SCREEN_ORIENTATION_UNSPECIFIED`。
- 新增 `BrowserLayoutMetricsTest`、`VideoOrientationTest`、`VideoJsBridgeDimensionsTest`、`MainActivityTest` 和 `BrowserWebViewTest` 回归覆盖；本次全量 JVM 单测为 77 tests，0 failures/errors。

本次修改文件：

- `app/src/main/java/com/yohuohuohuo/browser/MainActivity.kt`
- `app/src/main/java/com/yohuohuohuo/browser/BrowserFragment.kt`
- `app/src/main/java/com/yohuohuohuo/browser/gesture/VideoJsBridge.kt`
- `app/src/main/java/com/yohuohuohuo/browser/gesture/VideoOrientation.kt`
- `app/src/main/java/com/yohuohuohuo/browser/ui/BrowserLayoutMetrics.kt`
- `app/src/main/assets/video_bridge.js`
- `app/src/main/java/com/yohuohuohuo/browser/ui/UrlBarView.kt`
- `app/src/main/res/layout/view_url_bar.xml`
- `app/src/main/res/layout/fragment_browser.xml`
- `app/src/main/res/drawable/ic_home.xml`
- 对应 `app/src/test` 回归测试文件

## 本次功能：主页按钮与网页加载进度条

- `UrlBarView` 在地址栏左侧新增 Home 按钮，与 URL 输入框、UA 按钮保持同一行；当前点击会卸载旧 WebView 并进入启动页（早期只停止加载的实现已被 2026-09-18 修复替代）。
- `BrowserFragment` 使用现有 `BrowserWebChromeClient`/`BrowserViewModel` 的 `isLoading` 和 `loadingProgress` 状态控制横向进度条；地址栏可见时进度条位于地址栏底部，地址栏隐藏时位于状态栏安全区下方的网页顶部。
- `UrlBarViewTest` 覆盖 Home 按钮回调，`MainActivityTest` 覆盖进度条存在、最大值和地址栏底部定位；全量 JVM 单测为 79 tests，0 failures/errors。

## 本次修复：网页输入法与主页地址栏清空

- `BrowserWebView` 在未被视频手势接管时始终把普通触摸序列转发给 WebView；即使 WebView 对单个事件返回 `false`，也不会丢失后续 MOVE/UP，避免链接点击和页面滚动互相干扰。
- `WebViewManager` 显式开启 WebView 的普通焦点和触摸焦点能力，但不在每次触摸时强制调用 `requestFocusFromTouch()`，避免焦点操作重置网页滚动。
- `UrlBarView.clearUrl()` 与启动页状态同步使用，主页按钮点击后立即清空旧地址，ViewModel 后续状态刷新也不会恢复旧文本。
- `BrowserWebViewTest`、`UrlBarViewTest` 和 `MainActivityTest` 覆盖普通触摸序列、地址栏清空和主页跳转；全量 JVM 单测为 82 tests，0 failures/errors。

## 本次修复：主页布局与搜索框状态

- `BrowserFragment` 根据 `UiState.showStartup` 控制地址栏显隐：启动页隐藏顶部地址栏，网页状态恢复地址栏；布局 XML 初始状态也设为隐藏，避免启动页首帧闪现。
- 主页搜索提交时先清空 `StartupView` 的输入框，再发起 URL/搜索跳转；返回主页后不会残留上一次搜索词。
- `MainActivityTest` 覆盖启动页地址栏隐藏、网页状态恢复地址栏和搜索框清空；全量 JVM 单测为 84 tests，0 failures/errors。

## 本次修复：滚动重影与地址栏完全隐藏

- `BrowserFragment` 不再在连续滚动回调中立即改变 `webViewContainer.topMargin`；方向请求会在 300ms 没有新的有效滚动且触摸生命周期结束后执行，并在导航、全屏、WebView 重建和视图销毁时取消 pending runnable。
- `BrowserWebView` 上报普通 WebView 触摸的按下/抬起状态，供地址栏布局防抖判断是否仍在手势中。
- `BrowserLayoutMetrics.toolbarHideTranslation()` 使用地址栏完整高度作为隐藏位移，保证地址栏整体离开状态栏区域。
- `MainActivityTest` 覆盖连续滚动期间 viewport 保持稳定、触摸生命周期结束后才切换到状态栏安全区；`BrowserWebViewTest` 覆盖触摸状态回调；`BrowserLayoutMetricsTest` 覆盖完整隐藏位移和零高度边界。

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

本次开始前工作树为 clean，基线为 `3e38edf`。当前未提交内容仅是视频触摸路由、反馈样式、对应测试与文档。提交前仍应审阅完整 diff 并取得用户确认；不自动 commit/push。

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
| 内容安全区和视频方向判断 | `BrowserLayoutMetrics.kt` + `VideoOrientation.kt` |
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
