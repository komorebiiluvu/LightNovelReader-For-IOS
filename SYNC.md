# SYNC.md — 与上游 LightNovelReader 的同步手册

本文件回答一个问题：**上游项目（dmzz-yyhyy/LightNovelReader，refactoring 分支）更新后，我们怎么跟着更新、会不会出错。**

## 核心机制：手动同步（没有自动跟随）

上游项目**没有发布任何 Kotlin Multiplatform 包**（`:api` 是纯 Android library，
`api/build.gradle.kts` 无 iosArm64/commonMain 配置）。因此：

- ❌ 不能通过 Gradle 依赖自动跟随上游
- ✅ 只能「复制移植」：上游改动 → 人工同步到 `shared/` KMP 层 → 重编译 → 验证

**同步周期建议**：上游每次发版（或 refactoring 分支有实质提交）时，按本手册走一遍。

## 一、映射表（上游文件 → 我们的文件）

### 1. 数据模型（`shared/src/commonMain/kotlin/io/nightfish/lightnovelreader/api/`）

这些文件**包名与上游完全一致**（`io.nightfish.lightnovelreader.api.*`），
同步方式是**直接 diff 复制**，几乎零翻译成本。

| 上游 `:api` 文件 | 我们的文件 | 差异说明 |
|---|---|---|
| `book/BookInformation.kt` | `api/book/BookInformation.kt` | `Uri`→`String coverUrl`；字段结构一致 |
| `book/BookVolumes.kt` | `api/book/BookVolumes.kt` | 一致 |
| `book/Volume.kt` | `api/book/Volume.kt` | 一致 |
| `book/ChapterInformation.kt` | `api/book/ChapterInformation.kt` | 一致 |
| `book/ChapterContent.kt` | `api/book/ChapterContent.kt` | 一致 |
| `book/WordCount.kt` | `api/book/WordCount.kt` | 去掉 Android 专属 `unitResId` |
| `book/UserReadingData.kt` | `api/book/UserReadingData.kt` | 一致 |
| `bookshelf/Bookshelf.kt` | `api/bookshelf/Bookshelf.kt` | ⚠️ `id: Int`→`String`；`sortType` 枚举→字符串 |
| `identifier/Identifier.kt` | `api/identifier/Identifier.kt` | 去掉 `Parcelable`；加了 `wenku8Identifier()` 辅助 |
| `error/WebRequestError.kt` | `api/error/WebRequestError.kt` | ⚠️ 继承 `Exception`（Kotlin 内置 `Result<T>` 失败值） |
| `content/builder/ContentBuilder.kt` | `api/content/builder/ContentBuilder.kt` | 输出组件 JSON 格式一致（`lightnovelreader:simple_text`/`image`） |
| `content/component/SimpleTextComponentData.kt` | `api/content/component/SimpleTextComponentData.kt` | 一致 |
| `content/component/ImageComponentData.kt` | `api/content/component/ImageComponentData.kt` | 一致 |

> ⚠️ 同步模型后必须同步检查：`Sources/Services/KmpBookSourceAdapter.swift` 的
> `mapBook`/`mapVolumes` 字段映射是否仍匹配（新增/改名/删除字段都会在这里暴露）。

### 2. wenku8 抓取器（`shared/src/commonMain/kotlin/io/nightfish/lightnovelreader/wenku8/`）

上游是 **11 个文件**，我们压成 **3 个文件**。同步方式是**人工翻译**（jsoup→Ksoup、
Ktor OkHttp→Darwin、GB18030→自写解码），不是文件替换。

