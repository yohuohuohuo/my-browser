# Yobrowser Verification

## 最新验证快照

- 验证日期：2026-08-24
- 工作树：`feature/yobrowser-impl`，包含未提交改动
- 运行环境：macOS 本地 Gradle/Android SDK；无可用 ADB 设备

## 已通过

### Gradle 全量验证

命令：

```bash
./gradlew testDebugUnitTest assembleDebug assembleRelease lint
```

结果：`BUILD SUCCESSFUL`，`101 actionable tasks: 2 executed, 99 up-to-date`。

- JVM 单测：66 tests，0 failures，0 errors。
- Debug 构建：通过，产物为 `app/build/outputs/apk/debug/app-debug.apk`。
- Release 构建：通过，R8/resource shrinking 通过，产物为 `app/build/outputs/apk/release/app-release-unsigned.apk`。
- Lint：0 errors，34 warnings。
- Lint 报告：`app/build/reports/lint-results-debug.html` 和 `app/build/reports/lint-results-debug.txt`。

### 静态检查

```bash
node --check app/src/main/assets/video_bridge.js
git diff --check
```

两项均通过。`node --check` 只能证明 JavaScript 语法有效，不能证明 Android WebView 中的 DOM、媒体和 JavaScript interface 行为。

## 未完成验证

### ADB/设备

命令：

```bash
adb devices
```

结果：ADB server 无法启动，日志包含 `could not install *smartsocket* listener: Operation not permitted`，因此没有真实设备或 emulator 可用于安装和交互验证。

未验证项目包括：

- 普通网页点击和活动视频区域外触摸透传。
- CSS `getBoundingClientRect()`、`WebView.scale` 与 MotionEvent 坐标的设备适配。
- 视频播放、长按、双击、seek、亮度/音量、滚动后的 bounds 更新。
- 全屏 custom view、系统栏、返回键、页面历史的真实交互。
- SSL/HTTP 错误页面和 renderer gone 后的实际 WebView 重建。
- API 26、不同 density、横竖屏和不同系统 WebView 版本。

## 发布边界

- 当前 Release APK 是 unsigned，不能直接发布。
- 当前没有正式签名、安装覆盖、升级兼容和线上站点验收证据。
- Lint warnings 尚未逐条归类。
- 任何 agent 后续若改变 WebView 安全策略、视频坐标换算、bridge callback 或全屏生命周期，都必须重新运行本文件中的 Gradle/静态检查，并追加设备证据。
