import Foundation

/// KMP 数据互通适配器：当 SharedKit.xcframework 编译进工程时，
/// 用 Kotlin 侧移植的上游数据层（模型/抓取器与上游 LightNovelReader 完全一致）实现 BookSourceService；
/// 未编译 SharedKit 时（如未装 KMP 工具链的构建环境），自动回退到现有纯 Swift 的 Wenku8Service，
/// 保证两种环境下 App 都能正常运行。
///
/// 数据互通目标：KMP 侧的 BookInformation/BookVolumes/ChapterContent/UserReadingData
/// 与上游 Android 版 `:api` 模块字段一一对应，将来对接上游数据格式时零转换。
enum KmpBookSourceServiceFactory {
    /// 是否可用：SharedKit 框架已被编译进工程
    static var isSharedKitAvailable: Bool {
        #if canImport(SharedKit)
        return true
        #else
        return false
        #endif
    }

    /// 返回应注入的 BookSourceService 实现：
    /// 混合服务：探索走 KMP（Ktor/Darwin 带 PHPSESSID 即可访问 articlelist/toplist/index），
    /// 搜索走 Swift Wenku8Service（URLSession 指纹接近 Safari，登录能拿到 jieqi cookie）。
    static func makeDefaultService() -> BookSourceService {
        return HybridBookSourceService()
    }
}

#if canImport(SharedKit)
import SharedKit

/// 基于 SharedKit（KMP 移植的上游 wenku8 数据层）的 BookSourceService 实现。
final class KmpBookSourceAdapter: BookSourceService {
    var name: String { api.sourceName }

    private let api = Wenku8DataSourceApi()

    /// 释放 Kotlin 侧协程作用域与 Ktor HttpClient，避免随 App 生命周期常驻
    deinit {
        api.close()
    }

    // MARK: - 搜索（书源内搜索）

    func search(_ term: String) async throws -> [Book] {
        try await ensureLogin()
        // KMP 搜索返回书 ID 流；这里用第一个页面（页 1），单书直接取
        let ids: [String] = try await withCheckedThrowingContinuation { continuation in
            // 通过 KMP 门面的搜索（Kotlin Flow 在 Swift 侧用 CompletionHandler 收流）
            api.searchBookIds(keyword: term, page: 1) { result, error in
                if let error {
                    continuation.resume(throwing: Self.asError(error))
                    return
                }
                continuation.resume(returning: result ?? [])
            }
        }
        // 逐本拉详情（页 1 结果）
        var books: [Book] = []
        for id in ids {
            if let book = try? await fetchBookDetail(id: id) {
                books.append(book)
            }
        }
        return books
    }

    // MARK: - 书目（KMP 书源无内置精选书目，靠探索/搜索拉取）

    func fetchBooks() async throws -> [Book] {
        // 与现有 Wenku8Service 一致：无内置书目，返回空（书籍来自探索 + 本地持久化）
        []
    }

    // MARK: - 探索（对齐上游 Wenku8ExplorePageProvider）

    /// 探索「全部」板块的栏目列表（KMP 侧对齐上游 6 个 expanded page）
    var exploreCategories: [ExploreCategory] {
        api.exploreCategories.map { cat in
            ExploreCategory(
                id: cat.id,
                title: cat.title,
                path: "/" + cat.path,
                extraParams: cat.extraParams,
                supportsSort: cat.supportsSort
            )
        }
    }

    func fetchExplorePage(_ category: ExploreCategory, page: Int, sortSuffix: String) async throws -> (books: [Book], totalPages: Int) {
        guard isLoggedIn else { throw BookSourceError.loginRequired }
        let path = category.path.hasPrefix("/") ? String(category.path.dropFirst()) : category.path
        let extra = category.extraParams + sortSuffix
        let info: ExplorePageInfo = try await withCheckedThrowingContinuation { continuation in
            api.fetchExplorePage(path: path, extraParams: extra, page: Int32(page)) { info, error in
                if let error {
                    continuation.resume(throwing: Self.asError(error))
                    return
                }
                guard let info else {
                    continuation.resume(throwing: BookSourceError.unreachable)
                    return
                }
                continuation.resume(returning: info)
            }
        }
        return (info.books.map { Self.mapBook($0) }, Int(info.totalPages))
    }

