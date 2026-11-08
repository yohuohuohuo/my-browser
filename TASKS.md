# Yobrowser Tasks

以下任务按当前项目状态排序。完成一项后更新状态和 `HANDOFF.md`，不要把构建通过直接等同于发布就绪。

## P0：接管和运行时验证

- [x] 2026-11-08 用户确认提交六项修复及测试/相关文档，排除 `.memsearch/`，并交付至主分支；接管基线为 `1cc11a2`。
- [ ] 提供目标 Git 仓库地址并推送 `main`；当前仓库没有远端配置。
- [x] 2026-11-08 经单次审批启动独立只读 API 31 emulator 并安装本轮 Debug APK；有限 smoke 结果见 `VERIFICATION.md`，不代替下面的真机矩阵。
- [ ] 真机验证启动页、URL 加载、搜索、普通网页点击、返回键和启动页返回。
- [ ] 真机验证活动视频矩形外点击/滚动仍作用于网页，矩形内点击/滑动/长按/双击作用于视频；滚动视频位置后再次点击，确认 bounds 没有漂移。
- [ ] 真机验证视频全屏进入/退出、系统栏恢复、返回键优先退出全屏，以及从全屏返回网页历史。
- [ ] 真机验证页面加载失败、主 frame HTTP/SSL 错误、renderer crash/recreate；renderer 已有 API 31 本地 emulator smoke，HTTP/SSL 错误仍以 JVM/静态证据为主。

## P1：发布前决策

- [ ] 配置正式签名、校验 release keystore 管理方式，并用签名后的 Release APK 做安装验证；当前 `app-release-unsigned.apk` 不能直接发布。
- [ ] 审阅 Lint warnings，逐条决定修复、基线化或保留；2026-11-08 联网报告为 35 个（27 个依赖版本提示、8 个 Android/UI 告警），离线计数可能不同，具体以 `VERIFICATION.md` 为准。
- [ ] 明确明文流量策略：评估是否仍需要 Manifest 的 `usesCleartextTraffic="true"`；不要在没有站点兼容性证据的情况下放宽 `MIXED_CONTENT_NEVER_ALLOW`。
- [ ] 增加至少一组 `androidTest` 或设备测试脚本，覆盖 WebView touch routing 和全屏返回；JVM 测试不能模拟真实 Chromium 页面坐标与媒体行为。

## P2：兼容性与维护

- [ ] 在多个 `<video>` 的真实站点验证全屏目标优先、退出后的可见暂停视频保留与首次普通模式选择；记录 iframe、shadow DOM、自定义播放器和 CSS transform 场景。
- [ ] 在真实设备上验证不同 density、WebView zoom、横竖屏和窗口尺寸下 `getBoundingClientRect()` 与 MotionEvent 坐标缩放。
- [ ] 评审 2026-08-19 的设计文档/实施计划，将已实现项从“待评审/待实现”同步为事实或明确标记为历史设计。
- [ ] 若后续继续扩大浏览器能力，先更新 `DECISIONS.md` 和项目范围，再引入新依赖或新权限。
