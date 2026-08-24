# KMP 排错手册（踩坑记录）

> 本项目从纯 Swift 迁移到 Kotlin Multiplatform（KMP）数据层过程中踩过的坑。
> 每条记录格式：**现象 → 原因 → 解法 → 涉及文件**。
> 这些坑在 KMP + iOS 开发中高度常见，希望后来者少走弯路。

---

## 一、构建与工具链

### 1. Gradle 找不到 KMP 任务（`linkDebugFrameworkIosSimulatorArm64` not found）

**现象**：`./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` 报
`Cannot locate tasks that match ':shared:linkDebugFrameworkIosSimulatorArm64'`，
且 `tasks` 列表里没有任何 Kotlin/Native 任务。

**原因**：`settings.gradle.kts` 里写了 `include(":shared")`，但 `shared/` 本身就是
**独立的 Gradle 构建**（root project 就是 KMP 模块）。多余的 include 让 Gradle 去找
不存在的 `shared/shared/` 子项目，任务名带错前缀。

**解法**：删掉 `settings.gradle.kts` 里的 `include(":shared")`，只留 `rootProject.name`。
任务名用 `./gradlew linkDebugFrameworkIosSimulatorArm64`（无 `:shared:` 前缀）。

**涉及文件**：`shared/settings.gradle.kts`

### 2. Kotlin/Native 依赖下载失败（`no repositories are defined`）

**现象**：编译时报 `Cannot resolve external dependency org.jetbrains.kotlin:kotlin-native-prebuilt`。

**原因**：`settings.gradle.kts` 没有声明任何仓库（mavenCentral/google），Kotlin/Native
工具链和依赖都解析不到。

**解法**：在 `settings.gradle.kts` 加 `pluginManagement { repositories { ... } }` 和
`dependencyResolutionManagement { repositories { mavenCentral(); google() } }`。

**涉及文件**：`shared/settings.gradle.kts`

### 3. kotlin-result / ksoup 坐标或版本不存在

**现象**：`Could not find com.michael-bull:kotlin-result:2.0.1` 和
`com.fleeksoft.ksoup:ksoup:0.3.3`。

**原因**：
- kotlin-result 的真实 group 是 **`com.michael-bull.kotlin-result`**（不是 `com.michael-bull`）
- ksoup 0.3.3 不存在，实际最新是 0.2.6（版本号臆测导致）

**解法**：从 Maven Central 的 `maven-metadata.xml` 查真实坐标和版本，不猜。
```bash
curl -s https://repo.maven.apache.org/maven2/com/fleeksoft/ksoup/ksoup/maven-metadata.xml
```

**涉及文件**：`shared/gradle/libs.versions.toml`

### 4. kotlin-result ABI 不兼容（`incompatible ABI version '2.3.0'`）

**现象**：`The library was produced by '2.3.10' compiler. The current Kotlin compiler
can consume libraries having ABI version <= '1.201.0'.`

**原因**：kotlin-result 2.3.1 是用 Kotlin 2.3.10+ 编译的，而项目锁 Kotlin 2.1.20。
Kotlin/Native 的 klib ABI 版本向下兼容但**不向上兼容**——用旧编译器吃不了新库。

**解法**：把 Kotlin 升到与依赖匹配的版本（本项目升到 **2.3.21**），并同步升级
Ktor（3.5.2）、kotlinx-coroutines（1.11.0）、kotlinx-serialization（1.9.0）。
升级前先验证 `kotlin-native-prebuilt` 和 Gradle plugin 在目标版本存在。

**涉及文件**：`shared/gradle/libs.versions.toml`

### 5. Kotlin 编译器对超长数组/行解析失败

**现象**：把 23940 条 GBK 解码表写进 `intArrayOf(...)`，编译报
`Syntax error: Expecting ','`（每行 500+ 列处）。

**原因**：Kotlin 编译器（尤其 Kotlin/Native）对超大数组字面量有解析限制，
按行/块截断导致行尾缺逗号。