| 上游文件 | 我们的文件 | 同步方式 | 出错点 |
|---|---|---|---|
| `wenku8/Wenku8Api.kt` | `wenku8/Wenku8Client.kt` | 翻译：Ktor 客户端配置、cookie 会话、`getWithWenku8Cookie` | Ktor API 版本差异（见下） |
| `wenku8/Wenku8SearchProvider.kt` | `Wenku8DataSource.search()` | 翻译：搜索 Flow、5 秒限速 | 搜索 URL 构造、单书/多书判断 |
| `wenku8/book/BookRequestDispatcher.kt` | `Wenku8DataSource` 详情/目录/正文方法 | 翻译 | 详情页选择器 |
| `wenku8/book/Wenku8WebsiteDataSource.kt` | `Wenku8DataSource` | 翻译：`getBookInformation`/`getBookVolumes`/`getChapterContent` | 目录 XPath、正文 `#content` 子节点解析 |
| `wenku8/book/Wenku8BookDataSource.kt` | （合并进 `Wenku8DataSource`） | 翻译 | — |
| `wenku8/explore/Wenku8HomeExploreTapPage.kt` | `Wenku8DataSource.getHomeBlocks()` | 翻译：`#centers > div:nth-child(n+2)` 选择器 | 首页推荐块结构 |
| `wenku8/explore/Wenku8AllExploreTapPage.kt` | `Wenku8DataSource.exploreCategories()` | 翻译：6 个栏目 | 栏目 URL/参数 |
| `wenku8/explore/Wenku8TagsExploreTapPage.kt` | `Wenku8DataSource.tagList()`/`tagCategory()` | 翻译：48 标签硬编码 | 标签列表增减 |
| `wenku8/explore/Wenku8ExplorePageProvider.kt` | `Wenku8DataSource.exploreCategories()` | 翻译：栏目注册 | — |
| `wenku8/explore/expanedpage/AllBookExpandPageDataSource.kt` | `Wenku8DataSource.getExplorePage()` | 翻译：分页、`pagelink/em` 总页数 | 分页 URL、书卡选择器 |
| `wenku8/explore/expanedpage/filter/*` | ⚠️ **未移植** | 待办：服务端筛选/排序 | 上游加了过滤器逻辑需跟进 |

> ⚠️ 展开页过滤器（`IsCompletedSwitchFilter`/`PublishingHouseSingleChoiceFilter`/
> `WordCountFilter`/`SingleChoiceFilter`）目前**没移植**进 KMP，Swift 侧 `ExploreListView`
> 是本地筛选。若上游调整筛选逻辑，需评估是否同步移植。

### 3. Swift 桥接层（不直接对应上游文件）

| 文件 | 作用 | 上游更新时要不要动 |
|---|---|---|
| `Sources/Services/KmpBookSourceAdapter.swift` | KMP→`BookSourceService` 协议适配 | ⚠️ 模型字段变就要动 |
| `Sources/Services/BookSourceServiceProtocol.swift` | iOS 书源协议 | 一般不因上游动 |
| `Sources/Services/Wenku8Service.swift` | ⚠️ 早期纯 Swift 兜底实现（619 行） | 与上游无关，KMP 接管后仅兜底 |

## 二、平台替换对照（翻译时用）

上游是 Android/JVM 代码，翻译成 KMP 时按此表替换：

| 上游（JVM/Android） | 我们（KMP） | 备注 |
|---|---|---|
| `org.jsoup.Jsoup.parse` / `jsoup` | `com.fleeksoft.ksoup.Ksoup.parse` | Ksoup 0.2.6，API 与 jsoup 高度兼容 |
| `soup.selectFirstXpath(...)` | `KsoupXpath.selectFirstXpath(...)` | ⚠️ Ksoup **无 XPath**，用 `KsoupXpath.kt` 翻译器（仅支持 `//*[@id=...]` + `tag[n]`） |
| `soup.selectFirst("css")` / `select("css")` | 同（Ksoup 兼容 jsoup CSS 选择器） | — |
| `HttpClient(OkHttp)` | `HttpClient(Darwin)` | iosMain 依赖 `ktor-client-darwin` |
| `URLEncoder.encode(s, "gb2312")` | `Gbk.percentEncode(s)` | `shared/.../api/util/Gbk.kt` 手写 GBK 表 |
| `String(bytes, Charset.forName("GB18030"))` | `Gbk.decodeToString(bytes)` | Kotlin/Native 无内置 GB18030 |
| `android.net.Uri` | `String` | 封面/插图地址 |
| `Result<V, E>`（kotlin-result） | Kotlin 内置 `Result<T>` + `WebRequestError: Exception` | 见下「错误处理」 |
| `kotlinx.coroutines.flow` | 同（KMP 可用） | — |

### 错误处理约定

