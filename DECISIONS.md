# Yobrowser Decisions

这些是当前实现依赖的架构和安全决策。改变它们前，应同步 `PROJECT_CONTEXT.md`、`HANDOFF.md` 和验证记录。

## D1：单 Activity + 单 Fragment + MVVM

- 当前实现使用 `MainActivity`、`BrowserFragment`、`BrowserViewModel` 和手动装配。
- 原因：MVP 范围只有一个浏览页面，不引入多模块和 DI 框架降低维护成本。
- 约束：新能力优先放在现有责任边界内；只有出现真实复杂度或用户明确要求时才拆分架构。

## D2：使用系统 WebView，不自带浏览器内核

- 页面渲染、历史和 JavaScript 执行由系统 WebView 负责。
- 应用层只通过 `WebViewManager`、`BrowserWebViewClient`、`BrowserWebChromeClient` 和 bridge 管理边界。
- 约束：必须在文档中区分 JVM/Robolectric 结果与真实 WebView 结果。

## D3：视频手势通过 JS bridge 控制当前页面 video

- Native detector 负责手势识别，JS 负责查找 video、播放、暂停、seek、rate 和时间读取。
- 浏览器不覆盖或替代网页播放器控制栏。视频内首次单击保留原始触摸时序并交给网页；第二次点击确认双击后由 bridge 切换播放/暂停。拖动或长按接管时向网页原接收方发送 `ACTION_CANCEL`。已播放过且可见的视频暂停后仍保留活动态，并锁定同一目标；尚未启动的视频保留网页自己的启动入口。
- 全屏触摸经 `FullscreenVideoTouchHost` 路由给 custom view，overlay 仅绘制反馈。视频底部最多 56dp、且不超过视频高度 25% 的区域保留给网页控件，避免浏览器滑动手势劫持进度条。
- 水平滑动继续按 1px = 0.5s 换算；单次手势的正向净快进量上限为 180s，反向滑动可扣回，松手后重置额度；快退不在此次限制范围内。
- JS 回调带 page generation；Native 同时校验 page/attach generation，防止旧页面异步回调污染新页面。
- `@JavascriptInterface` 方法由 `app/proguard-rules.pro` 保留，不能仅依赖 R8 自动推断。

## D4：普通网页触摸优先于全屏 overlay

- `BrowserWebView` 是普通触摸的最终目标；`VideoGestureOverlay` 是反馈和手势路由层，不应在普通模式无条件拦截整个 WebView。
- 活动视频 bounds 来自 `getBoundingClientRect()`，仅 bounds 内的 `ACTION_DOWN` 开始一条视频手势；开始后维持 ownership 到结束。
- 全屏 custom view 是例外：进入 fullscreen touch mode 后整个全屏区域都由 overlay 消费。

## D5：SSL 错误默认拒绝，混合内容默认禁止

- SSL 错误直接 cancel，不提供“继续访问”路径。
- WebView 使用 `MIXED_CONTENT_NEVER_ALLOW`。
- Manifest 当前仍声明 `usesCleartextTraffic="true"`，这是历史兼容性配置，不能视为已完成的安全策略；需由产品根据目标站点决定是否收紧。

## D6：Release 默认开启 R8 和资源压缩

- Release 开启 `isMinifyEnabled` 和 `isShrinkResources`，bridge 和 ViewBinding 有明确 keep 规则。
- 任何新增 JavaScript bridge、反射入口或自定义 View 绑定都必须在 Release 构建中验证。
- unsigned release artifact 不代表发布完成，签名必须在外部安全流程处理。

## D7：先保留真实实现的稳定 contract，再抽象

- 当前稳定边界是输入层、WebView 生命周期、视频 detector/bridge/overlay、URL/UA 工具和状态流。
- 不要因为设计文档提到“全屏通知”“主视频识别”等概念就提前抽出 renderer/pipeline；先用真实站点和设备证据证明需要抽象。

## D8：Home 卸载页面，其余不可见场景暂停媒体

- Home 按钮及回到启动页的返回键销毁旧 WebView 并创建新 WebView，不保留旧页面历史、iframe 或媒体上下文。
- 后台、错误页及导航离开使用独立的全媒体暂停脚本；后台和错误页同时调用 WebView `onPause()`，可见后仅恢复 WebView 处理，不自动调用媒体 `play()`。
- 页面脚本只能遍历同源 frame；跨域 frame 的后台暂停是 WebView best-effort 行为，必须做设备验收，不能把 Node/Robolectric 测试视为跨站保证。