**解法**：不要用巨型数组字面量。改用**字符串常量 + 运行时解析**，且
**字符串拼接的 `+` 必须放在行尾**（Kotlin 不支持行首二元运算符续行）：
```kotlin
val TABLE: String =
    "33088,19970,1;33089,19972,3;" +
    "33092,19983,1;..."
```

**涉及文件**：`shared/src/commonMain/kotlin/io/nightfish/lightnovelreader/api/util/GbkTable.kt`

### 6. Ksoup 无 XPath 支持

**现象**：`soup.selectFirstXpath(...)` 报 `Unresolved reference`。

**原因**：Ksoup 0.2.x **不内置 XPath**（jsoup 的 `JsoupXpath` 是额外模块，KMP 版没有）。

**解法**：写一个轻量 XPath→CSS 翻译器，覆盖用到的子集
（`//*[@id=...]` 锚点 + `tag[n]` 索引 + 自动跳过 `tbody` 层）。
注意：**该文件用中文注释会导致 Kotlin/Native 报 `Unclosed comment` 误报**（编码坑，见第 9 条），
保持纯 ASCII 注释。

**涉及文件**：`shared/.../wenku8/KsoupXpath.kt`

---

## 二、Kotlin 编译

### 7. 双参 `Result<V, E>` 与 Kotlin 内置 `Result<T>` 混淆

**现象**：`One type argument expected for 'class Result<out T>'`、
`Argument type mismatch: actual type is 'Throwable', but 'String' was expected`。

**原因**：移植上游代码时用了 kotlin-result 的双参 `Result<V, E>`，但没 import
`com.github.michaelbull.result.Result`，全部解析到 Kotlin 内置的单参 `Result<T>`。

**解法**：统一用 Kotlin 内置 `Result<T>`，让 `WebRequestError` 继承 `Exception` 作为失败值。
避免双参 Result 的 import 混乱（且双参 Result 导出到 Swift 是 `Result<T,E>` 泛型，Swift 无法消费）。

**涉及文件**：`shared/.../api/error/WebRequestError.kt`、`wenku8/*.kt`

### 8. `@escaping` 误写进 Kotlin

**现象**：`Unresolved reference 'escaping'`。

**原因**：从 Swift 移植 completion handler 时把 Swift 的 `@escaping` 注解带进了 Kotlin。
**Kotlin 没有 `@escaping`**（函数类型参数天然可逃逸）。

**解法**：删掉所有 `@escaping`。

**涉及文件**：`shared/.../api/Wenku8DataSourceApi.kt`

### 9. Kotlin/Native 中文注释导致 `Unclosed comment` 误报

**现象**：`KsoupXpath.kt:107:1 Syntax error: Unclosed comment`，但文件本身注释
全部闭合（用 xxd 检查字节也正常）。

**原因**：文件里的中文注释字节在 Kotlin/Native 解析时被误判为注释边界。
具体是注释内容含 `//` 序列（如 `//*[@id=...]`）触发的解析异常。

**解法**：KMP 源码里**避免用中文注释**（尤其含 `//` 的），用纯 ASCII 英文注释。
这是 Kotlin/Native 的已知怪癖，排查时极易浪费大量时间。

**涉及文件**：`shared/.../wenku8/KsoupXpath.kt`（含 XPath 示例注释的文件）

---

## 三、Swift 桥接

### 10. `KotlinThrowable` 不能直接当 Swift `Error`

**现象**：`'KotlinThrowable' is not convertible to 'any Error'`。

**原因**：Kotlin/Native 导出的 `Throwable` 在 Swift 里是 `KotlinThrowable`，不自动
桥接成 Swift `Error` 协议。

**解法**：写转换函数，把 `KotlinThrowable` 转成 `NSError`：
```swift
private static func asError(_ throwable: Any) -> Error {
    let message = (throwable as? KotlinThrowable)?.message
        ?? String(describing: throwable)
    return NSError(domain: "SharedKit", code: 1,
                   userInfo: [NSLocalizedDescriptionKey: message])
}
```