- 我们不用 kotlin-result 的双参 `Result<V, E>`，统一 Kotlin 内置 `Result<T>`；
- `WebRequestError` 继承 `Exception` 作为失败值（`title`/`message` 保留）；
- Swift 侧 `NSErrorCompat` 承载 `title`+`message`。

## 三、上游更新后的同步流程（照此执行）

```bash
# 0. 拉上游最新代码
git -C /tmp/lnr-upstream pull   # 或重新 clone：git clone -b refactoring --proxy http://127.0.0.1:7897 https://github.com/dmzz-yyhyy/LightNovelReader

# 1. diff 数据模型（映射表第 1 节），有变化则复制 + 检查 Swift 适配器字段映射
#    命令示例：
diff /tmp/lnr-upstream/api/src/main/kotlin/io/nightfish/lightnovelreader/api/book/BookInformation.kt \
     shared/src/commonMain/kotlin/io/nightfish/lightnovelreader/api/book/BookInformation.kt

# 2. diff wenku8 抓取器（映射表第 2 节），有变化则人工翻译到 Wenku8DataSource.kt
#    重点看：选择器/URL/解析规则/搜索逻辑

# 3. 重编译 KMP + 合并 xcframework
cd shared && JAVA_HOME="$(/usr/libexec/java_home -v 17)" ./gradlew linkDebugFrameworkIosSimulatorArm64 linkDebugFrameworkIosArm64 --no-daemon
rm -rf build/bin/SharedKit.xcframework
xcodebuild -create-xcframework -framework build/bin/iosArm64/debugFramework/SharedKit.framework \
  -framework build/bin/iosSimulatorArm64/debugFramework/SharedKit.framework \
  -output build/bin/SharedKit.xcframework
rm -rf ../Frameworks/SharedKit.xcframework && cp -R build/bin/SharedKit.xcframework ../Frameworks/

# 4. 重新生成工程 + 构建 + 全部测试
cd .. && ~/bin/xcodegen generate
xcodebuild -project LightNovelReader.xcodeproj -scheme LightNovelReader \
  -destination 'platform=iOS Simulator,name=iPhone 17' test

# 5. 真机冒烟：重点验证 搜索 / 目录 / 正文 / 探索(6栏目+首页+标签) 仍正常
```

## 四、版本基线

| 项 | 版本 | 备注 |
|---|---|---|
| 上游跟踪分支 | `refactoring` | 上游默认分支 |
| 同步基准日期 | 2026-08 | 本次手册建立时对齐的状态 |
| Kotlin | 2.3.21 | 升级需同步检查 kotlin-result/ksoup ABI |
| Ktor | 3.5.2 | — |
| Ksoup | 0.2.6 | ⚠️ 无 XPath 模块，靠 KsoupXpath.kt |
| kotlin-result | 2.3.1 | group `com.michael-bull.kotlin-result` |

## 五、已知的「会出错」风险点（按优先级）

1. **KsoupXpath 翻译器能力有限**：只支持 `//*[@id=...]` + `tag[n]`。上游若新增复杂
   XPath（属性谓词、`//` 任意深度），翻译器返回 null → 解析静默失败（空结果），
   需扩展 `KsoupXpath.kt`。
2. **上游改选择器**：wenku8 页面改版时上游会改 CSS/XPath，我们的移植必须同步，
   否则抓取结果为空或错乱。**这是最常见的出错点**。
3. **Ktor API 版本漂移**：上游锁 Ktor 版本，我们锁 3.5.2；若上游升 Ktor 用了新
   API（如插件写法），需对照 Ktor 3.5 的 API 调整。
4. **Bookshelf/WebRequestError 类型偏差**：`id: Int→String`、继承 Exception，上游
   若改这些模型，diff 时会看到差异，需判断是「上游变更」还是「我们的有意替换」。
5. **Swift 适配器映射**：模型字段增删改，`KmpBookSourceAdapter.mapBook` 编译不报错
   但映射错（如字段改名），单测 `KmpExploreWiringTests` 可兜住一部分。
6. **Wenku8Service 兜底漂移**：纯 Swift 兜底与 KMP 行为不一致（历史上探索/登录
   就出过差异）。长期建议：KMP 稳定后移除或降级为「纯离线兜底」。
