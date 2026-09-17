# Yobrowser Handoff

## 快照

- 更新时间：2026-09-17
- 仓库：`/Users/qiuyupan/git_workspace/my-browser`
- 分支：`feature/yobrowser-impl`
- 最后提交：`5c602b8`（以当前 `git log` 为准）
- 工作树：视频手势、键盘提交修复及文档有未提交修改；更早的历史章节仅作背景，以当前 `git status` 为准。
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
- 全屏视频 custom view、系统栏隐藏/恢复、返回键优先退出全屏。
- 视频长按 3x、水平 seek、双击播放切换、左右垂直亮度/音量调节、反馈层和 pending gesture 清理。
- Release R8/resource shrinking、JavaScript bridge ProGuard 保留规则。
- `UrlUtils`、`BrowserViewModel`、`BrowserWebView`、视频 detector/bridge/overlay、UA、WebChrome/WebViewClient 的 JVM 单测。

## 2026-09-17 视频单击与双击修复

- 视频区域内单击确认后不再向网页或 fullscreen custom view 回放触摸，只显示浏览器统一控制栏（播放/暂停按钮、进度条、时间）；3 秒无操作自动隐藏。
- 双击仍通过 `VideoJsBridge.togglePlay()` 切换播放状态；JS 保持已选且可见的视频身份，支持暂停后再次双击恢复，而不误切到其他视频。
- 未启动的视频继续由网页处理首次点击；普通模式控制栏限制在视频 bounds 内，位置叠加 WebView 内容 top inset，视频外的网页触摸不被控制栏截获。
- bridge 注册回调时补发当前已识别视频的 active/bounds，避免脚本先识别视频、后注册回调时漏报。
- 视频失活、导航、全屏切换和 overlay detach/reset 时清理控制栏及计时任务。网页视频区域外触摸路径未改。
- 回归测试：`VideoGestureOverlayTest` 和 `app/src/test/js/video_bridge.test.js`，覆盖多视频、尚未启动视频与控制栏触摸范围。完整验证见 `VERIFICATION.md`；设备实测仍待办。

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

- `UrlBarView` 在地址栏左侧新增 Home 按钮，与 URL 输入框、UA 按钮保持同一行；点击后复用启动页返回流程，停止当前加载并清理旧页面的视频状态。
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

本次开始前工作树为 clean，基线为 `5c602b8`。当前未提交内容仅是视频单击/双击修复、对应测试/资源及接管文档。提交前仍应审阅完整 diff 并取得用户确认；不自动 commit/push。

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