**涉及文件**：`Sources/Services/KmpBookSourceAdapter.swift`

### 11. Kotlin `Int` 在 Swift 是 `KotlinInt` / `KotlinIntArray`，不是 `Int32`

**现象**：`cannot convert value of type '[Int32]' to expected argument type
'KotlinIntArray'`，`'Int32' has no member 'map'`。

**原因**：Kotlin 的 `Int`/`IntArray` 导出到 Swift 是 `KotlinInt`/`KotlinIntArray`，
不是 Swift 原生 `Int32`/`[Int32]`。Kotlin 的 `Int32` 才是 Swift 的 `Int32`。

**解法**：
- 模型字段：`info.wordCount?.count.map { $0 / 1000 }` 里 `count` 是 `Int32`，
  直接 `Int(count / 1000)`，不能 `.map`（非 Optional）
- 函数参数：Kotlin 侧用 `List<Int>` 导出成 Swift `[KotlinInt]` 也不好用，
  最省事是**改成 String 参数**（如 hex 字符串）绕开数组桥接

**涉及文件**：`Sources/Services/KmpBookSourceAdapter.swift`、`shared/.../Wenku8DataSourceApi.kt`

### 12. Kotlin `object` 在 Swift 是 `Xxx.shared` 单例

**现象**：`GbkBridge.decode(...)` 报 `value of type 'GbkBridge' has no member`，
或把类型当参数传。

**原因**：Kotlin `object` 导出到 Swift 是**类 + `shared` 单例属性**，不是静态方法。

**解法**：`GbkBridge.shared.decodeHex(hex: "...")`。

**涉及文件**：Swift 调用方

### 13. completion handler 的 `Error?` 参数类型

**现象**：`Argument type mismatch: actual type is 'NSErrorCompat', but 'Error?' was expected`。

**原因**：Kotlin 侧 completion handler 第二参写了 `Error?`（Kotlin 的 `kotlin.Error`），
和 Swift 的 `Error` 协议不是一回事。

**解法**：Kotlin 侧统一用 `Throwable?`（Swift 桥接成 `Error?`）。

**涉及文件**：`shared/.../Wenku8DataSourceApi.kt`

---

## 四、数据与解码

### 14. GBK 解码书名乱码（`资??位??????`）

**现象**：从 wenku8 拉到的中文书名/作者变成 `?` 或乱码。

**原因**：Kotlin/Native **无内置 GB18030 charset**。最初手写的解码表只有
GB2312 常用字（3755 个），书名里的二级汉字（吉/波/普/浩/平等）不在表里 → `?`。

**解法**：
1. 换成**完整 GB2312 6763 字表**（一级 3755 + 二级 3008，按区位顺序）——
   python 从系统 `gb2312` 编码生成，保证 `zone = 16 + i/94` 索引正确
2. 进一步换成**完整 GBK 双字节表（23940 码位）**，覆盖 GBK 扩展区
   （繁体字、日文汉字如 體/瑠/咲）
3. 加**严格解码校验**：尾字节不在 0x40-0xFE（且非 0x7F）时按单字节处理，
   避免中文+ASCII 混合时字节流错位

**涉及文件**：`shared/.../api/util/Gbk.kt`、`GbkTable.kt`

### 15. GBK 扩展区码位被 python 解成 PUA 私有区字符

**现象**：书名里出现 `\ue187` 这类 Unicode 私有区字符（显示为火星文/乱码），
且**排在一起无意义**。

**原因**：GB18030 标准把 0xAEA1-0xAEBF 区段映射到 **PUA 私有区（U+E000+）**，
没有任何标准汉字。wenku8 书名里用了这些码位，**任何标准解码器**
（JVM/pyton/Unicode 官方 CP936 表）都解不出正确汉字。

**结论**：**不是解码 bug，是标准问题。上游 Android 版（JVM `Charset.forName("GB18030")`）
同样显示这些乱码**。占比极小（书名个别字），不影响阅读，可接受。

