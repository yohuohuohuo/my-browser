# Yobrowser Verification

## 最新验证快照

- 验证日期：2026-09-17
- 工作树：`feature/yobrowser-impl`，基线 `5c602b8`；本次视频控制栏修改未提交
- 运行环境：macOS 本地 Gradle/Android SDK；无可用 ADB 设备

## 已通过

### Gradle 全量验证

命令：

```bash
./gradlew testDebugUnitTest assembleDebug assembleRelease lint
```

结果：`BUILD SUCCESSFUL`，`101 actionable tasks: 21 executed, 80 up-to-date`。

- JVM 单测：91 tests，0 failures，0 errors；包括视频单击/双击、横滑 180s 上限，以及新增的首页搜索框和地址栏提交后隐藏输入法回归。
- Debug 构建：通过，产物为 `app/build/outputs/apk/debug/app-debug.apk`。
- Release 构建：通过，R8/resource shrinking 通过，产物为 `app/build/outputs/apk/release/app-release-unsigned.apk`。
- Lint：0 errors，35 warnings（新增控制栏背景 `Overdraw` 提示；尚未设备确认实际绘制开销）。
- Lint 报告：`app/build/reports/lint-results-debug.html` 和 `app/build/reports/lint-results-debug.txt`。

### 静态检查

```bash
node --check app/src/main/assets/video_bridge.js
node --test app/src/test/js/video_bridge.test.js
git diff --check
```

三项均通过。Node 测试 3/3，覆盖暂停视频活动态、尚未启动视频的网页入口，以及多视频页面暂停后控制目标不漂移；它不能证明 Android WebView 中的 DOM、媒体和 JavaScript interface 行为。

## 未完成验证

### ADB/设备

命令：

```bash
adb devices
```

结果：2026-09-17 再次执行时 ADB server 无法启动，日志包含 `could not install *smartsocket* listener: Operation not permitted`，因此没有真实设备或 emulator 可用于安装和交互验证。

未验证项目包括：

- 普通网页点击和活动视频区域外触摸透传。
- CSS `getBoundingClientRect()`、`WebView.scale` 与 MotionEvent 坐标的设备适配。
- 视频播放、长按、双击、seek、亮度/音量、滚动后的 bounds 更新。
- 单次横滑快进最多 180s、达到上限后反向滑动扣回及松手后重新计额在真实 WebView 上的行为。
- 单击显示浏览器控制栏且不改变播放状态、双击暂停/恢复、控制栏进度拖动和 3 秒自动隐藏在真实网站上的表现。
- 全屏 custom view、系统栏、返回键、页面历史的真实交互。
- 全屏横屏/竖屏视频的真实自动旋转，以及退出全屏后的方向恢复。
- 连续网页滚动期间的 GPU/WebView 实际重影表现，以及 300ms 防抖和触摸生命周期保护在不同系统导航模式下的实际时序。
- Home 按钮在真实设备上的点击区域、返回启动页行为、启动页隐藏地址栏、搜索框清空，以及网页加载进度条在地址栏隐藏/显示和真实网页加载过程中的位置与动画表现。
- 不同 Android System WebView、网页脚本和输入控件类型下，页面输入框是否都能通过触摸建立输入法连接；主页按钮清空地址栏后的真实焦点和键盘状态。
- 首页搜索框和地址栏用软键盘动作键提交时，真实输入法是否随网页加载自动收起，以及再次点输入框能否正常唤起。
- SSL/HTTP 错误页面和 renderer gone 后的实际 WebView 重建。
- API 26、不同 density、横竖屏和不同系统 WebView 版本。

## 发布边界

- 当前 Release APK 是 unsigned，不能直接发布。
- 当前没有正式签名、安装覆盖、升级兼容和线上站点验收证据。
- Lint warnings 尚未逐条归类。
- 任何 agent 后续若改变 WebView 安全策略、视频坐标换算、bridge callback 或全屏生命周期，都必须重新运行本文件中的 Gradle/静态检查，并追加设备证据。
