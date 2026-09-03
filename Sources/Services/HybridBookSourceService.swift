import Foundation

/// 混合书源服务：首页/标签等探索数据用 KMP，展开列表和搜索用 Swift 的 Wenku8Service。
///
/// 背景（实测确认）：
/// - wenku8 通过 TLS/HTTP2 引擎指纹识别客户端。Ktor/Darwin（KMP）登录只拿到
///   PHPSESSID、拿不到 jieqiUserInfo；但探索接口（articlelist/toplist/index.php）
///   在带 PHPSESSID 时返回 200，所以 KMP 探索可用。
/// - 搜索接口 search.php 需要 jieqiUserInfo cookie；URLSession（Swift）的指纹
///   更接近系统 Safari，登录能拿到 jieqi cookie，所以搜索走 Swift。
final class HybridBookSourceService: BookSourceService {
    var name: String { "文库8(在线)" }

    private let kmp = KmpBookSourceAdapter()
    private let swift = Wenku8Service()

    // MARK: - 搜索（Swift：URLSession 能拿到 jieqi cookie）

    func search(_ term: String) async throws -> [Book] {
        // Swift 搜索前确保 Swift 侧已登录（jieqi cookie）
        if !swift.isLoggedIn {
            try await swift.login(username: Wenku8Service.bundledUsername, password: Wenku8Service.bundledPassword)
        }
        return try await swift.search(term)
    }

    var searchNotice: String? { swift.searchNotice }

    // MARK: - 探索

    /// 确保 Swift（URLSession）已登录拿到 jieqi cookie，并同步给 KMP。
    /// KMP 探索 .net 接口需要 jieqi cookie 才返回 200，否则被弹回登录页（302）。
    /// Swift 的 URLSession 指纹接近 Safari，登录稳定拿到 jieqi；KMP(Ktor) 抖动，故以 Swift 为准。
    private func ensureSwiftLoggedInSyncToKMP() async throws {
        if !swift.isLoggedIn {
            try await swift.login(username: Wenku8Service.bundledUsername, password: Wenku8Service.bundledPassword)
        }
        if let swiftCookie = swift.savedCookie,
           swiftCookie.contains("jieqiUserInfo"),
           kmp.savedCookie != swiftCookie {
            kmp.savedCookie = swiftCookie
        }
    }

    var exploreCategories: [ExploreCategory] {
        kmp.exploreCategories
    }

    func fetchExplorePage(_ category: ExploreCategory, page: Int, sortSuffix: String) async throws -> (books: [Book], totalPages: Int) {
        try await ensureSwiftLoggedInSyncToKMP()
        // 展开列表的后续页必须能分辨 302 登录页、WAF 页面与真实书目页。
        // Swift 实现会检查 HTTP 状态和最终跳转地址；KMP 层目前会把非书目 HTML
        // 解析成“空列表/共 1 页”，导致无限滚动被错误结束。
        return try await swift.fetchExplorePage(category, page: page, sortSuffix: sortSuffix)
    }

    func fetchHomeBlocks() async throws -> [HomeExploreBlock] {
        try await ensureSwiftLoggedInSyncToKMP()
        return try await kmp.fetchHomeBlocks()
    }

    func fetchTagList() async throws -> [String] {
        try await ensureSwiftLoggedInSyncToKMP()
        return try await kmp.fetchTagList()
    }

    func tagCategory(_ tag: String) -> ExploreCategory {
        kmp.tagCategory(tag)
    }

    // MARK: - 目录 / 正文 / 详情（KMP：实测只需 PHPSESSID 即可访问，与探索同一数据层）

    func fetchBooks() async throws -> [Book] {
        try await kmp.fetchBooks()
    }

    func fetchChapters(for book: Book) async throws -> [ChapterItem] {
        try await ensureSwiftLoggedInSyncToKMP()
        return try await kmp.fetchChapters(for: book)
    }

    func fetchContent(for book: Book, chapter index: Int) async throws -> ChapterContent {
        try await ensureSwiftLoggedInSyncToKMP()
        return try await kmp.fetchContent(for: book, chapter: index)
    }

    func fetchBookDetail(aid: Int) async throws -> Book {
        try await ensureSwiftLoggedInSyncToKMP()
        return try await kmp.fetchBookDetail(aid: aid)
    }

    // MARK: - 登录（两边都要登录：KMP 探索用 PHPSESSID，Swift 搜索用 jieqi）

    var isLoggedIn: Bool {
        // 探索可用（KMP 有 PHPSESSID 即可）或搜索可用（Swift 有 jieqi）
        kmp.isLoggedIn || swift.isLoggedIn
    }

    var loggedInUsername: String? {
        swift.loggedInUsername ?? kmp.loggedInUsername
    }

    func login(username: String, password: String) async throws {
        // 并行登录两边，互不阻塞：KMP 探索要 PHPSESSID，Swift 搜索要 jieqi cookie。
        // 任一边慢不拖慢另一边；至少一边成功即可。
        async let kmpLogin: Void = { try await kmp.login(username: username, password: password) }()
        async let swiftLogin: Void = { try await swift.login(username: username, password: password) }()
        var kmpSucceeded = false
        var swiftSucceeded = false
        do { try await kmpLogin; kmpSucceeded = true } catch {}
        do { try await swiftLogin; swiftSucceeded = true } catch {}
        if !kmpSucceeded && !swiftSucceeded {
            throw BookSourceError.loginRequired
        }
        // 关键：把 Swift（URLSession）拿到的 jieqi cookie 同步给 KMP。
        // KMP 探索 .net 的 articlelist/toplist/index 需要 jieqi cookie 才返回 200，
        // 否则被弹回登录页（302）导致「查看全部」加载不出新书。
        if let swiftCookie = swift.savedCookie, swiftCookie.contains("jieqiUserInfo") {
            kmp.savedCookie = swiftCookie
        }
    }

    func logout() {
        kmp.logout()
        swift.logout()
    }

    var savedCookie: String? {
        get { swift.savedCookie ?? kmp.savedCookie }
        set {
            // 同时设置两边（KMP 探索 + Swift 搜索各用各的）
            kmp.savedCookie = newValue
            swift.savedCookie = newValue
        }
    }
}