**解法**：不强行修（无标准依据，猜 wenku8 自定义映射风险大）。用官方
`https://www.unicode.org/Public/MAPPINGS/VENDORS/MICSFT/WINDOWS/CP936.TXT`
交叉验证（官方表 0 PUA 映射，但也没有 0xAEA1 区段）。

### 16. Xcode 把静态 framework embed 成动态库

**现象**：app 的 `Frameworks/` 里出现 `SharedKit.framework`，`file` 显示
`dynamically linked shared library`，依赖 `/private/var/folders/.../swbuild.tmp...` 临时路径
（真机上 dyld 找不到必崩）。主二进制无 Kotlin 符号（0 个）。

**原因**：XcodeGen 的 `framework: Frameworks/SharedKit.xcframework` + `embed: true`
导致 Xcode 把静态 framework 当动态库复制进 app 并链接成动态。

**解法**：
- 打包 IPA 时**删掉 app 里的 Frameworks 目录**（主二进制已静态链接 Kotlin 符号，
  用 `nm 主二进制 | grep Wenku8` 验证）
- 排查时用 `otool -L` 看动态库依赖、`nm` 看主二进制符号，确认静态链接

**涉及文件**：`project.yml`（依赖配置）、打包流程

### 17. XcodeGen `embed: false` 后静态库完全不链接

**现象**：改 `embed: false` 后，主二进制 0 个 Kotlin 符号，链接命令里没有 SharedKit。

**原因**：XcodeGen 的 framework 依赖在 embed:false 时可能不生成 Frameworks build phase，
或不加 `-framework` 链接参数。

**解法**：本项目回归 `embed: true`（标准做法），并**确保 xcframework 的 slice 是最新**
（改 KMP 代码后必须重新 `linkDebugFramework*` + `-create-xcframework` + 覆盖 Frameworks/，
否则 app 链接到旧 slice，行为诡异）。

**涉及文件**：`project.yml`、构建脚本 `scripts/build-sharedkit.sh`

---

## 五、测试与验证的坑

### 18. 模拟器"无网"其实是代理拦截

**现象**：模拟器里探索页一直显示"未登录/无数据"，以为 KMP 没生效。

**原因**：开发机开了代理（Clash 等），模拟器的网络请求被代理挡掉，
**真实数据从未拉到过**——不是代码问题，是环境问题。

**解法**：区分"下载工具链用代理"和"真实请求直连"。关掉代理后模拟器
立即拉到真实数据，暴露了真正的解码 bug（见第 14 条）。

### 19. UI 测试被 splash 遮罩拦截点击

**现象**：UI 测试点 Tab 按钮无反应，导航栏断言失败。

**原因**：`SplashView` 是 ZStack 顶层遮罩，splash 淡出前 Tab 按钮在底层存在但
**点击被遮罩拦截**。等 `tabBar` 出现不代表可交互。

**解法**：等 splash 的特征元素**消失**（`waitForNonExistence`）再点击。

**涉及文件**：`UITests/TabNavigationUITests.swift`

---

## 六、真机实战排错（V1.1.0 迭代新增）

> 模拟器全绿、真机翻车的坑。**模拟器验证 ≠ 真机可用**——这批坑全部是真机实测才暴露的。

### 20. GBK 表生成的"分号 bug"：真机全乱码，模拟器却"大部分正常"

**现象**：真机上探索/详情/书名几乎全乱码（带框问号 U+FFFD），但模拟器只有个别字乱码（PUA）。

**原因**：生成 GBK 表（23940 条）时压成"区段字符串"，**每行切分丢了分号**，拼接后变成
`...20014,2" + "33103,...`（缺 `;`）→ `233103` 被当成巨大长度 → 表残缺。
模拟器碰巧覆盖常用字（"大部分正常"），真机严格解析就全崩。

**解法**：**每段必须带分号结尾**（含行尾），并用 python 独立验证还原出完整 23940 条。
教训：**大字符串生成脚本的分隔符 bug 极难察觉，生成后必须程序化验证条目数**。

