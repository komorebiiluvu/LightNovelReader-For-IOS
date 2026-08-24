# 云 Mac 构建验证指南

本工程为 SwiftUI iOS 应用,需要 Xcode 14+ 和 XcodeGen。

## 一、首次构建步骤

1. 装 Xcode(App Store),启动一次并同意许可协议。
2. 装 Homebrew(如果没有):
   ```bash
   /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
   ```
3. 装 XcodeGen:
   ```bash
   brew install xcodegen
   ```
4. 终端进入本目录(`ios/`),生成工程:
   ```bash
   xcodegen generate
   ```
5. 打开工程:
   ```bash
   open LightNovelReader.xcodeproj
   ```
6. Xcode 顶部选任意 iPhone 模拟器,按 **Command+B** 构建。

## 二、如何反馈构建结果

- **有报错**:打开 Xcode 左侧 Issue Navigator(⚠️图标),把所有错误条目原样复制;
  或菜单 Product → Build → 复制构建日志中的 `error:` 行。连同文件名行号一起发回。
- **构建成功**:按 **Command+R** 运行,截图 App 各页面(阅读/书架/探索/设置)。

## 三、运行说明

- 默认数据源是模拟源("文库8"),离线可用,先验证 UI 和交互;
- 设置 → 数据源 切到「文库8(在线)」测真实数据(需联网,精选书单 9 本,真实封面/目录/正文);
- 阅读器:左/右 1/3 屏点击翻页,中间呼出/隐藏上下栏。

## 四、预期内警告(不是错误,可忽略)

- `onChange(of:)` 弃用警告(兼容 iOS 16 的写法);
- 封面服务器 img.wenku8.com 为 http,Info.plist 已配 ATS 例外(project.yml 内)。

## 五、如果 xcodegen 装不上

备选:新建一个 iOS App 工程(Product Name: LightNovelReader, Interface: SwiftUI,
最低版本 iOS 16),把本目录 `Sources/` 下全部文件拖进工程即可(Info.plist 的 ATS
配置需手动加:NSAppTransportSecurity → NSExceptionDomains → img.wenku8.com →
NSExceptionAllowsInsecureHTTPLoads = YES)。
