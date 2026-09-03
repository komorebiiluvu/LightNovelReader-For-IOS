import Foundation

enum BookSourceError: LocalizedError {
    case unreachable
    case bookNotFound(source: String)
    case contentUnavailable(title: String)
    case loginRequired
    case searchThrottled
    case invalidExplorePage

    var errorDescription: String? {
        switch self {
        case .unreachable:
            return "书源暂时无法连接，请稍后重试"
        case .bookNotFound(let source):
            return "「\(source)」里没有找到这本书"
        case .contentUnavailable(let title):
            return "《\(title)》因版权原因已从书源下架，无法获取正文"
        case .loginRequired:
            return "搜索需要登录：请在「设置」中登录 wenku8 账号"
        case .searchThrottled:
            return "搜索太频繁，站点要求两次搜索间隔不少于 5 秒"
        case .invalidExplorePage:
            return "书源返回了无效的分页数据，请重试"
        }
    }
}

/// 书库浏览的一个栏目（探索页「全部」板块下的子项，如「轻小说列表」「热门榜」）
struct ExploreCategory: Identifiable, Hashable {
    let id: String
    let title: String
    let path: String
    let extraParams: String
    /// 标签栏目支持服务端排序（v=1 按热度 / v=3 仅动画化）
    var supportsSort = false

    func url(page: Int, sortSuffix: String = "") -> String {
        "\(path)?page=\(page)\(extraParams)\(sortSuffix)"
    }
}

/// 探索首页的推荐块（如「新书风云榜」「本周推荐」）
struct HomeExploreBlock: Codable {
    let title: String
    let books: [Book]
}

/// 一个书源 = 目录 + 章节 + 正文 + 搜索 + 探索。
/// UI 只依赖这个协议；接入新书源时提供一个新的实现即可，换源无需改 UI。
protocol BookSourceService: AnyObject {
    var name: String { get }

    /// 书源提供的完整书目（可返回空，表示该源无内置书目、靠探索/搜索拉取）
    func fetchBooks() async throws -> [Book]

    /// 某本书的目录（章节标题列表）
    func fetchChapters(for book: Book) async throws -> [ChapterItem]

    /// 某一章的正文
    func fetchContent(for book: Book, chapter index: Int) async throws -> ChapterContent

    /// 书源内搜索
    func search(_ term: String) async throws -> [Book]

    /// 搜索能力说明（如降级提示），nil 表示无
    var searchNotice: String? { get }

    // MARK: - 登录能力（wenku8 类书源需登录态才能探索/搜索）

    /// 是否已登录（不需要登录的书源默认返回 true）
    var isLoggedIn: Bool { get }

    /// 当前登录用户名（未登录/不适用返回 nil）
    var loggedInUsername: String? { get }

    /// 登录（不需要登录的书源为空实现）
    func login(username: String, password: String) async throws

    /// 退出登录（不需要登录的书源为空实现）
    func logout()

    /// 登录会话 cookie（持久化/恢复用；不需要登录的书源返回 nil）
    var savedCookie: String? { get set }

    // MARK: - 探索能力（默认空实现，不支持的源返回空/抛错）

    /// 探索页「全部」板块的栏目列表；空数组表示该源无书库浏览能力
    var exploreCategories: [ExploreCategory] { get }

    /// 拉取某个栏目的一页
    func fetchExplorePage(_ category: ExploreCategory, page: Int, sortSuffix: String) async throws -> (books: [Book], totalPages: Int)

    /// 探索页「首页」板块的推荐块
    func fetchHomeBlocks() async throws -> [HomeExploreBlock]

    /// 探索页「分类」板块的标签列表
    func fetchTagList() async throws -> [String]

    /// 标签 → 书库浏览栏目（tags.php?t= 分页）
    func tagCategory(_ tag: String) -> ExploreCategory

    /// 按 wenku8 aid 补全某本书的完整元数据（简介/作者/字数/更新时间等）
    func fetchBookDetail(aid: Int) async throws -> Book
}

extension BookSourceService {
    var searchNotice: String? { nil }

    // 登录能力默认实现：不需要登录
    var isLoggedIn: Bool { true }
    var loggedInUsername: String? { nil }
    func login(username: String, password: String) async throws {}
    func logout() {}
    var savedCookie: String? {
        get { nil }
        set {}
    }

    // 探索能力默认实现：不支持
    var exploreCategories: [ExploreCategory] { [] }
    func fetchExplorePage(_ category: ExploreCategory, page: Int, sortSuffix: String) async throws -> (books: [Book], totalPages: Int) {
        throw BookSourceError.unreachable
    }
    func fetchHomeBlocks() async throws -> [HomeExploreBlock] {
        throw BookSourceError.unreachable
    }
    func fetchTagList() async throws -> [String] {
        throw BookSourceError.unreachable
    }
    func tagCategory(_ tag: String) -> ExploreCategory {
        ExploreCategory(id: "tag-\(tag)", title: tag, path: "/modules/article/tags.php", extraParams: "&t=\(tag)", supportsSort: true)
    }
    func fetchBookDetail(aid: Int) async throws -> Book {
        throw BookSourceError.unreachable
    }
}
