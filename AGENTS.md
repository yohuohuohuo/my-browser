# Yobrowser Agent Guide

## 项目边界

- 仓库：`/Users/qiuyupan/git_workspace/my-browser`
- Android application module：`app`
- 包名：`com.yohuohuohuo.browser`
- 当前工作分支：`main`（本轮修复由 `feature/yobrowser-impl` 快进合入）
- 本轮接管基线：`1cc11a2`；交付提交以 `git log` 为准，`.memsearch/` 不纳入本次提交。
- 当前状态：源码可以通过 JVM 单测、Debug/Release 构建和 Lint；最新验证及 emulator/真机边界见 `VERIFICATION.md`，Release 仍未签名。

## 新 agent 的阅读顺序

进入项目后按以下顺序读取，优先相信“当前代码事实”和最新验证报告：

1. `AGENTS.md`
2. `HANDOFF.md`
3. `PROJECT_CONTEXT.md`
4. `TASKS.md`
5. `DECISIONS.md`
6. `KNOWN_ISSUES.md`
7. `VERIFICATION.md`
8. `docs/superpowers/specs/2026-08-19-yobrowser-design.md` 和对应 plan（仅用于理解原始目标，不作为当前实现事实）

每次接管前先运行：

```bash
git status --short
git branch --show-current
git log -5 --oneline --decorate
```

## 工作树与 Git 规则

- 工作树已有用户改动。不要运行 `git reset --hard`、`git checkout --`、批量删除、无授权的 `stash` 或覆盖式生成命令。
- 修改前先记录 `git status --short`，并区分已跟踪修改、删除文件和未跟踪文件。
- 只修改当前任务涉及的文件；不要为了“整理”而重写其他未提交文件。
- 任何提交前先让用户确认提交边界；2026-11-08 用户已授权提交本轮六项修复、测试和相关文档，排除 `.memsearch/`，并推送到主分支。后续范围仍需另行确认。
- 本项目文档使用中文；代码中的 API、类名、命令和 Android 专有名词保持原文。

## 技术与构建约束

- Kotlin `1.9.24`、AGP `8.4.2`、Gradle Wrapper `8.6`。
- `minSdk = 26`、`targetSdk = 35`、`compileSdk = 35`。
- Java/Kotlin JVM target 为 17，使用 Gradle Kotlin DSL 和 Version Catalog。
- 架构为单 Activity + 单 Fragment + MVVM + 手动 DI；不要无明确需求引入 Hilt、Compose 或多模块重构。
- Release 开启 R8 和资源压缩；`app/proguard-rules.pro` 必须保留 WebView JavaScript bridge 的 `@JavascriptInterface` 方法。
- Manifest 当前只声明 `INTERNET` 权限，但 `android:usesCleartextTraffic="true"` 与 WebView 的 `MIXED_CONTENT_NEVER_ALLOW` 是需要后续产品决策的边界，不能擅自改成更宽松策略。

## 核心运行契约

### 浏览器主流程

- `MainActivity` 是唯一入口，负责 WindowInsets、系统栏和返回键分发。
- `BrowserFragment` 负责 WebView、启动页、地址栏、错误页、全屏 custom view、视频 bridge 的装配和生命周期。
- `BrowserViewModel` 通过 `StateFlow<UiState>` 管理 URL、标题、加载进度、UA、启动页、工具栏、视频活动态和错误态。
- `BrowserWebView` 将普通触摸交给 WebView；只有 `gestureHandler` 返回 `true` 时才由自定义手势接管，并在必要时向 WebView 发送 `ACTION_CANCEL`。

### 视频手势流程

```text
video_bridge.js
  -> 当前活动视频的 getBoundingClientRect()
  -> VideoJsBridge.onVideoBounds(generation, ...)
  -> BrowserFragment 按 WebView scale 转换
  -> VideoGestureOverlay 保存 RectF
  -> 仅视频区域内的 ACTION_DOWN 开始手势接管
```

- 普通模式下，视频区域外的 `ACTION_DOWN/MOVE/UP` 必须返回 `false`，保证网页点击和滚动可用。
- 一旦在视频区域内开始一条手势，后续移出区域仍保持消费到 `UP/CANCEL`。
- 全屏 custom view 使用 `enterFullscreenTouchMode()`，此时整个全屏 overlay 都是手势区域。
- 导航、视频 inactive、WebView replacement、Fragment view 销毁时必须清理 detector、bounds、bridge callback 和 pending runnable。
- JS bridge 的回调必须携带 page generation；旧页面回调不得重新激活新页面的视频层。

### WebView 与安全边界

- `WebViewManager` 开启 JavaScript、DOM storage 和必要的媒体控制，但禁用 file/content access，保留用户手势播放限制。
- `BrowserWebViewClient` 只对主 frame 报告加载错误、HTTP 错误和 SSL 错误；SSL 错误直接 cancel。
- `BrowserWebChromeClient` 负责进度和全屏 custom view，返回键优先退出全屏，再处理网页历史和启动页。
- `UrlUtils` 负责 URL/search 判断，当前覆盖 scheme、端口、localhost、IPv4、带方括号 IPv6 和 IDN 边界；不要仅凭字符串拼接扩大可识别范围。

## 标准验证命令

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew lint
node --check app/src/main/assets/video_bridge.js
git diff --check
```

完整验证证据见 `VERIFICATION.md`。构建通过不等于 WebView 交互通过；没有设备或 emulator 时必须明确标记为未验证。

## 完成任务前的交接要求

1. 更新 `HANDOFF.md` 的当前状态、修改文件、验证结果和剩余风险。
2. 若新增风险，补充 `KNOWN_ISSUES.md`；若改变架构或安全策略，先更新 `DECISIONS.md`。
3. 运行与改动相关的定向单测，再运行全量验证。
4. 用 `git diff --check` 检查空白错误，并再次确认没有误改或删除用户文件。
5. 最终回复中明确：改了什么、验证了什么、哪些仍需设备/线上验证、是否提交了 Git。
