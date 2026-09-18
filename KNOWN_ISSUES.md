# Yobrowser Known Issues

这里区分“已确认事实”和“尚未通过设备验证的风险”，避免把静态检查结果写成运行时保证。

## P0：验证阻塞

### 没有真实 WebView/设备交互证据

- 状态：已确认。
- 证据：本机 2026-08-24 执行 `adb devices` 时，ADB server 因 `Operation not permitted` 无法启动。
- 影响：网页点击透传、视频坐标、全屏系统栏、renderer crash、媒体播放策略仍未在真实 Chromium/WebView 上确认。
- 下一步：见 `TASKS.md` 的 P0 设备 smoke test；在设备可用前，不能宣称视频交互已发布级验证。

## P1：发布风险

### Release APK 未签名

- 状态：已确认。
- 证据：`app/build/outputs/apk/release/app-release-unsigned.apk`；Release 已启用 R8/resource shrinking，但没有正式签名配置。
- 影响：该 APK 不能作为最终发布包，也不能代表升级、安装覆盖和签名校验已验证。
- 下一步：配置安全的签名流程并在 CI/发布环境验证，不要把 keystore 放入仓库。

### Lint 仍有 warnings

- 状态：已确认；最新数量见 `VERIFICATION.md`，尚未逐条归类。
- 影响：当前构建允许通过，但警告是否与 UI、可访问性、资源或 release 风险有关还没有逐条归类。
- 下一步：在不批量压制的前提下逐条审阅；本次文档生成不修改这些 warning。

### 明文流量配置需要产品决策

- 状态：已确认配置，运行时影响未在设备确认。
- 当前代码：`AndroidManifest.xml` 设置 `android:usesCleartextTraffic="true"`；`WebViewManager.kt` 设置 `MIXED_CONTENT_NEVER_ALLOW`。
- 影响：应用级允许明文流量与 WebView 禁止 HTTPS 页面加载 HTTP 混合内容并不等价，可能导致部分站点资源或视频不可用，也扩大了应用允许的网络边界。
- 下一步：根据真实站点需求决定是否移除 Manifest 开关或使用明确的 Network Security Config；不要只为“兼容更多网站”全局放开 mixed content。

### 没有 instrumentation/androidTest

- 状态：已确认。虽然 Gradle 设置了 `testInstrumentationRunner`，仓库当前没有 `app/src/androidTest`。
- 影响：返回键、系统栏、真实 WebView 事件分发和 renderer 生命周期没有自动化设备回归。
- 下一步：至少补充 WebView touch routing、fullscreen back 和页面生命周期的设备测试。

## P2：兼容性风险

### 不可见页面媒体暂停仍需真实 WebView 验证

- 状态：Home 已改为销毁旧 WebView，Robolectric 验证旧实例销毁/替换；后台和错误页暂停脚本、WebView `onPause()` 已实现，但没有设备行为证据。
- 影响：同源 frame 可以由脚本遍历，跨域 frame 和站点自建的非 HTMLMediaElement 播放器无法由页面脚本可靠控制；后台暂停依赖系统 WebView 的 best-effort 行为。
- 下一步：真机验证 Home 后音频确实停止、返回主页后历史不可恢复、后台/错误页/导航离开时音视频暂停、前台恢复不自动播放，覆盖普通页面与跨域 iframe。

### 活动视频选择是启发式策略

- 状态：已确认设计限制，未定义为缺陷。
- 当前策略：`video_bridge.js` 只扫描当前 document 的 `<video>`，优先保持已选且可见的视频；首次选择优先视口内最大的播放视频，无播放视频时仅回退到曾播放过的可见暂停视频。
- 影响：iframe、shadow DOM、多个同时播放视频、自定义 canvas 播放器和复杂 CSS transform 页面可能无法识别或选择错误。
- 下一步：在真实目标站点采样后再决定是否扩展 renderer；不要先抽象不存在的公共 pipeline。

### 网页播放器控制栏与浏览器手势仍需真实站点验证

