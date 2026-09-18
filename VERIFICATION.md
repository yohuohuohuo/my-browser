# Yobrowser Verification

## 最新验证快照

- 验证日期：2026-09-18
- 工作树：`feature/yobrowser-impl`，基线 `3e38edf`；视频触摸路由与本次媒体生命周期修复均未提交
- 运行环境：macOS 本地 Gradle/Android SDK；无可用 ADB 设备

## 已通过

### Gradle 全量验证

命令：

```bash
./gradlew testDebugUnitTest assembleDebug assembleRelease lint
```

结果：`BUILD SUCCESSFUL`，`101 actionable tasks: 21 executed, 80 up-to-date`。

- JVM 单测：102 tests，0 failures，0 errors；本次新增 Home/返回键卸载旧 WebView、旧页面及全屏回调隔离、旧导航完成回调隔离、后台暂停与错误页完成/重试回归。此前原始单击透传、全屏滑动等测试仍通过。
- Debug 构建：通过，产物为 `app/build/outputs/apk/debug/app-debug.apk`。
- Release 构建：通过，R8/resource shrinking 通过，产物为 `app/build/outputs/apk/release/app-release-unsigned.apk`。
- Lint：0 errors，35 warnings；告警尚未逐条归类。
- Lint 报告：`app/build/reports/lint-results-debug.html` 和 `app/build/reports/lint-results-debug.txt`。

### 静态检查

```bash
node --check app/src/main/assets/video_bridge.js
node --check app/src/main/assets/media_pause.js
node --test app/src/test/js/*.test.js
git diff --check
```

两份 JS 的语法检查、Node 测试和 `git diff --check` 均通过；Node 测试 4/4，其中新增主文档 audio/video、同源 frame 和跨域 frame 异常隔离用例。上述结果不能证明 Android WebView 中的媒体行为。

## 未完成验证

### ADB/设备

命令：

```bash
adb devices
```

结果：2026-09-17 再次执行时 ADB server 无法启动，日志包含 `could not install *smartsocket* listener: Operation not permitted`，因此没有真实设备或 emulator 可用于安装和交互验证。用户对上一版控制栏重叠与控件常驻提供了实测反馈，但本次改动后的 APK 尚未经设备复测。

未验证项目包括：

- 普通网页点击和活动视频区域外触摸透传。
- CSS `getBoundingClientRect()`、`WebView.scale` 与 MotionEvent 坐标的设备适配。
- 视频播放、长按、双击、seek、亮度/音量、滚动后的 bounds 更新。
- 单次横滑快进最多 180s、达到上限后反向滑动扣回及松手后重新计额在真实 WebView 上的行为。
- 单击是否唤起并正确自动隐藏网页原生/自制控制栏、是否保持播放状态；双击暂停/恢复、长按 3x、横滑 seek、网页进度条拖动及底部控制区域透传在真实网站上的表现。
- 全屏 custom view、系统栏、返回键、页面历史的真实交互。
- 全屏横屏/竖屏视频的真实自动旋转，以及退出全屏后的方向恢复。
- 连续网页滚动期间的 GPU/WebView 实际重影表现，以及 300ms 防抖和触摸生命周期保护在不同系统导航模式下的实际时序。
- Home 按钮在真实设备上的点击区域、返回启动页行为、启动页隐藏地址栏、搜索框清空，以及网页加载进度条在地址栏隐藏/显示和真实网页加载过程中的位置与动画表现。
- 不同 Android System WebView、网页脚本和输入控件类型下，页面输入框是否都能通过触摸建立输入法连接；主页按钮清空地址栏后的真实焦点和键盘状态。
- 首页搜索框和地址栏用软键盘动作键提交时，真实输入法是否随网页加载自动收起，以及再次点输入框能否正常唤起。
- Home/返回主页销毁旧 WebView 后真实网页、跨域 iframe、全屏视频的音频是否立即停止；后台/错误页/导航离开时媒体是否暂停、回到前台是否保持暂停。跨域 frame 与非 HTMLMediaElement 播放器尤其需要实测。
- SSL/HTTP 错误页面和 renderer gone 后的实际 WebView 重建。
- API 26、不同 density、横竖屏和不同系统 WebView 版本。

## 发布边界

- 当前 Release APK 是 unsigned，不能直接发布。
- 当前没有正式签名、安装覆盖、升级兼容和线上站点验收证据。
- Lint warnings 尚未逐条归类。
- 任何 agent 后续若改变 WebView 安全策略、视频坐标换算、bridge callback 或全屏生命周期，都必须重新运行本文件中的 Gradle/静态检查，并追加设备证据。
