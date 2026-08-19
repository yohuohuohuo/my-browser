# Yobrowser 设计文档

> 日期：2026-08-19
> 状态：待评审

## 1. 项目概述

Yobrowser 是一款极简 Android 手机浏览器，核心目标是提供流畅的网页浏览体验和增强的视频手势控制。

### 1.1 核心功能

1. **视频手势增强**：重写网页视频播放器的交互，支持长按倍速（3x）、左右滑动快进/快退、双击暂停/播放、上下滑动调节亮度/音量。
2. **UA 切换**：支持移动端 / PC 端浏览器标识一键切换，切换后自动刷新页面。
3. **沉浸式浏览**：顶部地址栏与 UA 按钮同一行，网页滚动时自动隐藏，最大化屏幕使用率。

### 1.2 非目标（MVP 不做）

- 多标签页管理
- 书签、历史记录
- 下载管理
- 广告拦截
- 隐私/无痕模式
- 深色模式
- 多搜索引擎切换

## 2. 技术选型

| 维度 | 选择 | 理由 |
|------|------|------|
| 开发语言 | Kotlin | Android 官方推荐，简洁安全 |
| 浏览器内核 | 系统 WebView | 开发成本低，兼容性好 |
| 架构模式 | MVVM + 手动 DI | 页面少，无需 Hilt 等重型框架 |
| 构建配置 | Gradle Kotlin DSL + Version Catalog | 现代标准做法 |
| minSdk | API 26（Android 8.0） | 覆盖绝大多数设备 |
| targetSdk | API 34（Android 14） | 遵循最新规范 |
| 包名 | com.yohuohuohuo.browser | |
| 应用名 | Yobrowser | |

## 3. 整体架构

### 3.1 架构模式

单 Activity + 单 Fragment + MVVM。

- `MainActivity`：唯一入口，处理返回键、权限、生命周期。
- `BrowserFragment`：浏览主界面，承载 WebView + 顶部栏 + 视频手势层。
- `BrowserViewModel`：管理浏览状态、UA 状态、页面加载状态。
- 启动页（快捷搜索）与浏览页在同一 Fragment 内通过 ViewModel 状态切换。

### 3.2 模块划分

```
com.yohuohuohuo.browser
├── MainActivity                    # 唯一入口
├── BrowserFragment                 # 浏览主界面
├── BrowserViewModel                # MVVM 核心
├── ui/
│   ├── UrlBarView                  # 顶部栏：地址框 + UA 按钮（同一行）
│   ├── VideoGestureOverlay         # 视频手势覆盖层
│   └── StartupView                 # 启动页：快捷搜索
├── web/
│   ├── WebViewManager              # WebView 配置与生命周期
│   ├── BrowserWebChromeClient      # 全屏回调、进度回调
│   └── BrowserWebViewClient        # 页面加载、URL 拦截、错误处理
├── gesture/
│   ├── VideoGestureDetector        # 手势识别
│   └── VideoJsBridge               # JS 注入与通信
├── ua/
│   └── UaManager                   # UA 切换与持久化
└── util/
    ├── UrlUtils                    # URL 识别
    └── SearchEngine                # 搜索引擎配置（百度）
```

## 4. 核心组件设计

### 4.1 VideoGestureOverlay（视频手势覆盖层）

覆盖在 WebView 上方的透明 FrameLayout。页面无活跃视频时完全透传事件，检测到视频时激活手势检测。

#### 手势映射

| 手势 | 行为 | 实现 |
|------|------|------|
| 长按（>500ms） | 3 倍速播放，松开恢复原速 | OnLongPressListener + JS playbackRate |
| 水平滑动 | 快进/快退，距离正比（约 1px = 0.5s） | onScroll + JS currentTime |
| 双击 | 暂停/播放切换 | OnDoubleTapListener + JS paused |
| 左侧垂直滑动 | 调节屏幕亮度 | onScroll + Window.screenBrightness |
| 右侧垂直滑动 | 调节媒体音量 | onScroll + AudioManager |

#### 视觉反馈

- 长按：屏幕中央显示"3x"半透明图标
- 水平滑动：进度预览（当前时间 / 总时长 + 进度条）
- 垂直滑动：亮度/音量进度条
- 所有反馈 1.5 秒后自动消失

#### 激活条件

`VideoJsBridge` 检测到页面存在 `<video>` 元素且视频可见时，通过回调通知 overlay 激活。

### 4.2 VideoJsBridge（JS 注入与通信）

#### 注入时机

`WebViewClient.onPageFinished` 时注入。

#### 注入脚本职责