**涉及文件**：`shared/.../api/util/GbkTable.kt`（生成逻辑）

### 21. Ksoup `text()` 对 `<td>`（inline）之间不加空格 → 字段拼接成一长串

**现象**：详情页作者/文库/状态/最后更新每行都是一长串
（如作者显示"上远野浩平文章状态：连载中最后更新：..."）。

**原因**：Ksoup 的 `text()` 只对 block 元素（div/p/table/br）加空格，**`<td>` 是 inline 不加**。
`<td>小说作者：上远野浩平</td><td>文章状态：...</td>` 连在一起，按空格/全角空格截断截不断。

**解法**：不能靠 `text()` 拼接 + 空格截断。改用**DOM 定位**：
`td:contains(小说作者)` 取 td 文本，去掉前缀。和简介的 DOM 方案统一。

**涉及文件**：`shared/.../wenku8/Wenku8DataSource.kt`

### 22. 简介显示"最近章节/插图"或吞入整个网页

**现象**：简介显示"第二十六卷 ...插图..."（章节链接文字），或简介后面跟着
"阅读 小说目录 收藏 ... TXT下载 ... 同分类推荐 ... 年份列表"（整个网页）。

**原因**：
1. "内容简介："标签**后隔着 `<br>` 才是简介 span**，sibling 遍历/`selectFirst(span[14px])` 取到了
   **"最近章节"的 span**（含 `<a>` 链接，文字是章节名，可能含"插图"）。
2. 文本截断用"作品Tags/最后更新"等 key——**简介后没有这些 key 时截到页面末尾**，吞入按钮区/推荐区。

**解法**：DOM 定位——`span:contains(内容简介)` 找 label，**取其后第一个不含 `<a>` 的 span**。
简介正文无链接，"最近章节"有链接，天然区分。

**涉及文件**：`shared/.../wenku8/Wenku8DataSource.kt`（`extractDescription`）

### 23. 正文不分段（密密麻麻一整页）

**现象**：正文从头到尾密密麻麻，没有段落间隔。

**原因**：wenku8 正文用 **`<br>` 分段**（无 `<p>`），但解析循环只处理 `divimage`，
**`<br>` 被忽略** → 所有文本累积成一段。

**解法**：解析时遇到 `<br>` 元素**切分段落**（flush 当前文本成一段）。
注意 `ContentBuilder.simpleText` 内部会跳过空白，连续 `<br>` 不会产生空段。

**涉及文件**：`shared/.../wenku8/Wenku8DataSource.kt`（`getChapterContent`）

### 24. 阅读/缓存全书闪退：JsonObject 跨 Swift 边界桥接崩溃

**现象**：真机点"开始阅读"或"缓存全书"直接闪退。崩溃报告堆栈：
`_unconditionallyBridgeFromObjectiveC NSDictionary` 在 `fetchContent` 桥接 `ChapterContent` 时。

**原因**：Kotlin `ChapterContent.content` 是 `JsonObject`（导出为 NSDictionary），
Swift 从 completion handler 拿它时**强制桥接崩溃**（真机 iOS 17 更严格，模拟器 iOS 26 不崩）。

**解法**：**不让 JsonObject 跨 Swift 边界**——Kotlin 侧新增 `getChapterContentFlat`，
直接解析好段落/图片/标题，返回纯 String 类型。Swift 不再碰 JsonObject。

**涉及文件**：`shared/.../Wenku8DataSourceApi.kt`（`ChapterContentFlat`）、`KmpBookSourceAdapter.swift`

### 25. Kotlin `description` 属性导出到 Swift 变 `description_`

**现象**：简介显示英文 + 乱码（其实是 `BookInformation(...)` 的 toString）。

**原因**：Kotlin 的 `description` 属性和 `NSObject.description` 冲突，
Kotlin/Native 导出时**自动改名成 `description_`**。Swift 侧 `info.description`
取到的是对象的 `description`（toString）。

