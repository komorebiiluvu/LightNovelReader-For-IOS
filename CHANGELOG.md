# Changelog

本项目的版本变更记录。格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [1.1.0] - 2026-08-25

### Added

- **KMP 数据互通层（SharedKit）**：新增 `shared/` Kotlin Multiplatform 模块，
  从上游 [LightNovelReader](https://github.com/dmzz-yyhyy/LightNovelReader) 移植数据模型
  （`BookInformation`/`BookVolumes`/`ChapterContent`/`UserReadingData` 等）与 wenku8 抓取器，
  编译为 `SharedKit.xcframework` 供 iOS 侧调用。书 ID、组件 JSON 格式与上游完全一致。
- **探索页对齐上游**：6 个栏目（轻小说列表/热门/动画化/今日更新/新书一览/完结全本）、
  首页推荐块、48 个标签，数据全部来自 KMP 层（对齐上游 `Wenku8ExplorePageProvider`）。
- **完整 GBK 解码表**：23940 码位（GB2312 6763 + GBK 扩展繁体/日文汉字），
  修复中文书名/作者乱码。
- **`SYNC.md` 上游同步手册**：上游更新后的映射表 + 同步流程 + 风险清单。
- **`docs/KMP-TROUBLESHOOTING.md` 排错手册**：19 条踩坑记录（现象/原因/解法）。
- **XCUITest UI 测试**：书架/探索/设置 Tab 导航验证 + 真实数据验证。

### Changed

- `BookSourceService` 协议扩展：新增登录能力（`isLoggedIn`/`login`/`logout`/`savedCookie`）、
  `tagCategory`/`fetchBookDetail`，协议改为 class-bound。
- `AppStore` 解耦：登录态、探索、详情、备份全部走协议，不再强转 `Wenku8Service`。
- 自动重试登录：启动快速尝试 + 后台重试 + 探索加载失败自动重登（用户无需手动登录）。
- 探索加载改为后台预加载，不阻塞启动 splash。
- 设置页精简冗余小字说明。

### Fixed

- 中文书名/作者乱码（GBK 解码表不完整）。
- 探索页显示"书目 0 本"（KMP 适配器未实现探索方法）。
- 模拟器启动被登录重试拖慢 31 秒（改为后台重试）。
- UI 测试被 splash 遮罩拦截点击（等 splash 消失再点）。
- 打包时 app 内残留依赖临时路径的动态库（已移除，静态链接进主二进制）。

### Security

- 内置 wenku8 账号仍硬编码（与上游一致）；如担心泄露可改为用户手动登录（UI 入口已留方法，未接 UI）。

## [1.0.0] - 2026-08-24

### Added

- 基于 SwiftUI 的 iOS 轻小说阅读器（iPhone/iPad）。
- 书架、探索、设置三大 Tab。
- 阅读器：仿真卷曲翻页、滚动模式、字体/字号/行距/背景调节、OLED 纯黑夜间模式。
- 离线缓存、EPUB/TXT 导出、阅读统计。
- 封面下采样、异步分页 + 缓存 + 防抖（性能优化）。