- 状态：用户实测上一版简化浏览器控制栏与网页控件重叠，网页控件常驻；本次已删除叠加栏并修正原始触摸路由，设备结果待复测。
- 当前策略：单击原样交给网页；双击第二次点击、拖动越阈值及长按才由浏览器接管。底部常见控件区域优先交给网页；暂停后保留同一视频目标。
- 影响：网页自制播放器可把单击定义为播放/暂停，浏览器无法在保留其原生触摸行为的同时通用地改写其控件显隐。控件不在底部、超出保留区、iframe/shadow DOM 或多视频页面仍可能与手势冲突。
- 下一步：真机分别验证普通/全屏的单击、双击、长按、seek、网页进度条拖动、控件自动隐藏和返回键；若仍有冲突，记录准确事件序列和控件位置，再调整通用触摸边界。

### CSS bounds 与 MotionEvent 坐标尚未做设备矩阵验证

- 状态：代码已按 `WebView.scale` 转换，设备证据缺失。
- 影响：不同 density、zoom、横竖屏、窗口 inset 和页面 transform 可能使视频区域边界出现偏移。
- 下一步：在多种屏幕尺寸和 WebView zoom 下记录 rect、scale、MotionEvent 坐标，必要时补 instrumentation 断言。

### JS bridge 依赖页面生命周期和 WebView 实现

- 状态：generation、attach/detach 和 R8 keep 已处理，站点级兼容性未完全验证。
- 影响：SPA、频繁替换 video、旧 WebView 对 `MutationObserver`/`WeakSet`/`querySelectorAll().forEach` 的支持差异，可能使活动态更新滞后。
- 下一步：在 Android 8/API 26 及当前 target 设备上验证；不要把 `node --check` 当作 WebView 行为测试。

### 滚动重影与地址栏隐藏依赖真实 WebView 渲染验证

- 状态：代码和 Robolectric 回归已覆盖，真实设备未验证。
- 当前策略：地址栏显示/隐藏在滚动空闲 300ms 且 WebView 触摸生命周期结束后才调整 viewport；隐藏时地址栏整体移出屏幕，正文保留状态栏安全区。
- 影响：不同 Android System WebView、GPU 驱动、系统导航模式和页面复杂度下，仍可能存在渲染抖动或动画时序差异。
- 下一步：设备可用后验证快速连续上滑、惯性滚动、反向滚动以及状态栏/地址栏边界；如仍有重影，再采集 `dumpsys gfxinfo` 和屏幕录制定位。

### Home 按钮与加载进度条依赖真实系统栏验证

- 状态：代码和 Robolectric 回归已覆盖，真实设备未验证。
- 当前策略：Home 按钮销毁旧 WebView 并进入启动页；启动页隐藏地址栏，网页状态恢复地址栏；横向加载进度条根据地址栏安全区定位，地址栏隐藏后放在网页顶部。
- 影响：不同系统状态栏高度、导航模式、字体缩放和 System WebView 加载时序可能影响按钮触摸区域或进度条视觉位置。
- 下一步：设备可用后验证 Home 点击、启动页无地址栏、搜索框清空、加载中/完成/失败状态，以及地址栏滚动隐藏和恢复过程中的进度条位置。

### 网页输入法与主页地址栏清空依赖真实 WebView 验证

- 状态：代码和 Robolectric 回归已覆盖，真实设备和实际网页输入框未验证。
- 当前策略：普通网页触摸从 `ACTION_DOWN` 到 `ACTION_UP/CANCEL` 保持完整序列并交给 WebView；不再对每次触摸强制调用 `requestFocusFromTouch()`，视频手势接管的触摸保持原有边界。主页状态同步强制清空 URL 输入框。
- 影响：不同 Android System WebView、输入框类型、网页脚本焦点逻辑和输入法实现可能仍存在差异。
- 下一步：设备可用后验证普通文本、密码、搜索、iframe 和脚本动态聚焦输入框，并确认 Home 点击后地址栏、启动页输入框和键盘状态一致。

### 地址栏与首页搜索提交后的键盘收起依赖设备验证

- 状态：提交路径已显式调用 `InputMethodManager.hideSoftInputFromWindow()`；Robolectric 覆盖两个入口，真实 IME 尚未验证。
- 下一步：设备上分别输入 URL 和搜索词，用软键盘动作键提交，确认网页开始加载且键盘收起；再点输入框确认仍能唤起键盘。