    func fetchHomeBlocks() async throws -> [HomeExploreBlock] {
        guard isLoggedIn else { throw BookSourceError.loginRequired }
        let blocks: [HomeBlockInfo] = try await withCheckedThrowingContinuation { continuation in
            api.getHomeBlocks { blocks, error in
                if let error {
                    continuation.resume(throwing: Self.asError(error))
                    return
                }
                continuation.resume(returning: blocks ?? [])
            }
        }
        return blocks.map { HomeExploreBlock(title: $0.title, books: $0.books.map { Self.mapBook($0) }) }
    }

    func fetchTagList() async throws -> [String] {
        // 标签列表是硬编码常量（对齐上游 Wenku8ExplorePageProvider.tagList），无需登录/网络
        api.tagList
    }

    func tagCategory(_ tag: String) -> ExploreCategory {
        let cat = api.tagCategory(tag: tag)
        return ExploreCategory(
            id: cat.id,
            title: cat.title,
            path: "/" + cat.path,
            extraParams: cat.extraParams,
            supportsSort: cat.supportsSort
        )
    }

    // MARK: - 目录

    func fetchChapters(for book: Book) async throws -> [ChapterItem] {
        let aid = try Self.aid(of: book)
        let volumes: BookVolumes = try await withCheckedThrowingContinuation { continuation in
            api.getBookVolumes(bookId: "\(aid)") { volumes, error in
                if let error {
                    continuation.resume(throwing: Self.asError(error))
                    return
                }
                guard let volumes else {
                    continuation.resume(throwing: BookSourceError.unreachable)
                    return
                }
                continuation.resume(returning: volumes)
            }
        }
        // 卷→章 映射为扁平目录（与现有 ChapterItem 对齐）
        var items: [ChapterItem] = []
        var index = 0
        for volume in volumes.volumes {
            for chapter in volume.chapters {
                items.append(ChapterItem(index: index, title: chapter.title, volume: volume.volumeTitle, remoteID: Int(chapter.id)))
                index += 1
            }
        }
        return items
    }

    // MARK: - 正文

    func fetchContent(for book: Book, chapter index: Int) async throws -> ChapterContent {
        let aid = try Self.aid(of: book)
        let chapters = try await fetchChapters(for: book)
        guard index < chapters.count else {
            throw BookSourceError.contentUnavailable(title: book.title)
        }
        let chapterId = "\(chapters[index].remoteID ?? index + 1)"
        // 用 Flat 版：Kotlin 侧解析好段落/图片，避免 JsonObject 跨 Swift 边界桥接崩溃
        let flat: SharedKit.ChapterContentFlat = try await withCheckedThrowingContinuation { continuation in
            api.getChapterContentFlat(chapterId: chapterId, bookId: "\(aid)") { content, error in
                if let error {
                    continuation.resume(throwing: Self.asError(error))
                    return
                }
                guard let content else {
                    continuation.resume(throwing: BookSourceError.contentUnavailable(title: book.title))
                    return
                }
                continuation.resume(returning: content)
            }
        }
        return ChapterContent(
            index: index,
            title: flat.title,
            paragraphs: flat.paragraphs,
            images: flat.images
        )
    }

    // MARK: - 登录

    var isLoggedIn: Bool { api.isLoggedIn }

    var loggedInUsername: String? { api.loggedInUsername }

