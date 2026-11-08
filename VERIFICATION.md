# Yobrowser Verification

## 最新验证快照

- 验证日期：2026-11-08
- 验证时工作树：`feature/yobrowser-impl`，HEAD/接管基线 `1cc11a2`；当时六项修复、测试和文档未提交，原有 `.memsearch/` 未改动。后续提交交付状态见 `HANDOFF.md`。
- 运行环境：macOS ARM64、Zulu Java 17、Android SDK、本地 Gradle 8.6 缓存；本轮以单次审批执行缓存写入和 emulator 操作
- 设备：独立只读 Pixel 2 API 31 ARM64 emulator，System WebView `91.0.4472.114`；未连接真机

## 已通过

### Gradle 全量验证

命令：

```bash
./gradlew testDebugUnitTest assembleDebug assembleRelease lint
```

结果：`BUILD SUCCESSFUL in 29s`，`101 actionable tasks: 24 executed, 77 up-to-date`。日志：`/private/tmp/yobrowser-fixes-20261108/full-verification.log`。

- JVM 单测：111 tests，0 failures，0 errors，0 skipped；新增 7 条生命周期/history、1 条窗口宽度和1 条 URL 参数化回归，原 102 条全部通过。
- 六项回归先验证 RED：定向 JVM 38 tests / 7 failures（全屏清理、当前页/历史恢复及 fallback、同文档地址/完成交叉、窗口宽度、@ 路径）；Node 新增两条全屏目标测试失败。修复后全套为 GREEN。RED 日志/XML 保留在 `/private/tmp/yobrowser-fixes-20261108/`，原临时审查证据未删除。
- Debug 构建：通过，产物为 `app/build/outputs/apk/debug/app-debug.apk`。
- Release 构建：通过，R8/resource shrinking 通过，产物为 `app/build/outputs/apk/release/app-release-unsigned.apk`。
- Lint：联网运行 0 errors、35 warnings，其中 27 条是依赖版本提示（AndroidGradlePluginVersion 3、GradleDependency 24），其余 8 条为 OldTargetApi 1、MergeRootFrame 1、Overdraw 4、ViewConstructor 1、ClickableViewAccessibility 1。此次没有修改依赖、SDK 目标或压制告警；8 条 Android/UI 告警的源位置此前已存在。此前离线报告的 8 warnings 与本次联网计数不同，不能混用。
- Lint 报告：`app/build/reports/lint-results-debug.html` 和 `app/build/reports/lint-results-debug.txt`。

### 静态检查

```bash
node --check app/src/main/assets/video_bridge.js
node --check app/src/main/assets/media_pause.js
node --test app/src/test/js/*.test.js
git diff --check
```

两份 JS 的语法检查、Node 测试 6/6 和 `git diff --check` 均通过。新增全屏目标的全部控制/时间读取与容器全屏/退出暂停目标保留用例；原有媒体暂停、首次点击透传与暂停态选择用例继续通过。Node 测试不等同 Android WebView 行为。

### 范围内独立代码复核

独立 reviewer 只读核对六项未提交改动、正式测试与关联调用，未发现有证据、可执行的 P1/P2；未扩大到全仓审查，也未重复全套测试。真实设备与真实站点的兼容性仍不由静态复核保证。

### API 合同依据

- [Android WebViewClient](https://developer.android.com/reference/android/webkit/WebViewClient)：fragment 导航不触发 onPageStarted，history 地址单独同步。
- [Android WebView 状态 API](https://developer.android.com/reference/android/webkit/WebView#restoreState(android.os.Bundle))：用 saveState/restoreState 保存/恢复导航历史，返回 null 时走 URL fallback；此 API 不保存页面显示数据，不代表 DOM、表单或媒体播放位置持久化。

## 设备验证范围

### ADB/emulator

命令：

```bash
adb devices
```

结果：本轮单次审批后 ADB server 正常启动；初始没有已连接设备。使用已有 API 31 ARM64 镜像，以 `-read-only -no-snapshot -no-window -no-audio` 启动独立 `emulator-5580`，Debug APK 安装成功。本地夹具仅监听 `127.0.0.1:43001` 并通过本任务 emulator 的 adb reverse 访问，未使用日常浏览器或真实站点。

已完成的有限 smoke：

- `http://127.0.0.1:43001/@alice` 直接加载，软键盘提交后收起；fragment/hash、pushState 和同文档返回同步地址栏。
- UI mode 切换触发重建后，当前页面及返回历史可用；Home 后再次重建仍为启动页，不复活旧 URL/历史。
- 先播放 A，再将 B 全屏；真实手势 seek 的夹具事件只出现 B 的 `seeking`，没有 A 的 `seeking`（`server.log` 16:26:25～26）。
- 全屏横屏 1920px 宽时，在 x=700 向上滑动触发左侧亮度调整，窗口 `sbrt` 从默认覆盖值变为 `0.6511429`；普通竖屏返回可用。
- 确认 isolated renderer PID 4725 与本浏览器 UID/service 绑定后，仅对该测试 renderer 执行 `am crash`。旧全屏画面移除，新 WebView 恢复 hash URL，页面可见、系统栏与竖屏恢复；后续仍可再次进入/退出全屏。证据：`activity-processes.txt`、`renderer-crash-logcat.txt`、`device-renderer-recovered.png`。
- 重建后本地视频显示为暂停态，未自动继续播放。本夹具为无声合成 MP4，不能证明音频、跨域 frame 或非 HTMLMediaElement 播放器的行为。

截图、UI XML、夹具、窗口状态和命令日志均保留于 `/private/tmp/yobrowser-fixes-20261108/`。临时回环服务、adb reverse 和只读 emulator 在验证后关闭；原 AVD 数据未改动。完整媒体/全屏矩阵见以下边界，本地单版本夹具不代表真实站点验收。

尚未完成的真机/真实站点矩阵包括：

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