**解法**：Swift 侧用 **`info.description_`**。教训：**Kotlin 属性名和 NSObject 冲突时会改名**，
Swift 调用前先查 `SharedKit.h` 确认实际属性名。

**涉及文件**：`Sources/Services/KmpBookSourceAdapter.swift`

### 26. Swift 适配器 `!` 强解包崩溃

**现象**：KMP 返回 nil 时（解析失败），`continuation.resume(returning: content!)` 直接崩。

**原因**：completion handler 里对可选值强解包，KMP 失败路径返回 nil 就崩。

**解法**：所有 `xxx!` 改成 `guard let xxx else { resume(throwing:) ; return }`。

**涉及文件**：`Sources/Services/KmpBookSourceAdapter.swift`

### 27. 阅读设置（背景/字体/边距）不即时生效，要翻页才刷新

**现象**：改背景后当前页还是旧背景（且字体先变导致米白底白字看不清），翻页才生效。

**原因**：翻页视图（PageCurlReaderView）缓存页面 VC，只有翻页请求新页才 purge 重建。
`renderToken` 原来只含背景/分页版本，**不含字体/边距/字重/字号**。

**解法**：
1. `renderToken` 包含**全部**阅读设置参数（背景/字体/字号/行距/字重/边距/模式）；
2. `PageCurlReaderView` 加 `.id("\(chapter)#\(renderToken)")`——设置变化时整体重建。

**涉及文件**：`Sources/Features/ReaderView.swift`

### 28. 真机排查无日志：需要崩溃报告功能

**现象**：真机闪退/异常，开发机（云 Mac）连不上真机，无法看日志。

**解法**：app 内置 `CrashReporter`：
- `NSSetUncaughtExceptionHandler` 捕获 NSException + 信号处理（SIGABRT/SIGSEGV 等）；
- 崩溃写文件到 `Documents/CrashReports/`（含设备/版本/堆栈）；
- 设置页"崩溃报告"入口可导出 txt（分享面板）。
配合 `NSLog`（Kotlin/Native 的 NSLog 进系统日志，`println` 不进）定位。

**涉及文件**：`Sources/Services/CrashReporter.swift`、设置页

### 29. 详情页字段解析：fieldAfter（文本）→ DOM 定位（td:contains）

**现象**：fieldAfter 依赖 `soup.text()` 拼接 + key 截断列表，wenku8 改格式要维护列表。

**解法**：统一改用 DOM 定位——`td:contains(小说作者)` / `td:contains(文库分类)` 等，
取 td 文本去前缀。和简介 DOM 方案一致，不依赖 text() 拼接，更稳。

**涉及文件**：`shared/.../wenku8/Wenku8DataSource.kt`

---

## 七、验证工具速查

```bash
# Kotlin 符号是否静态链接进主二进制（0 = 没链接）
nm <app>/<AppName> | grep -c 'Wenku8DataSourceApi'

# 动态库依赖（含临时路径 = 真机必崩）
otool -L <app>/Frameworks/SharedKit.framework/SharedKit

# framework 是静态(ar)还是动态(Mach-O dylib)
file <framework>/SharedKit

# 真实页面编码交叉验证（python）
python3 -c "print(bytes.fromhex('B2BBBCAA').decode('gb18030'))"

# 完整 GBK 解码表生成（防 PUA 陷阱，用 CP936 官方表交叉验证）
curl -s https://www.unicode.org/Public/MAPPINGS/VENDORS/MICSFT/WINDOWS/CP936.TXT
```

## 版本基线（踩坑时的工具链）

| 项 | 版本 | 备注 |
|---|---|---|
| Kotlin | 2.3.21 | 升到 2.3.21 解决 kotlin-result ABI |
| Ktor | 3.5.2 | `UserAgent` 配置属性是 `agent`（不是 `userAgent`） |
| Ksoup | 0.2.6 | 无 XPath，用 KsoupXpath.kt |
| kotlin-result | 2.3.1 | group `com.michael-bull.kotlin-result` |
| Xcode | 26.6 | — |
| Gradle | 8.13 | — |