- 遍历页面 `<video>` 元素，定位当前播放/可见的主视频。
- 暴露 JS 函数：`play()`, `pause()`, `seek(seconds)`, `setRate(rate)`, `getCurrentTime()`, `getDuration()`。
- 监听视频事件（play/pause/ended/visibilitychange/fullscreenchange），通过 JavascriptInterface 回调原生层。
- 全屏切换时通知原生调整 overlay 尺寸。

#### 通信方式

- 原生 → JS：`webView.evaluateJavascript("window.__videoBridge.play()", callback)`
- JS → 原生：`@JavascriptInterface` 注解的 `VideoJsCallback` 接口

### 4.3 UrlBarView（顶部地址栏）

#### 布局

同一行从左到右：安全图标 → 地址输入框（权重占满）→ UA 切换按钮。

#### 行为

- 非输入态：显示当前网页标题或 URL。
- 点击进入输入态：全选当前 URL，弹出键盘。
- 回车：`UrlUtils` 判断 URL / 搜索词，URL 直接加载，搜索词拼接百度搜索 URL。
- UA 按钮：点击切换移动端/PC 端，保存设置并 reload。

#### 自动隐藏

- 监听 `WebView.setOnScrollChangeListener`。
- 向下滚动：隐藏（-toolbarHeight 偏移动画）。
- 向上滚动 / 滚动到顶部：显示。
- 视频全屏：隐藏。
- 沉浸式状态栏下处理 `WindowInsets` 避开状态栏区域。

### 4.4 UaManager（UA 管理）

- 移动端 UA：系统 WebView 默认 UA。
- PC 端 UA：桌面 Chrome UA，例如 `Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36`。
- 持久化：`SharedPreferences` 存储当前模式，启动时恢复。
- 切换：`webView.settings.userAgentString = newUa` + `webView.reload()`。

## 5. 数据与状态

### 5.1 UiState

```kotlin
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

### 5.2 数据流

```
用户操作 → UrlBarView/VideoGestureOverlay → BrowserViewModel → WebViewManager/VideoJsBridge
                                                          ↓
                                              WebView 加载/视频控制
                                                          ↓
                          WebViewClient/WebChromeClient 回调 → BrowserViewModel
                                                          ↓
                                                   StateFlow 更新
                                                          ↓
                                              Fragment 观察 → 更新 UI
```

## 6. 关键交互流程

### 6.1 输入网址/搜索

1. 用户点击地址栏 → 进入输入态，全选现有内容。
2. 用户输入文本 + 回车。
3. `UrlUtils.isUrl(input)` 判断：
   - URL → `webView.loadUrl(input)`
   - 搜索词 → `https://www.baidu.com/s?wd=<encoded input>`
4. `showStartup = false`，显示 WebView，开始加载。

### 6.2 视频手势控制（以长按倍速为例）

1. `VideoGestureOverlay` 检测到长按。
2. 显示"3x"视觉反馈。
3. `VideoJsBridge.setRate(3.0f)` → `evaluateJavascript("window.__videoBridge.setRate(3)")`。
4. 用户松开 → `VideoJsBridge.setRate(1.0f)` 恢复原速。
5. 隐藏视觉反馈。

### 6.3 UA 切换

1. 用户点击 UA 按钮。
2. `UaManager.toggle()` → 保存新模式。
3. `webView.settings.userAgentString = UaManager.getCurrentUa()`。
4. `webView.reload()`。
5. UiState.uaMode 更新，按钮图标切换。

### 6.4 返回键

1. `MainActivity.onBackPressed()`。
2. `webView.canGoBack()` → `webView.goBack()`。
3. 否则：启动页 → 退出 App；浏览页 → 回到启动页。

## 7. 错误处理

| 场景 | 处理 |
|------|------|
| 网页加载失败 | onReceivedError → UiState.error → 错误页（含重试按钮） |
| SSL 证书错误 | 默认拒绝，提示用户选择是否继续 |
| JS 注入失败 | 静默降级，视频手势层不激活，正常浏览不受影响 |
| 页面无 video 元素 | videoActive=false，overlay 透传所有事件 |
| WebView 渲染进程崩溃 | onRenderProcessGone → 重建 WebView，恢复上次 URL |

## 8. 权限

仅需 `INTERNET` 权限。无需存储、定位、相机等权限。

## 9. 主题与 UI

- 浅色模式（仅浅色）。
- 主题色：灰黑色。
- 沉浸式状态栏：网页内容延伸到状态栏下方。
- 顶部栏自动隐藏/显示动画。

## 10. 测试策略

- **单元测试**：`UrlUtils`（URL 识别）、`UaManager`（UA 切换）、`VideoGestureDetector`（手势阈值）。
- **集成测试**：`BrowserViewModel` 状态流转（加载中 → 完成 → 错误）。
- **手动测试**：主流视频网站（B站、优酷、腾讯视频、YouTube）手势验证；PC/移动 UA 切换；横竖屏切换；全屏播放。
