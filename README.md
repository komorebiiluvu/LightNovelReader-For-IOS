**简体中文** | [繁體中文](README_TW.md) | [English](README_US.md) | [Русский](README_RU.md)

<div align="center">
    <img src="assets/logo.png" alt="logo" width="120"/>
    <h1>LightNovelReader (iOS)</h1>
    <a><img alt="iOS" src="https://img.shields.io/badge/iOS-000000?logo=apple&logoColor=white&style=for-the-badge"/></a>
    <a><img alt="Swift" src="https://img.shields.io/badge/Swift-F05138?logo=swift&logoColor=white&style=for-the-badge"/></a>
    <a><img alt="SwiftUI" src="https://img.shields.io/badge/SwiftUI-0D7CFF?logo=swiftui&logoColor=white&style=for-the-badge"/></a>
    <a><img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white&style=for-the-badge"/></a>
    <a><img alt="KMP" src="https://img.shields.io/badge/KMP-Multiplatform-7F52FF?style=for-the-badge"/></a>
    <p>基于 SwiftUI + Kotlin Multiplatform 的 iOS 轻小说阅读器，支持 iPhone 与 iPad</p>
</div>

## 介绍

LightNovelReader (iOS) 是一款开源的轻小说阅读软件，使用 **Swift/SwiftUI（UI 层）+ Kotlin Multiplatform（数据层）** 编写，支持 iPhone 与 iPad。界面与交互参考 Android 版 [LightNovelReader](https://github.com/dmzz-yyhyy/LightNovelReader) 实现。

**数据层采用 Kotlin Multiplatform（KMP）**：从上游项目移植数据模型与 wenku8 抓取器，编译为 `SharedKit` 框架供 iOS 调用，实现与上游 Android 版**同源的数据层**（书 ID、组件 JSON 格式完全一致），便于跟随上游更新与数据互通。

> ### ❤️ 特别鸣谢
>
> **本项目深受开源项目 [LightNovelReader](https://github.com/dmzz-yyhyy/LightNovelReader) 的启发与指引，没有它就没有本项目。**
> 感谢作者 [dmzz-yyhyy](https://github.com/dmzz-yyhyy)（夜鱼很业余）与全体贡献者的杰出工作与无私开源。

## 特性

- 真实书源：在线浏览、搜索与阅读
- 阅读器：仿真卷曲翻页、滚动模式，字体/字号/行距/背景自由调节，OLED 纯黑夜间模式
- 书架：多书架管理，收藏与更新提醒
- 离线缓存：全书下载，断网可读
- 导出：EPUB / TXT
- 阅读统计：时长累计与热力图
- **KMP 数据互通**：数据层用 Kotlin Multiplatform 实现，与上游 Android 版同源（详见下方「技术架构」）
- **探索对齐上游**：6 大栏目（轻小说列表/热门/动画化/今日更新/新书一览/完结全本）、首页推荐、标签浏览
- **崩溃报告**：设置页可导出崩溃日志，方便反馈问题

## 技术架构

```
┌─────────────────────────────────────────────┐
│  SwiftUI UI 层（书架 / 探索 / 设置 / 阅读器）│
├─────────────────────────────────────────────┤
│  BookSourceService 协议（登录/搜索/探索/目录）│
├─────────────────────────────────────────────┤
│  KmpBookSourceAdapter  ←→  SharedKit (KMP)  │
│  （Swift 桥接，未编译 SharedKit 时回退 Swift）│
├─────────────────────────────────────────────┤
│  shared/ Kotlin Multiplatform 模块           │
│  模型 + wenku8 抓取器（对齐上游 :api）       │
└─────────────────────────────────────────────┘
```

- **数据模型**：`BookInformation`/`BookVolumes`/`ChapterContent`/`UserReadingData` 等，
  包名与上游 `:api` 模块一致，同步上游更新时可直接 diff 复制。
- **wenku8 抓取器**：Ktor(Darwin) + Ksoup + 手写 GBK 解码（Kotlin/Native 无内置 GB18030）。
- **文档**：[SYNC.md](SYNC.md)（上游同步手册）、[docs/KMP-TROUBLESHOOTING.md](docs/KMP-TROUBLESHOOTING.md)（踩坑记录）、[CHANGELOG.md](CHANGELOG.md)（版本记录）。

## 软件截图

| | | |
|---|---|---|
| ![1](assets/screenshots/01.png) | ![2](assets/screenshots/02.png) | ![3](assets/screenshots/03.png) |
| ![4](assets/screenshots/04.png) | ![5](assets/screenshots/05.png) | ![6](assets/screenshots/06.png) |
| ![7](assets/screenshots/07.png) | | |

## 下载

从 [GitHub Releases](https://github.com/komorebiiluvu/LightNovelReader-For-IOS/releases/latest) 下载最新发布版。发布包为未签名的 `.ipa` 文件，需自行签名后安装（可使用爱思助手、Sideloadly 等工具）。

## 支持

- 在 [**此处**](https://github.com/komorebiiluvu/LightNovelReader-For-IOS/issues/new/choose) 提交 Bug 反馈或新功能请求
- 联系作者：**QQ：`3662909214`**

## License

```
Copyright (C) dmzz-yyhyy (夜鱼很业余) and contributors of LightNovelReader
Copyright (C) 2026 komorebiiluvu (iOS Port)

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
```

完整许可文本见 [LICENSE](LICENSE)，内置字体的许可见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 免责声明

本应用仅为个人阅读工具，不提供、不存储任何小说内容；所有内容版权归原作者及站点所有，请支持正版。
