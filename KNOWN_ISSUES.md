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

### Lint 仍有 34 个 warnings

- 状态：已确认，0 errors / 34 warnings。
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

### 活动视频选择是启发式策略

- 状态：已确认设计限制，未定义为缺陷。
- 当前策略：`video_bridge.js` 只扫描当前 document 的 `<video>`，优先视口内面积最大的播放视频；没有播放视频时为控制操作回退到最大可见视频。
- 影响：iframe、shadow DOM、多个同时播放视频、自定义 canvas 播放器和复杂 CSS transform 页面可能无法识别或选择错误。
- 下一步：在真实目标站点采样后再决定是否扩展 renderer；不要先抽象不存在的公共 pipeline。

### CSS bounds 与 MotionEvent 坐标尚未做设备矩阵验证

- 状态：代码已按 `WebView.scale` 转换，设备证据缺失。
- 影响：不同 density、zoom、横竖屏、窗口 inset 和页面 transform 可能使视频区域边界出现偏移。
- 下一步：在多种屏幕尺寸和 WebView zoom 下记录 rect、scale、MotionEvent 坐标，必要时补 instrumentation 断言。

### JS bridge 依赖页面生命周期和 WebView 实现

- 状态：generation、attach/detach 和 R8 keep 已处理，站点级兼容性未完全验证。
- 影响：SPA、频繁替换 video、旧 WebView 对 `MutationObserver`/`WeakSet`/`querySelectorAll().forEach` 的支持差异，可能使活动态更新滞后。
- 下一步：在 Android 8/API 26 及当前 target 设备上验证；不要把 `node --check` 当作 WebView 行为测试。
