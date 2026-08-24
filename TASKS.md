# Yobrowser Tasks

以下任务按当前项目状态排序。完成一项后更新状态和 `HANDOFF.md`，不要把构建通过直接等同于发布就绪。

## P0：接管和运行时验证

- [ ] 在不丢失当前工作树的前提下审阅 `git diff` 和所有未跟踪文件，按“基础实现 / 生命周期与错误 / 视频手势 / 测试与构建”拆分提交边界；提交前由用户确认。
- [ ] 准备 Android 设备或 emulator，运行 `adb devices`，安装 `app/build/outputs/apk/debug/app-debug.apk`。
- [ ] 真机验证启动页、URL 加载、搜索、普通网页点击、返回键和启动页返回。
- [ ] 真机验证活动视频矩形外点击/滚动仍作用于网页，矩形内点击/滑动/长按/双击作用于视频；滚动视频位置后再次点击，确认 bounds 没有漂移。
- [ ] 真机验证视频全屏进入/退出、系统栏恢复、返回键优先退出全屏，以及从全屏返回网页历史。
- [ ] 真机验证页面加载失败、主 frame HTTP/SSL 错误、renderer crash/recreate；这些场景目前只有 JVM/静态证据。

## P1：发布前决策

- [ ] 配置正式签名、校验 release keystore 管理方式，并用签名后的 Release APK 做安装验证；当前 `app-release-unsigned.apk` 不能直接发布。
- [ ] 审阅 Lint 的 34 个 warnings，逐条决定修复、基线化或保留，并确保没有本次改动新增的告警。
- [ ] 明确明文流量策略：评估是否仍需要 Manifest 的 `usesCleartextTraffic="true"`；不要在没有站点兼容性证据的情况下放宽 `MIXED_CONTENT_NEVER_ALLOW`。
- [ ] 增加至少一组 `androidTest` 或设备测试脚本，覆盖 WebView touch routing 和全屏返回；JVM 测试不能模拟真实 Chromium 页面坐标与媒体行为。

## P2：兼容性与维护

- [ ] 在至少一个包含多个 `<video>` 的真实站点验证“最大播放视频”选择策略；记录 iframe、shadow DOM、自定义播放器和 CSS transform 场景的结果。
- [ ] 在真实设备上验证不同 density、WebView zoom、横竖屏和窗口尺寸下 `getBoundingClientRect()` 与 MotionEvent 坐标缩放。
- [ ] 评审 2026-08-19 的设计文档/实施计划，将已实现项从“待评审/待实现”同步为事实或明确标记为历史设计。
- [ ] 若后续继续扩大浏览器能力，先更新 `DECISIONS.md` 和项目范围，再引入新依赖或新权限。
