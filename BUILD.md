# LightNovelReader iOS 构建与打包指南

## 一、环境要求

- **Xcode 16+**（当前工程用 Xcode 26.6 验证通过）
- **JDK 17+**（仅 KMP 数据互通层需要，见「KMP 构建」；纯 Swift 构建不需要）
- **XcodeGen**（可选，仅当你用 `project.yml` 重新生成工程时需要）

> 本机（2026-08）状态：Xcode 26.6、JDK 17（Temurin，`~/Library/Java/JavaVirtualMachines`）、
> Gradle 8.13（`~/bin/gradle`）、XcodeGen 2.46（`~/bin/xcodegen`）均已装好。
> `shared/` 的 KMP 模块已编译并合并出 `Frameworks/SharedKit.xcframework` 接入工程，
> `KmpBookSourceAdapter` 的 KMP 路径已激活；纯 Swift 兜底仍可用。

## 二、纯 Swift 构建（不需要 JDK/KMP）

```bash
# 模拟器构建
xcodebuild -project LightNovelReader.xcodeproj -scheme LightNovelReader \
  -destination 'generic/platform=iOS Simulator' -configuration Debug build CODE_SIGNING_ALLOWED=NO

# 真机构建（需在 Xcode 里选开发者账号，或命令行指定签名参数）
xcodebuild -project LightNovelReader.xcodeproj -scheme LightNovelReader \
  -destination 'generic/platform=iOS' -configuration Release build

# 跑单元测试（7 个）
xcodebuild -project LightNovelReader.xcodeproj -scheme LightNovelReaderTests \
  -destination 'platform=iOS Simulator,name=iPhone 17' -configuration Debug test CODE_SIGNING_ALLOWED=NO
```

打开工程用 `open LightNovelReader.xcodeproj`，选模拟器按 **Command+B** / **Command+R**。

## 三、KMP 数据互通层（SharedKit，与上游 Android 版数据互通）

`shared/` 是 Kotlin Multiplatform 模块，移植了上游 LightNovelReader `:api` 的
数据模型（`BookInformation`/`BookVolumes`/`ChapterContent`/`UserReadingData`/`Bookshelf`/`Identifier`）
和 wenku8 抓取器（Ktor Darwin + Ksoup + 手写 GBK 编码/GB18030 解码），
导出 `SharedKit.xcframework` 供 iOS 侧 `KmpBookSourceAdapter` 使用。

### 构建 SharedKit（需要 JDK 17+）

```bash
# 1. 装 JDK 17（二选一）
brew install --cask zulu@17
# 或 brew install openjdk@17

# 2. 构建 SharedKit.xcframework（自动用 gradlew 编译 iosArm64/iosSimulatorArm64 并合并）
./scripts/build-sharedkit.sh

# 3. 产物在 shared/build/bin/SharedKit.xcframework，已复制到 Frameworks/ 并接入工程
```

已验证的工具链版本（2026-08）：Kotlin **2.3.21**、Ktor **3.5.2**、kotlinx-coroutines **1.11.0**、
kotlinx-serialization **1.9.0**、Ksoup **0.2.6**、kotlin-result **2.3.1**（group
`com.michael-bull.kotlin-result`，Kotlin 2.3 的 ABI 需 Kotlin ≥ 2.3.10，故未沿用旧版 Kotlin 2.1.20）。
依赖坐标见 `shared/gradle/libs.versions.toml`。

接入方式：`project.yml` 里 LightNovelReader target 的 `dependencies` 加了
`framework: Frameworks/SharedKit.xcframework`（embed），`xcodegen generate` 重新生成工程。
KMP 代码有改动时需重新执行 `./scripts/build-sharedkit.sh` 并重新 `xcodegen generate`。

### 上游同步

上游项目（dmzz-yyhyy/LightNovelReader）更新后如何跟着更新、有哪些出错点，
见仓库根目录 **`SYNC.md`**（映射表 + 同步流程 + 风险清单）。

### 踩坑记录

从纯 Swift 迁移到 KMP 过程中踩过的坑（GBK 解码、ABI 版本、Ksoup 无 XPath、
Swift 桥接类型、Xcode embed 静态库等 19 条，每条含现象/原因/解法），
见 **`docs/KMP-TROUBLESHOOTING.md`**。KMP 开发遇到类似问题先查这里。

### Ksoup 无 XPath 的说明

Ksoup 0.2.x 不含 XPath 支持（jsoup 的 `JsoupXpath` 是额外模块），
`shared/src/commonMain/.../wenku8/KsoupXpath.kt` 提供了覆盖上游用到的 XPath 子集的
轻量翻译器（`//*[@id=...]` 锚点 + 子路径 + `tag[n]` 索引，自动跳过 `tbody` 层）。
注意：该文件请勿使用中文注释（Kotlin/Native 对 UTF-8 注释解析有坑，曾导致
`Unclosed comment` 误报），保持纯 ASCII 注释。

### 回退机制

`KmpBookSourceAdapter.swift` 用 `#if canImport(SharedKit)` 条件编译：
- SharedKit 编译进工程 → 用 KMP 适配器（与上游数据层同源，书 ID 格式 `wk8-<aid>` 一致）；
- 未编译 SharedKit → 自动回退现有纯 Swift 爬虫 `Wenku8Service`，App 照常运行。

### KMP 与上游的对应关系

| iOS 侧 | 上游 Android 版 | 说明 |
|---|---|---|
| `BookInformation` | `:api` 同名 | `coverUrl` 替代 `android.net.Uri` |
| `BookVolumes`/`Volume`/`ChapterInformation` | `:api` 同名 | 卷→章层级，适配层映射为扁平目录 |
| `ChapterContent.content` JSON | `ContentBuilder` 输出 | 组件格式 `lightnovelreader:simple_text`/`image` 完全一致 |
| `UserReadingData`/`Bookshelf` | `:api` 同名 | 阅读进度/书架互通预留 |

## 四、打包 IPA（真机）

1. Xcode 打开工程 → 选择真机设备 → 菜单 Product → **Archive**；
2. Organizer 里选最新 Archive → **Distribute App** → 选签名方式（个人开发者账号选
   "Development" 或 "Ad Hoc"）→ 导出 IPA；
3. 命令行方式（无签名构建产物，供测试）：
   ```bash
   xcodebuild -project LightNovelReader.xcodeproj -scheme LightNovelReader \
     -destination 'generic/platform=iOS' -configuration Release archive \
     -archivePath build/LightNovelReader.xcarchive CODE_SIGNING_ALLOWED=NO
   ```

## 五、工程配置

- 部署目标 **iOS 16.0**（KMP 框架对 iOS 16 无额外约束，保留即可）；
- Bundle ID `com.komorebiiluv.LightNovelReader`，`CODE_SIGN_STYLE = Automatic`；
- 字体 `UIAppFonts` 已配置 NotoSerifSC + LXGWWenKai（各约 24MB，未子集化，保留生僻字）；
- 封面 `img.wenku8.com` 为 http，Info.plist 已配 ATS 例外。

## 六、预期内警告（可忽略）

- `appintentsmetadataprocessor: Metadata extraction skipped`（无 AppIntents 依赖）；
- 封面服务器 http 的 ATS 例外已配置，无实际警告。