    func login(username: String, password: String) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            api.login(username: username, password: password) { errorMessage, error in
                if let error {
                    continuation.resume(throwing: Self.asError(error))
                    return
                }
                if errorMessage != nil {
                    continuation.resume(throwing: BookSourceError.loginRequired)
                    return
                }
                continuation.resume()
            }
        }
    }

    func logout() {
        api.logout()
        Self.persistedCookie = nil
    }

    /// 登录 cookie 持久化（KMP 侧是内存态，这里用 UserDefaults 跨启动保持，避免每次启动重登）
    var savedCookie: String? {
        get { api.savedCookie ?? Self.persistedCookie }
        set {
            api.savedCookie = newValue
            Self.persistedCookie = newValue
        }
    }

    private static let cookieDefaultsKey = "wenku8.kmp.cookies"
    private static var persistedCookie: String? {
        get { UserDefaults.standard.string(forKey: cookieDefaultsKey) }
        set { UserDefaults.standard.set(newValue, forKey: cookieDefaultsKey) }
    }

    private func ensureLogin() async throws {
        // 启动时先从持久化恢复 cookie，避免无谓重登
        if let saved = Self.persistedCookie, api.savedCookie == nil {
            api.savedCookie = saved
        }
        // wenku8 探索/详情接口只需 PHPSESSID（实测 Ktor 登录能拿到），不需要 jieqi cookie。
        // 首次登录后即使拿不到 jieqiUserInfo 也不再重复登录，避免每次详情/探索前都发登录请求导致卡顿。
        if didAttemptLogin { return }
        didAttemptLogin = true
        if isLoggedIn { return }
        try await login(username: api.bundledUsername, password: api.bundledPassword)
        Self.persistedCookie = api.savedCookie
    }

    private var didAttemptLogin = false

    // MARK: - 详情

    func fetchBookDetail(aid: Int) async throws -> Book {
        try await ensureLogin()
        let info: SharedKit.BookInformation = try await withCheckedThrowingContinuation { continuation in
            api.getBookInformation(bookId: "\(aid)") { info, error in
                if let error {
                    continuation.resume(throwing: Self.asError(error))
                    return
                }
                guard let info else {
                    continuation.resume(throwing: BookSourceError.unreachable)
                    return
                }
                continuation.resume(returning: info)
            }
        }
        return Self.mapBook(info)
    }

    private func fetchBookDetail(id: String) async throws -> Book {
        try await ensureLogin()
        let info: SharedKit.BookInformation = try await withCheckedThrowingContinuation { continuation in
            api.getBookInformation(bookId: id) { info, error in
                if let error {
                    continuation.resume(throwing: Self.asError(error))
                    return
                }
                guard let info else {
                    continuation.resume(throwing: BookSourceError.unreachable)
                    return
                }
                continuation.resume(returning: info)
            }
        }
        return Self.mapBook(info)
    }

    // MARK: - 映射

    /// KMP BookInformation → iOS Book
    static func mapBook(_ info: SharedKit.BookInformation) -> Book {
        let id = "wk8-\(info.id)"
        let title = info.subtitle.isEmpty ? info.title : info.title
        return Book(
            id: id,
            title: title,
            author: info.author,
            tags: info.tags,
            source: "文库8(在线)",
            totalChapters: 0,
            lastChapter: 0,
            hasUpdate: false,
            hits: 0,
            intro: info.description_,
            coverIndex: 0,
            coverURL: info.coverUrl,
            publishingHouse: info.publishingHouse,
            isCompleted: info.isComplete,
            wordCountK: info.wordCount != nil ? Int(info.wordCount!.count / 1000) : nil,
            lastUpdate: info.lastUpdated
        )
    }

    /// 解析 wk8-<aid> 形式的书 ID
    static func aid(of book: Book) throws -> Int {
        guard book.id.hasPrefix("wk8-"), let aid = Int(book.id.dropFirst(4)) else {
            throw BookSourceError.bookNotFound(source: book.source)
        }
        return aid
    }

    /// Kotlin/Native 的 Throwable（KotlinThrowable）转成 Swift Error
    private static func asError(_ throwable: Any) -> Error {
        let message = (throwable as? KotlinThrowable)?.message
            ?? String(describing: throwable)
        return NSError(domain: "SharedKit", code: 1, userInfo: [NSLocalizedDescriptionKey: message])
    }
}
#endif
