# 六项审查问题修复计划

授权范围：仅修复 6 项已确认问题及直接回归；在当前分支原地实施，不创建 worktree、不提交、不推送。采用现有结构，不改变 WebView 安全策略。

## 执行顺序与验收

1. 先将复现转成正式测试，运行并记录 RED；旧临时失败证据保留在 `/private/tmp/yobrowser-audit-20261108/`。
2. `BrowserFragment.replaceWebView()` 在销毁前统一退出全屏。测试验证旧 host/custom view 移除、overlay 归位、方向恢复、callback 仅一次及下一次全屏/返回可用。
3. `BrowserFragment` 在实例状态保存/恢复中使用 WebView `saveState/restoreState`，缺少历史时重载已保存 URL。测试覆盖 Activity 重建、前后历史、恢复失败回退、Home 后重建不复活页面；保留媒体用户手势要求。
4. `video_bridge.js` 统一以全屏视频优先选择控制目标。Node 测试覆盖所有控制/读取接口、全屏容器、退出全屏及原有暂停/首次点击语义。
5. `VideoGestureDetector` 从 overlay 获取当前触摸容器宽度。测试覆盖缩放后左右分区；保留阈值、垂直方向和底部网页控件保留区。
6. `UrlUtils` 先提取 authority，再移除 user info。测试覆盖 path/query/fragment 的 @、credentials、端口、IPv4、IPv6、IDN 和无效 host。
7. `BrowserWebViewClient.doUpdateVisitedHistory()` 同步当前 WebView 的地址，`BrowserFragment` 区分文档加载 URL 与当前 history URL。测试覆盖 fragment/pushState/back、旧回调隔离、加载中同文档更新不阻断完成和 bridge generation 保持。
8. 定向测试通过后运行完整 Gradle、两份 JS 语法、Node 和 diff 检查；检查设备可用性。更新 HANDOFF/VERIFICATION/TASKS/KNOWN_ISSUES 等过时事实。

## 共享边界

- 2 与 7 共用当前 URL：恢复使用最新 history URL，文档完成回调不能覆盖它。
- 1 与 3 共用 WebView 清理：全屏清理必须在 activeWebView 变更前执行，Home 保存状态必须为空。
- 4 与 5 保留已有普通触摸透传、原始 DOWN/UP、底部控件区和 page/attach generation。

## 进度与证据

- 接管：`feature/yobrowser-impl` / `1cc11a2`；原有工作树仅 `?? .memsearch/`，不触碰。
- 本文件作为本轮执行记录；具体验证结果完成后同步 `VERIFICATION.md`。
- RED：正式 JVM 定向 38 tests / 7 failures；Node 新增 2 条均失败，原用例通过。证据：`jvm-red.log`、`node-red.log`、`red-test-results/`。
- GREEN：完整 JVM 111/111、Node 6/6。夹具修正：ShadowWebView 默认 loadUrl 不提交历史，正式历史测试用其 pushEntryToHistory 模拟平台提交；未弱化断言。
- 全量：`./gradlew testDebugUnitTest assembleDebug assembleRelease lint` 成功；联网 Lint 0 errors / 35 warnings，27 条为依赖版本提示，8 条 Android/UI 告警。两份 JS 语法与 diff 检查通过。
- 复核：独立 reviewer 仅检查六项 diff 及直接回归，未发现可执行 P1/P2；无 Git 操作或额外扫描。
- 设备：独立只读 API 31 emulator 启动成功，当前 System WebView 91.0.4472.114。本地 @ 路径、Hash/pushState/back、UI mode 切换后的当前页/返回历史 smoke 已通过；其余结果见 `VERIFICATION.md`。
- 设备收尾：B 全屏 seek 仅记录 B 的 seeking、横屏 x=700 触发亮度、全屏 renderer crash 恢复 URL/页面/系统栏和竖屏、后续全屏/返回、重建暂停和 Home 不复活均已完成有限 smoke。已关闭本任务服务、adb reverse 和只读 emulator，保留日志及截图；未操作真机或真实站点。
- 六项及必要验证完成。无新架构/安全策略或依赖改动；未 commit/push，未触碰原 `.memsearch/`。待办仅为原有签名、告警处置和真实设备/站点矩阵。
