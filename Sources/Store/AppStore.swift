import SwiftUI
import Combine

enum ThemePreference: String, CaseIterable, Identifiable, Codable {
    case system, light, dark
    var id: String { rawValue }

    var label: String {
        switch self {
        case .system: return "跟随系统"
        case .light: return "浅色"
        case .dark: return "深色"
        }
    }

    var colorScheme: ColorScheme? {
        switch self {
        case .system: return nil
        case .light: return .light
        case .dark: return .dark
        }
    }
}

enum ShelfFilter: String, CaseIterable, Identifiable {
    case all = "全部"
    case saved = "收藏"
    case updated = "已更新"
    var id: String { rawValue }
}

enum ShelfSort: String, CaseIterable, Identifiable {
    case recent = "最近阅读"
    case title = "书名"
    case update = "更新时间"
    var id: String { rawValue }
}

/// 阅读器偏好，跨书籍共享并持久化
struct ReaderPreferences: Codable {
    var fontSize: CGFloat = 22
    var lineSpacing: CGFloat = 8
    var backgroundIndex: Int = 0
    var mode: ReaderMode = .flip
    var fontFamily: ReaderFontFamily = .kaiti
    var bold: Bool = false
    var marginLeft: CGFloat = 35
    var marginRight: CGFloat = 35
    var marginTop: CGFloat = 72
    var marginBottom: CGFloat = 24

    init() {}

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        fontSize = try container.decodeIfPresent(CGFloat.self, forKey: .fontSize) ?? 22
        lineSpacing = try container.decodeIfPresent(CGFloat.self, forKey: .lineSpacing) ?? 8
        backgroundIndex = try container.decodeIfPresent(Int.self, forKey: .backgroundIndex) ?? 0
        mode = try container.decodeIfPresent(ReaderMode.self, forKey: .mode) ?? .flip
        fontFamily = try container.decodeIfPresent(ReaderFontFamily.self, forKey: .fontFamily) ?? .kaiti
        bold = try container.decodeIfPresent(Bool.self, forKey: .bold) ?? false
        marginLeft = try container.decodeIfPresent(CGFloat.self, forKey: .marginLeft) ?? 35
        marginRight = try container.decodeIfPresent(CGFloat.self, forKey: .marginRight) ?? 35
        marginTop = try container.decodeIfPresent(CGFloat.self, forKey: .marginTop) ?? 72
        marginBottom = try container.decodeIfPresent(CGFloat.self, forKey: .marginBottom) ?? 24
    }
}

/// 持久化到 UserDefaults 的状态快照。
/// 字段全部带默认值解码，旧版本快照缺少新字段时也能正常加载。
struct AppStateSnapshot: Codable {
    var theme: ThemePreference = .system
    var preferredSource: String = "文库8(在线)"
    var savedIDs: Set<String> = []
    var searchHistory: [String] = []
    var readerPreferences = ReaderPreferences()
    var lastChapterByID: [String: Int] = [:]
    var updateFlagIDs: Set<String> = []
    var sourceByID: [String: String] = [:]
    var shelves: [BookShelf] = []
    var selectedShelfID: String?
    var dailyStats: [String: DailyStat] = [:]
    var bookReadingSeconds: [String: Int] = [:]
    var chapterOffsetByID: [String: Double] = [:]
    /// 每本书上次已知的章节数（更新检测的比对基准）
    var knownTotalChaptersByID: [String: Int] = [:]
    /// 用户接触过的所有书（含非精选书源的收藏/浏览记录），重启后据此重建 books
    var bookLibrary: [Book] = []
    /// 已读过的书（进入过阅读器即标记，不依赖进度绝对值，读第1章也能进最近阅读）
    var readBookIDs: Set<String> = []
    /// 每本书的最后阅读时间（用于「最近阅读」按时间排序）
    var lastReadAtByID: [String: Date] = [:]
    /// 手动合并：bookID → 分组 ID（同一分组 ID 的书视为同一本书）
    var manualGroupByID: [String: String] = [:]
    /// 手动合并后的显示书名：分组 ID → 显示名
    var groupDisplayName: [String: String] = [:]
    /// 手动拆分：这些 bookID 不参与任何合并（单独成条）
    var splitBookIDs: Set<String> = []

    init() {}

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        theme = try container.decodeIfPresent(ThemePreference.self, forKey: .theme) ?? .system
        preferredSource = try container.decodeIfPresent(String.self, forKey: .preferredSource) ?? "文库8(在线)"
        savedIDs = try container.decodeIfPresent(Set<String>.self, forKey: .savedIDs) ?? []
        searchHistory = try container.decodeIfPresent([String].self, forKey: .searchHistory) ?? []
        readerPreferences = try container.decodeIfPresent(ReaderPreferences.self, forKey: .readerPreferences) ?? ReaderPreferences()
        lastChapterByID = try container.decodeIfPresent([String: Int].self, forKey: .lastChapterByID) ?? [:]
        updateFlagIDs = try container.decodeIfPresent(Set<String>.self, forKey: .updateFlagIDs) ?? []
        sourceByID = try container.decodeIfPresent([String: String].self, forKey: .sourceByID) ?? [:]
        shelves = try container.decodeIfPresent([BookShelf].self, forKey: .shelves) ?? []
        selectedShelfID = try container.decodeIfPresent(String.self, forKey: .selectedShelfID)
        dailyStats = try container.decodeIfPresent([String: DailyStat].self, forKey: .dailyStats) ?? [:]
        bookReadingSeconds = try container.decodeIfPresent([String: Int].self, forKey: .bookReadingSeconds) ?? [:]
        chapterOffsetByID = try container.decodeIfPresent([String: Double].self, forKey: .chapterOffsetByID) ?? [:]
        knownTotalChaptersByID = try container.decodeIfPresent([String: Int].self, forKey: .knownTotalChaptersByID) ?? [:]
        bookLibrary = try container.decodeIfPresent([Book].self, forKey: .bookLibrary) ?? []
        readBookIDs = try container.decodeIfPresent(Set<String>.self, forKey: .readBookIDs) ?? []
        lastReadAtByID = try container.decodeIfPresent([String: Date].self, forKey: .lastReadAtByID) ?? [:]
        manualGroupByID = try container.decodeIfPresent([String: String].self, forKey: .manualGroupByID) ?? [:]
        groupDisplayName = try container.decodeIfPresent([String: String].self, forKey: .groupDisplayName) ?? [:]
        splitBookIDs = try container.decodeIfPresent(Set<String>.self, forKey: .splitBookIDs) ?? []
    }

    static func load() -> AppStateSnapshot {
        guard let data = UserDefaults.standard.data(forKey: AppStore.storageKey),
              let snapshot = try? JSONDecoder().decode(AppStateSnapshot.self, from: data) else {
            return AppStateSnapshot()
        }
        return snapshot
    }
}

@MainActor
final class AppStore: ObservableObject {
    nonisolated static let storageKey = "lightnovelreader.app_state.v1"
    private static let maxSearchHistory = 10
    private static let maxContentCache = 40

    // MARK: - 状态

    @Published private(set) var books: [Book] = []
    @Published var theme: ThemePreference
    @Published var source: String
    @Published var savedIDs: Set<String>
    @Published var searchHistory: [String]
    @Published var readerPreferences: ReaderPreferences
    @Published var shelfFilter: ShelfFilter = .all
    @Published var shelfSort: ShelfSort = .recent
    @Published var shelfSortAscending = false

    // 多书架
    @Published private(set) var shelves: [BookShelf]
    @Published var selectedShelfID: String? = nil

    // 同名书合并（手动分组/拆分/改名）
    @Published private(set) var manualGroupByID: [String: String]
    @Published private(set) var groupDisplayName: [String: String]
    @Published private(set) var splitBookIDs: Set<String>

    // 书源加载状态
    @Published private(set) var isLoading = false
    @Published var loadError: String?
    @Published private(set) var chaptersByBook: [String: [ChapterItem]] = [:]

    // 搜索状态（提交后走书源，输入过程仍是本地过滤）
    @Published private(set) var isSearching = false
    @Published private(set) var searchResults: [Book] = []
    @Published private(set) var submittedTerm = ""
    @Published private(set) var searchError: String?

    // 阅读统计
    @Published private(set) var dailyStats: [String: DailyStat]
    @Published private(set) var bookReadingSeconds: [String: Int]
    /// 每本书的章内阅读位置（0~1，滚动模式恢复用）
    @Published private(set) var chapterOffsetByID: [String: Double]
    /// 每本书上次已知的章节数（更新检测基准）
    @Published private(set) var knownTotalChaptersByID: [String: Int]
    /// 已读过的书（进入过阅读器即标记）
    @Published private(set) var readBookIDs: Set<String>
    /// 每本书的最后阅读时间
    @Published private(set) var lastReadAtByID: [String: Date]

    // 离线缓存与导出
    struct OfflineProgress: Equatable {
        var done: Int
        var total: Int
    }
    @Published private(set) var offlineProgress: [String: OfflineProgress] = [:]
    @Published private(set) var offlineBookCounts: [String: Int] = [:]
    private var offlineTasks: [String: Task<Void, Never>] = [:]
    private let disk = ChapterDiskCache.shared

    // MARK: - 私有

    private let services: [String: BookSourceService]
    let sourceNames: [String]
    private var initialSnapshot: AppStateSnapshot
    /// 用户在书籍详情页“换源”产生的按书覆盖，优先于全局书源
    private var sourceOverrideByID: [String: String]
    private var contentCache: [String: ChapterContent] = [:]
    private var contentCacheOrder: [String] = []
    private var chapterLoadTasks: [String: Task<[ChapterItem]?, Never>] = [:]
    /// 每个探索栏目当前有效请求的标识。刷新会使旧请求的迟到结果失效。
    private var exploreRequestIDs: [String: UUID] = [:]
    private var bootstrapped = false
    /// 后台登录重试是否已执行过（避免每次启动重复排队）
    private var bootstrappedRetryDone = false
    private var lastUpdateCheckAt: Date?
    private var updateCheckTask: Task<Void, Never>?
    private var isCheckingForUpdates = false
    private var cancellables: Set<AnyCancellable> = []
    private var persistDebounceTask: Task<Void, Never>?
    /// `exploreBrowse` 只包含远端分页结果和加载状态，不属于 AppStateSnapshot。
    /// 标记它的下一次发布，避免无意义地全量编码书库和阅读统计。
    private var suppressNextAutomaticPersist = false

    private struct ChapterUpdateResult {
        let book: Book
        let chapters: [ChapterItem]
    }

    init(services: [BookSourceService] = AppStore.makeDefaultServices()) {
        let snapshot = AppStateSnapshot.load()
        initialSnapshot = snapshot
        sourceNames = services.map(\.name)
        self.services = Dictionary(uniqueKeysWithValues: services.map { ($0.name, $0) })

        theme = snapshot.theme
        source = snapshot.preferredSource
        savedIDs = snapshot.savedIDs
        searchHistory = snapshot.searchHistory
        readerPreferences = snapshot.readerPreferences
        sourceOverrideByID = snapshot.sourceByID
        shelves = snapshot.shelves
        selectedShelfID = snapshot.selectedShelfID
        dailyStats = snapshot.dailyStats
        bookReadingSeconds = snapshot.bookReadingSeconds
        chapterOffsetByID = snapshot.chapterOffsetByID
        knownTotalChaptersByID = snapshot.knownTotalChaptersByID
        readBookIDs = snapshot.readBookIDs
        lastReadAtByID = snapshot.lastReadAtByID
        manualGroupByID = snapshot.manualGroupByID
        groupDisplayName = snapshot.groupDisplayName
        splitBookIDs = snapshot.splitBookIDs
        // 精选书单已移除：books 从持久化的 bookLibrary 恢复（含用户收藏的非精选书）
        books = snapshot.bookLibrary

        // 旧版模拟书源改名迁移：旧「文库8」按名字意图直接转到真实源
        let renamedSources = [
            "文库8": "文库8(在线)",
            "轻小说源A": "示例·轻小说A",
            "轻小说源B": "示例·轻小说B",
        ]
        if self.services[source] == nil {
            source = renamedSources[source] ?? sourceNames.first ?? source
        }
        for (bookID, name) in sourceOverrideByID where self.services[name] == nil {
            if let migrated = renamedSources[name] {
                sourceOverrideByID[bookID] = migrated
            } else {
                sourceOverrideByID.removeValue(forKey: bookID)
            }
        }

        // objectWillChange 发生在变更前，包一层 Task 等变更落地后再序列化。
        // 阅读设置滑杆/步进器拖动时对象会高频触发，持久化做 300ms 防抖合并，
        // 避免每次拖动都全量 JSON 编码写 UserDefaults（老设备上的卡顿来源之一）。
        objectWillChange
            .sink { [weak self] _ in
                guard let self else { return }
                if self.suppressNextAutomaticPersist {
                    self.suppressNextAutomaticPersist = false
                    return
                }
                self.persistDebounceTask?.cancel()
                let task = Task { @MainActor [weak self] in
                    try? await Task.sleep(nanoseconds: 300_000_000)
                    guard !Task.isCancelled else { return }
                    self?.persist()
                }
                self.persistDebounceTask = task
            }
            .store(in: &cancellables)
    }

    /// 发布探索页瞬时状态，但不触发与它无关的 AppStateSnapshot 落盘。
    private func setExploreBrowseState(_ state: ExploreBrowseState?, forKey key: String) {
        suppressNextAutomaticPersist = true
        exploreBrowse[key] = state
        // `@Published` 当前同步发送 objectWillChange；仍显式复位，防止未来实现变化时
        // 标记泄漏并误跳过下一次真正需要持久化的状态更新。
        suppressNextAutomaticPersist = false
    }

    // MARK: - 持久化

    nonisolated private static func makeDefaultServices() -> [BookSourceService] {
        // KMP 数据互通：SharedKit 编译进工程时用 KMP 适配器（与上游数据层同源），否则回退纯 Swift 爬虫
        [KmpBookSourceServiceFactory.makeDefaultService()]
    }

    private func persist() {
        if let data = try? JSONEncoder().encode(makeSnapshot()) {
            UserDefaults.standard.set(data, forKey: Self.storageKey)
        }
    }

    /// 同步落盘（退出阅读器等关键时机调用，避免异步 persist 来不及写入进度就丢失）
    func flushPersist() {
        persist()
    }

    private func makeSnapshot() -> AppStateSnapshot {
        var snapshot = AppStateSnapshot()
        snapshot.theme = theme
        snapshot.preferredSource = source
        snapshot.savedIDs = savedIDs
        snapshot.searchHistory = searchHistory
        snapshot.readerPreferences = readerPreferences
        snapshot.lastChapterByID = Dictionary(uniqueKeysWithValues: books.map { ($0.id, $0.lastChapter) })
        snapshot.updateFlagIDs = Set(books.filter(\.hasUpdate).map(\.id))
        snapshot.sourceByID = sourceOverrideByID
        snapshot.shelves = shelves
        snapshot.selectedShelfID = selectedShelfID
        snapshot.dailyStats = dailyStats
        snapshot.bookReadingSeconds = bookReadingSeconds
        snapshot.chapterOffsetByID = chapterOffsetByID
        snapshot.knownTotalChaptersByID = knownTotalChaptersByID
        snapshot.bookLibrary = books
        snapshot.readBookIDs = readBookIDs
        snapshot.lastReadAtByID = lastReadAtByID
        snapshot.manualGroupByID = manualGroupByID
        snapshot.groupDisplayName = groupDisplayName
        snapshot.splitBookIDs = splitBookIDs
        return snapshot
    }

    // MARK: - 书源加载

    /// App 启动后调用一次
    func bootstrap() async {
        guard !bootstrapped else { return }
        bootstrapped = true
        // 启动只快速尝试一次登录（不重试，避免无网时拖住启动页）；失败后由后台任务自动重试
        await ensureWenku8Login(allowRetry: false)
        scheduleBackgroundLoginRetry()
        await reload()
        scheduleUpdateCheck()
        // 启动预加载：后台并行拉探索数据，不阻塞 splash（失败/登录重试都在后台，用户无感）
        Task { @MainActor [weak self] in
            await self?.prewarmExplore()
        }
        Task.detached(priority: .utility) {
            CoverImageCache.shared.pruneOlderThan(days: 14)
        }
        prewarmCoverCache()
    }

    /// 更新检查不参与开屏等待；大量书架或弱网时也能先进入 App。
    private func scheduleUpdateCheck() {
        guard updateCheckTask == nil else { return }
        updateCheckTask = Task { @MainActor [weak self] in
            guard let self else { return }
            await self.checkForUpdates()
            self.updateCheckTask = nil
        }
    }

    /// 后台自动重试登录（不阻塞 UI；网络抖动/站点短暂不可达时自愈，用户无需手动登录）
    private func scheduleBackgroundLoginRetry() {
        let delay: UInt64 = 8_000_000_000
        let retryCount = 3
        Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: delay)
            guard let self, !self.bootstrappedRetryDone else { return }
            self.bootstrappedRetryDone = true
            await self.ensureWenku8Login(allowRetry: true, maxAttempts: retryCount)
        }
    }

    /// 启动预加载探索数据（登录态就绪后调用，并行拉取）
    private func prewarmExplore() async {
        async let home: Void = loadHomeBlocks()
        async let tags: Void = loadTagList()
        _ = await (home, tags)
        // 各栏目第一页并行拉取（wenku8 有 1 秒限速，串行会拖 6 秒+）
        if let categories = exploreCategories {
            await withTaskGroup(of: Void.self) { group in
                for category in categories {
                    // 这里只预取书目数据，不下载 6 个栏目共几十张不可见封面；
                    // 否则这些后台请求会抢占用户当前页面的图片连接。
                    group.addTask { await self.loadExplore(category) }
                }
            }
        }
    }

    /// 未登录时用内置账号自动登录。
    /// - allowRetry=false：启动时快速尝试一次，失败即返回（不拖住启动页）
    /// - allowRetry=true：后台重试（网络抖动/站点短暂不可达时自愈），无需用户手动登录——用户大多没有文库吧账号
    private func ensureWenku8Login(allowRetry: Bool = false, maxAttempts: Int = 1) async {
        guard let service = wenku8Service, !service.isLoggedIn else { return }
        let attempts = allowRetry ? maxAttempts : 1
        for attempt in 0..<attempts {
            do {
                try await service.login(
                    username: Self.bundledWenku8Username,
                    password: Self.bundledWenku8Password
                )
                return
            } catch {
                if attempt < attempts - 1 {
                    try? await Task.sleep(nanoseconds: UInt64(attempt + 1) * 3_000_000_000)
                }
            }
        }
    }

    /// 低优先级预热书架首行封面，下次启动可直接命中缓存。
    private func prewarmCoverCache() {
        let urls = books.prefix(CoverLoadingPolicy.defaultPrewarmLimit).compactMap(\.coverURL)
        CoverImageCache.shared.prewarm(urls)
    }

    /// 更新检测：重新拉取书架内各书的目录，章节数比上次已知的多则点亮“有更新”角标。
    /// 启动时自动执行（15 分钟节流），书架下拉刷新时强制执行。
    func checkForUpdates(force: Bool = false) async {
        guard !isCheckingForUpdates else { return }
        if !force, let last = lastUpdateCheckAt, Date().timeIntervalSince(last) < 15 * 60 { return }
        isCheckingForUpdates = true
        defer { isCheckingForUpdates = false }
        lastUpdateCheckAt = Date()

        let shelfIDs = savedIDs.union(shelves.flatMap(\.bookIDs))
        let candidates: [(Book, BookSourceService)] = shelfIDs.compactMap { id in
            guard let book = book(withID: id), let service = services[book.source] else { return nil }
            return (book, service)
        }

        // 文库站点不适合高并发；最多同时检查两本，兼顾速度与限流。
        await withTaskGroup(of: ChapterUpdateResult?.self) { group in
            var next = 0
            let concurrency = min(2, candidates.count)
            while next < concurrency {
                let (book, service) = candidates[next]
                next += 1
                group.addTask {
                    guard let chapters = try? await service.fetchChapters(for: book) else { return nil }
                    return ChapterUpdateResult(book: book, chapters: chapters)
                }
            }

            while let result = await group.next() {
                if let result {
                    await applyChapterUpdate(result)
                }
                guard !Task.isCancelled else {
                    group.cancelAll()
                    continue
                }
                if next < candidates.count {
                    let (book, service) = candidates[next]
                    next += 1
                    group.addTask {
                        guard let chapters = try? await service.fetchChapters(for: book) else { return nil }
                        return ChapterUpdateResult(book: book, chapters: chapters)
                    }
                }
            }
        }
    }

    private func applyChapterUpdate(_ result: ChapterUpdateResult) async {
        let id = result.book.id
        let freshChapters = result.chapters
        if let known = knownTotalChaptersByID[id], freshChapters.count > known,
           let index = books.firstIndex(where: { $0.id == id }) {
            books[index].hasUpdate = true
        }
        knownTotalChaptersByID[id] = freshChapters.count
        chaptersByBook[id] = freshChapters
        await disk.storeChapters(freshChapters, bookID: id, source: result.book.source)

        if let index = books.firstIndex(where: { $0.id == id }),
           books[index].totalChapters != freshChapters.count {
            books[index].totalChapters = freshChapters.count
            books[index].lastChapter = Self.clampChapter(
                books[index].lastChapter,
                total: freshChapters.count
            )
        }
    }

    /// 从当前全局书源重新拉取书目，并叠加本地阅读状态；断网时回退到磁盘缓存的书目。
    /// 书源无内置书目（如文库8已移除精选书单）时不清空现有 books。
    func reload() async {
        guard let service = services[source] else { return }
        isLoading = true
        do {
            let fetched = try await service.fetchBooks()
            if !fetched.isEmpty {
                books = merge(fetched)
                await disk.storeCatalog(books, source: source)
            }
            loadError = nil
        } catch {
            if books.isEmpty, let cached = await disk.catalog(source: source), !cached.isEmpty {
                books = merge(cached)
                loadError = "离线模式：正在显示已缓存的书目"
            } else if !books.isEmpty {
                // 有本地书目（用户收藏/浏览过的书），书源失败不影响已有数据
                loadError = nil
            } else {
                loadError = "「\(service.name)」连接失败：\(error.localizedDescription)"
            }
        }
        isLoading = false
    }

    func setGlobalSource(_ name: String) async {
        guard name != source, services[name] != nil else { return }
        source = name
        searchResults = []
        submittedTerm = ""
        searchError = nil
        exploreBrowse = [:]
        exploreRequestIDs.removeAll()
        homeBlocks = []
        homeBlocksError = nil
        tagList = []
        tagListError = nil
        await reload()
    }

    /// 书籍详情页“换源”：只影响这一本书，并同步刷新它的目录。失败时抛出，由调用方提示。
    func switchBookSource(_ book: Book, to name: String) async throws {
        guard name != book.source, let service = services[name] else { return }
        let catalog = try await service.fetchBooks()
        guard var refreshed = catalog.first(where: { $0.id == book.id }) else {
            throw BookSourceError.bookNotFound(source: name)
        }
        refreshed.source = name
        refreshed.lastChapter = Self.clampChapter(book.lastChapter, total: refreshed.totalChapters)
        refreshed.hasUpdate = book.hasUpdate
        sourceOverrideByID[book.id] = name
        if let index = books.firstIndex(where: { $0.id == book.id }) {
            books[index] = refreshed
        }
        chaptersByBook[book.id] = try await service.fetchChapters(for: refreshed)
    }

    /// 把书源返回的书目叠加本地状态（进度、更新角标、按书换源）
    private func merge(_ fetched: [Book]) -> [Book] {
        let currentByID = Dictionary(uniqueKeysWithValues: books.map { ($0.id, $0) })
        return fetched.map { fresh in
            var book = fresh
            if let override = sourceOverrideByID[book.id] {
                book.source = override
            }
            let known = currentByID[book.id]
            let lastKnown = known?.lastChapter ?? initialSnapshot.lastChapterByID[book.id] ?? fresh.lastChapter
            book.lastChapter = Self.clampChapter(lastKnown, total: book.totalChapters)
            book.hasUpdate = known?.hasUpdate ?? initialSnapshot.updateFlagIDs.contains(book.id)
            return book
        }
    }

    /// total 未知(0)时不做上限钳制，避免把已有进度压回 0
    static func clampChapter(_ chapter: Int, total: Int) -> Int {
        total > 0 ? min(max(chapter, 0), total - 1) : max(chapter, 0)
    }

    // MARK: - 目录与正文

    func loadChapters(for book: Book) async {
        guard let service = services[book.source] else { return }
        if let existing = chaptersByBook[book.id], !existing.isEmpty,
           book.totalChapters == 0 || existing.count == book.totalChapters {
            return
        }

        let taskKey = "\(book.source)#\(book.id)"
        let task: Task<[ChapterItem]?, Never>
        if let existing = chapterLoadTasks[taskKey] {
            task = existing
        } else {
            let diskCache = disk
            task = Task { [diskCache] in
                if let cached = await diskCache.chapters(bookID: book.id, source: book.source), !cached.isEmpty {
                    return cached
                }
                guard let fetched = try? await service.fetchChapters(for: book), !fetched.isEmpty else {
                    return nil
                }
                await diskCache.storeChapters(fetched, bookID: book.id, source: book.source)
                return fetched
            }
            chapterLoadTasks[taskKey] = task
        }

        let items = await task.value
        if chapterLoadTasks[taskKey] != nil {
            chapterLoadTasks[taskKey] = nil
        }
        guard let items, !items.isEmpty else { return }
        applyLoadedChapters(items, to: book)
    }

    private func applyLoadedChapters(_ items: [ChapterItem], to book: Book) {
        chaptersByBook[book.id] = items
        // 真实书源的 fetchBooks 不知道章节数，拿到目录后回填总章数
        if let index = books.firstIndex(where: { $0.id == book.id }) {
            if books[index].totalChapters != items.count {
                books[index].totalChapters = items.count
                books[index].lastChapter = Self.clampChapter(books[index].lastChapter, total: items.count)
            }
        } else {
            // 探索页/榜单点进来的书不在精选书目里：拉目录时补进 books，否则 totalChapters 永远为 0
            // （这会导致目录"加载中"、加入书架后书架/阅读也找不到它）
            var added = book
            added.totalChapters = items.count
            books.append(added)
        }
    }

    func chapterTitles(for book: Book) -> [ChapterItem]? {
        chaptersByBook[book.id]
    }

    /// 按章取正文：内存 → 磁盘 → 网络，取回后写双层缓存；插图已下载时替换为本地文件
    func chapterContent(for book: Book, index: Int) async throws -> ChapterContent {
        let key = "\(book.id)#\(index)"
        var content: ChapterContent
        if let cached = contentCache[key] {
            content = cached
        } else if let cached = await disk.content(bookID: book.id, source: book.source, index: index) {
            cache(cached, forKey: key)
            content = cached
        } else {
            guard let service = services[book.source] else {
                throw BookSourceError.unreachable
            }
            content = try await service.fetchContent(for: book, chapter: index)
            cache(content, forKey: key)
            await disk.store(content, bookID: book.id, source: book.source)
        }
        // 插图若已随全书下载缓存到本地，则替换为文件地址（离线可看）
        if !content.images.isEmpty {
            var resolved = content
            resolved.images = await resolveImages(content.images, book: book, chapterIndex: index)
            return resolved
        }
        return content
    }

    private func resolveImages(_ urls: [String], book: Book, chapterIndex: Int) async -> [String] {
        var result: [String] = []
        for (offset, urlString) in urls.enumerated() {
            let name = Self.offlineImageName(chapterIndex: chapterIndex, offset: offset, urlString: urlString)
            let legacyPrefix = "\(chapterIndex)-\(offset)-"
            if let local = await disk.imageURL(
                bookID: book.id,
                source: book.source,
                name: name,
                legacyPrefix: legacyPrefix
            ) {
                result.append(local.absoluteString)
            } else {
                result.append(urlString)
            }
        }
        return result
    }

    nonisolated static func offlineImageName(chapterIndex: Int, offset: Int, urlString: String) -> String {
        "\(chapterIndex)-\(offset)-\(StableHash.hex(urlString).prefix(24))"
    }

    private func cache(_ content: ChapterContent, forKey key: String) {
        if contentCache[key] == nil {
            contentCacheOrder.append(key)
        }
        contentCache[key] = content
        while contentCacheOrder.count > Self.maxContentCache {
            let oldest = contentCacheOrder.removeFirst()
            contentCache[oldest] = nil
        }
    }

    // MARK: - 搜索

    /// 书库浏览状态（按栏目，排序不同分开缓存）
    struct ExploreBrowseState: Equatable {
        var books: [Book] = []
        /// 随分页增量维护，避免每次追加都在主线程重新扫描全部历史书目。
        var bookIDs: Set<String> = []
        var loadedPages = 0
        var totalPages = 1
        var isLoading = false
        var error: String?

        var canLoadMore: Bool { !isLoading && loadedPages < totalPages }
    }

    @Published private(set) var exploreBrowse: [String: ExploreBrowseState] = [:]

    // 探索首页块与标签
    @Published private(set) var homeBlocks: [HomeExploreBlock] = []
    @Published private(set) var homeBlocksError: String?
    @Published private(set) var tagList: [String] = []
    @Published private(set) var tagListError: String?

    /// 当前书源支持书库浏览时返回栏目列表，否则 nil（UI 据此隐藏入口）
    var exploreCategories: [ExploreCategory]? {
        let cats = services[source]?.exploreCategories ?? []
        return cats.isEmpty ? nil : cats
    }

    private static func exploreKey(_ category: ExploreCategory, sort: String) -> String {
        "\(category.id)#\(sort)"
    }

    /// 探索数据的独立磁盘缓存（首页块/标签/各栏目第一页），二次进入秒开
    private enum exploreDiskCache {
        private static let defaults = UserDefaults.standard
        private static let homeKey = "explore.homeBlocks.v1"
        private static let tagKey = "explore.tagList.v1"
        private static let pagePrefix = "explore.page.v1."

        struct PageCache: Codable {
            let books: [Book]
            let totalPages: Int
        }

        static func storeHomeBlocks(_ blocks: [HomeExploreBlock]) {
            if let data = try? JSONEncoder().encode(blocks) { defaults.set(data, forKey: homeKey) }
        }
        static func loadHomeBlocks() -> [HomeExploreBlock]? {
            guard let data = defaults.data(forKey: homeKey) else { return nil }
            return try? JSONDecoder().decode([HomeExploreBlock].self, from: data)
        }
        static func storeTagList(_ tags: [String]) {
            defaults.set(tags, forKey: tagKey)
        }
        static func loadTagList() -> [String]? {
            defaults.stringArray(forKey: tagKey)
        }
        static func storeExplorePage(_ key: String, books: [Book], totalPages: Int) {
            if let data = try? JSONEncoder().encode(PageCache(books: books, totalPages: totalPages)) {
                defaults.set(data, forKey: pagePrefix + key)
            }
        }
        static func loadExplorePage(_ key: String) -> PageCache? {
            guard let data = defaults.data(forKey: pagePrefix + key),
                  let cache = try? JSONDecoder().decode(PageCache.self, from: data) else { return nil }
            return cache
        }
    }

    func exploreState(_ category: ExploreCategory, sort: String = "") -> ExploreBrowseState {
        exploreBrowse[Self.exploreKey(category, sort: sort)] ?? ExploreBrowseState()
    }

    func loadExplore(
        _ category: ExploreCategory,
        refresh: Bool = false,
        sort: String = ""
    ) async {
        let key = Self.exploreKey(category, sort: sort)
        if refresh {
            // 不取消底层网络连接，但让它返回后不再覆盖这次刷新结果。
            exploreRequestIDs[key] = nil
            setExploreBrowseState(nil, forKey: key)
        }
        guard exploreBrowse[key] == nil else { return }
        await fetchExplore(category, page: 1, sort: sort)
    }

    func loadMoreExplore(_ category: ExploreCategory, sort: String = "") async {
        let state = exploreState(category, sort: sort)
        guard state.canLoadMore, state.loadedPages >= 1 else { return }
        await fetchExplore(category, page: state.loadedPages + 1, sort: sort)
    }

    private func fetchExplore(
        _ category: ExploreCategory,
        page: Int,
        sort: String
    ) async {
        guard let service = services[source] else { return }
        let key = Self.exploreKey(category, sort: sort)
        var state = exploreBrowse[key] ?? ExploreBrowseState()
        guard !state.isLoading else { return }
        let requestID = UUID()
        exploreRequestIDs[key] = requestID
        // 第一页有磁盘缓存时先展示，避免二次进入白屏等待
        if page == 1, state.books.isEmpty,
           let cached = Self.exploreDiskCache.loadExplorePage(key), !cached.books.isEmpty {
            state.books = cached.books
            state.bookIDs = Set(cached.books.map(\.id))
            state.loadedPages = 1
            state.totalPages = max(cached.totalPages, 1)
            setExploreBrowseState(state, forKey: key)
        }
        state.isLoading = true
        state.error = nil
        setExploreBrowseState(state, forKey: key)

        // 自动重试（3 次，指数退避）：失败可能是未登录/网络抖动，重试前先自动登录（硬编码账号）自愈
        var lastError: Error?
        for attempt in 0..<3 {
            // 未登录则先自动登录（内置账号），成功后继续拉数据；登录失败则重试
            if !(services[source]?.isLoggedIn ?? true) {
                await ensureWenku8Login(allowRetry: true, maxAttempts: 3)
                if !(services[source]?.isLoggedIn ?? true) {
                    lastError = BookSourceError.loginRequired
                    if attempt < 2 {
                        try? await Task.sleep(nanoseconds: UInt64(attempt + 1) * 700_000_000)
                    }
                    continue
                }
            }
            do {
                let result = try await service.fetchExplorePage(category, page: page, sortSuffix: sort)
                guard exploreRequestIDs[key] == requestID else { return }
                state = exploreBrowse[key] ?? state
                let reportedTotalPages = max(result.totalPages, 1)
                // 站点返回登录页、拦截页或重复首页时，旧 KMP 层会解析成空列表/1 页。
                // 这些都不能推进页码，否则列表会被永久判定为“到底”。
                guard page <= reportedTotalPages else { throw BookSourceError.invalidExplorePage }
                var existing = state.bookIDs
                if existing.isEmpty, !state.books.isEmpty {
                    existing = Set(state.books.map(\.id))
                }
                let added = result.books.filter { existing.insert($0.id).inserted }
                if result.books.isEmpty || (page > 1 && added.isEmpty) {
                    throw BookSourceError.invalidExplorePage
                }
                state.books += added
                state.bookIDs = existing
                state.totalPages = reportedTotalPages
                state.loadedPages = page
                state.isLoading = false
                setExploreBrowseState(state, forKey: key)
                exploreRequestIDs[key] = nil
                // 不在分页发布时批量预热封面。LazyVStack 会让接近视口的 cell 自行
                // 发起延迟、可取消的加载，避免 20 本新书发布时产生 I/O/解码尖峰。
                // 缓存第一页（各栏目首页数据，二次进入秒开）
                if page == 1 {
                    Self.exploreDiskCache.storeExplorePage(key, books: state.books, totalPages: state.totalPages)
                }
                return
            } catch {
                lastError = error
                // 这不是临时网络抖动；重复页/空页重试只会再次把同一错误结果当成功。
                if let sourceError = error as? BookSourceError,
                   case .invalidExplorePage = sourceError {
                    break
                }
                if attempt < 2 {
                    try? await Task.sleep(nanoseconds: UInt64(attempt + 1) * 700_000_000)
                }
            }
        }
        guard exploreRequestIDs[key] == requestID else { return }
        state = exploreBrowse[key] ?? state
        state.isLoading = false
        // 已有首页缓存时也要展示后续页失败，否则用户会误以为已经到底且无法重试。
        state.error = lastError?.localizedDescription ?? "加载失败"
        setExploreBrowseState(state, forKey: key)
        exploreRequestIDs[key] = nil
    }

    /// 探索「首页」板块：wenku8 首页推荐块（带磁盘缓存 + 失败自动重试）
    func loadHomeBlocks(force: Bool = false) async {
        if !force, !homeBlocks.isEmpty || homeBlocksError != nil { return }
        if !force, let cached = Self.exploreDiskCache.loadHomeBlocks(), !cached.isEmpty {
            homeBlocks = cached
        }
        guard let service = services[source] else { return }
        for attempt in 0..<3 {
            // 未登录则先自动登录（内置账号）自愈
            if !(services[source]?.isLoggedIn ?? true) {
                await ensureWenku8Login(allowRetry: true, maxAttempts: 3)
                if !(services[source]?.isLoggedIn ?? true) {
                    if attempt < 2 {
                        try? await Task.sleep(nanoseconds: UInt64(attempt + 1) * 700_000_000)
                        continue
                    }
                    homeBlocksError = "登录失败，请检查网络后重试"
                    return
                }
            }
            do {
                homeBlocks = try await service.fetchHomeBlocks()
                homeBlocksError = nil
                Self.exploreDiskCache.storeHomeBlocks(homeBlocks)
                return
            } catch {
                if attempt < 2 {
                    try? await Task.sleep(nanoseconds: UInt64(attempt + 1) * 700_000_000)
                    continue
                }
                homeBlocksError = homeBlocks.isEmpty ? error.localizedDescription : nil
            }
        }
    }

    /// 探索「分类」板块：全站标签列表（带磁盘缓存）
    func loadTagList(force: Bool = false) async {
        if !force, !tagList.isEmpty || tagListError != nil { return }
        if !force, let cached = Self.exploreDiskCache.loadTagList(), !cached.isEmpty {
            tagList = cached
        }
        guard let service = services[source] else { return }
        do {
            tagList = try await service.fetchTagList()
            tagListError = nil
            Self.exploreDiskCache.storeTagList(tagList)
        } catch {
            tagListError = tagList.isEmpty ? error.localizedDescription : nil
        }
    }

    /// 补全书目元数据（进详情页时拉取完整简介/作者/字数/更新时间）。
    /// 搜索/探索列表里的书简介被截断，这里无条件重拉完整详情（保留进度与更新角标）。
    func refreshBookDetail(_ book: Book) async {
        guard book.id.hasPrefix("wk8-"),
              let aid = Int(book.id.dropFirst(4)),
              let service = services[book.source] else { return }
        guard let fresh = try? await service.fetchBookDetail(aid: aid) else { return }
        if let index = books.firstIndex(where: { $0.id == fresh.id }) {
            var merged = fresh
            merged.lastChapter = books[index].lastChapter
            merged.hasUpdate = books[index].hasUpdate
            // 保留原有的 totalChapters（详情页拉目录后才回填，避免被 fresh 的 0 覆盖）
            merged.totalChapters = books[index].totalChapters
            books[index] = merged
        } else {
            books.append(fresh)
        }
    }

    /// 提交搜索：记录历史并请求当前书源
    func submitSearch(_ rawTerm: String) async {
        addSearchTerm(rawTerm)
        await runSearch(rawTerm)
    }

    func runSearch(_ rawTerm: String) async {
        let term = rawTerm.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !term.isEmpty, let service = services[source] else { return }
        submittedTerm = term
        isSearching = true
        do {
            searchResults = try await service.search(term)
            searchError = nil
        } catch {
            searchResults = []
            searchError = "「\(service.name)」搜索失败，请重试"
        }
        isSearching = false
    }

    // MARK: - 查询

    func book(withID id: String) -> Book? {
        books.first(where: { $0.id == id })
    }

    var searchNotice: String? {
        services[source]?.searchNotice
    }

    // MARK: - 同名书合并

    /// 归一化书名：用于自动同名合并（去掉书名后的括号注释、空白、标点差异）
    static func normalizedTitle(_ title: String) -> String {
        title
            .replacingOccurrences(of: "[（(][^）)]*[）)]", with: "", options: .regularExpression)
            .replacingOccurrences(of: "「」", with: "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
    }

    /// 某本书所在的分组 ID：手动拆分优先；手动合并其次；否则按归一化书名自动分组
    func groupID(for book: Book) -> String? {
        if splitBookIDs.contains(book.id) { return nil }  // 手动拆分 = 不合并
        if let manual = manualGroupByID[book.id] { return manual }
        let key = Self.normalizedTitle(book.title)
        return key.isEmpty ? nil : "auto:\(key)"
    }

    /// 同一分组内某书可切换到的其他源版本
    func siblingVersions(of book: Book) -> [Book] {
        guard let gid = groupID(for: book) else { return [book] }
        return books.filter { groupID(for: $0) == gid }
    }

    /// 展示用书名：分组名（若手动改过）优先，否则取该组第一本书名
    func displayTitle(for book: Book) -> String {
        if let gid = groupID(for: book), let name = groupDisplayName[gid], !name.isEmpty {
            return name
        }
        return book.title
    }

    /// 手动把两本书合并成一个分组
    func mergeBooks(_ a: Book, _ b: Book) {
        let gid = "manual:\(UUID().uuidString.prefix(8))"
        manualGroupByID[a.id] = gid
        manualGroupByID[b.id] = gid
        // 拆分标记清除
        splitBookIDs.remove(a.id)
        splitBookIDs.remove(b.id)
    }

    /// 把书从合并中拆出（单独成条）
    func splitBook(_ book: Book) {
        splitBookIDs.insert(book.id)
        manualGroupByID[book.id] = nil
    }

    /// 重命名分组显示名
    func renameGroup(for book: Book, to name: String) {
        guard let gid = groupID(for: book) else { return }
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            groupDisplayName[gid] = nil
        } else {
            groupDisplayName[gid] = trimmed
        }
    }

    /// 阅读页「最近阅读」：合并同名书后，按「最后阅读时间」倒序（最近读的在最上面）。
    /// 用「已读标记」判断（进入过阅读器即标记），读第 1 章也能进最近阅读。
    var recentBooks: [Book] {
        let read = books.filter { readBookIDs.contains($0.id) }
        return mergedShelfBooks(read).sorted {
            (lastReadAtByID[$0.id] ?? .distantPast) > (lastReadAtByID[$1.id] ?? .distantPast)
        }
    }

    /// 阅读页「更新提醒」：合并同名书
    var updatedBooks: [Book] {
        mergedShelfBooks(books.filter { $0.hasUpdate })
    }

    /// 书架分组后的条目：同一组返回一本「代表书」（优先有进度的、当前源的）
    private func mergedShelfBooks(_ scoped: [Book]) -> [Book] {
        var groups: [String: [Book]] = [:]
        var order: [String] = []
        for book in scoped {
            let gid = groupID(for: book) ?? "solo:\(book.id)"
            if groups[gid] == nil { order.append(gid) }
            groups[gid, default: []].append(book)
        }
        var result: [Book] = []
        for gid in order {
            guard let group = groups[gid], !group.isEmpty else { continue }
            // 选代表：优先有进度的、其次当前源、再其次章节多的
            let representative = group.first(where: { $0.progress > 0 })
                ?? group.first(where: { $0.source == source })
                ?? group.sorted { $0.totalChapters > $1.totalChapters }.first!
            result.append(representative)
        }
        return result
    }

    /// 书架作用域内的书：只显示用户收藏（默认书架=savedIDs，或对应自定义书架）。
    /// nil 与 "default" 均视为默认书架，避免把书源书目误当收藏展示。
    var shelfScopedBooks: [Book] {
        if selectedShelfID == nil || selectedShelfID == "default" {
            return books.filter { savedIDs.contains($0.id) }
        }
        guard let shelf = shelves.first(where: { $0.id == selectedShelfID }) else {
            return books.filter { savedIDs.contains($0.id) }
        }
        let byID = Dictionary(uniqueKeysWithValues: books.map { ($0.id, $0) })
        return shelf.bookIDs.compactMap { byID[$0] }
    }

    var filteredBooks: [Book] {
        let scoped = shelfScopedBooks
        let merged = mergedShelfBooks(scoped)
        let filtered: [Book]
        switch shelfFilter {
        case .all: filtered = merged
        case .saved: filtered = merged.filter { savedIDs.contains($0.id) }
        case .updated: filtered = merged.filter { $0.hasUpdate }
        }
        let sorted: [Book]
        switch shelfSort {
        case .recent:
            // 最近阅读：按最后阅读时间倒序（最近读的在最上面）
            sorted = filtered.sorted {
                (lastReadAtByID[$0.id] ?? .distantPast) > (lastReadAtByID[$1.id] ?? .distantPast)
            }
        case .title:
            sorted = filtered.sorted { displayTitle(for: $0).localizedStandardCompare(displayTitle(for: $1)) == .orderedAscending }
        case .update:
            sorted = filtered.sorted { ($0.lastUpdate ?? "") > ($1.lastUpdate ?? "") }
        }
        // shelfSortAscending = true 时反转（正序/倒序切换）
        return shelfSortAscending ? sorted.reversed() : sorted
    }

    // MARK: - 书架

    func isSaved(_ book: Book) -> Bool {
        savedIDs.contains(book.id) || shelves.contains { $0.bookIDs.contains(book.id) }
    }

    func toggleSaved(_ book: Book) {
        if savedIDs.contains(book.id) {
            savedIDs.remove(book.id)
        } else {
            savedIDs.insert(book.id)
            // 确保书在 books 里（探索页点进来的书不在精选书目，收藏后才能被书架/阅读找到）
            if !books.contains(where: { $0.id == book.id }) {
                books.append(book)
            }
        }
    }

    // MARK: - 自定义书架

    func addShelf(named rawName: String) {
        let name = rawName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty else { return }
        let shelf = BookShelf(id: "shelf-\(UUID().uuidString.prefix(8))", name: name, bookIDs: [])
        shelves.append(shelf)
        selectedShelfID = shelf.id
    }

    func renameShelf(id: String, to rawName: String) {
        let name = rawName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty, let index = shelves.firstIndex(where: { $0.id == id }) else { return }
        shelves[index].name = name
    }

    func deleteShelf(id: String) {
        shelves.removeAll { $0.id == id }
        if selectedShelfID == id {
            selectedShelfID = nil
        }
    }

    func isBook(_ bookID: String, inShelf shelfID: String) -> Bool {
        shelves.first(where: { $0.id == shelfID })?.bookIDs.contains(bookID) ?? false
    }

    func toggleBook(_ bookID: String, inShelf shelfID: String) {
        guard let index = shelves.firstIndex(where: { $0.id == shelfID }) else { return }
        if let offset = shelves[index].bookIDs.firstIndex(of: bookID) {
            shelves[index].bookIDs.remove(at: offset)
        } else {
            shelves[index].bookIDs.append(bookID)
        }
    }

    // MARK: - 阅读进度

    /// 阅读器在进入、切章、退出时调用；读到最新章会清掉“有更新”角标
    /// 确保书在 books 里（阅读前调用），并标记为「已读」，这样未加书架的书也能进最近阅读
    func registerReading(_ book: Book) {
        if !books.contains(where: { $0.id == book.id }) {
            books.append(book)
        }
        readBookIDs.insert(book.id)
        lastReadAtByID[book.id] = Date()
    }

    func recordReading(bookID: String, chapter: Int) {
        guard let index = books.firstIndex(where: { $0.id == bookID }) else { return }
        books[index].lastChapter = Self.clampChapter(chapter, total: books[index].totalChapters)
        if books[index].hasReadLatest {
            books[index].hasUpdate = false
        }
    }

    /// 不改变阅读进度，仅消掉“有更新”角标
    func clearUpdateFlag(for bookID: String) {
        guard let index = books.firstIndex(where: { $0.id == bookID }) else { return }
        books[index].hasUpdate = false
    }

    func resetReadingProgress() {
        books = books.map { book in
            var book = book
            book.lastChapter = 0
            book.hasUpdate = false
            return book
        }
        chapterOffsetByID = [:]
    }

    // MARK: - 搜索历史

    func addSearchTerm(_ rawTerm: String) {
        let term = rawTerm.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !term.isEmpty else { return }
        searchHistory.removeAll { $0 == term }
        searchHistory.insert(term, at: 0)
        if searchHistory.count > Self.maxSearchHistory {
            searchHistory = Array(searchHistory.prefix(Self.maxSearchHistory))
        }
    }

    func clearSearchHistory() {
        searchHistory = []
    }

    /// 标签 → 书库浏览栏目（转发到当前书源，KMP/纯 Swift 皆可）
    func tagCategory(_ tag: String) -> ExploreCategory {
        services[source]?.tagCategory(tag) ?? ExploreCategory(id: "tag-\(tag)", title: tag, path: "/modules/article/tags.php", extraParams: "&t=\(tag)", supportsSort: true)
    }

    // MARK: - wenku8 账号

    /// 内置账号（与 KMP 门面 bundledUsername/bundledPassword 一致；协议解耦后统一从这里取）
    private static let bundledWenku8Username = "komorebiiluv"
    private static let bundledWenku8Password = "komorebi041016"

    /// 当前 wenku8 书源（协议类型，KMP 适配器或纯 Swift 实现皆可）
    private var wenku8Service: BookSourceService? {
        services["文库8(在线)"]
    }

    var isWenku8LoggedIn: Bool {
        wenku8Service?.isLoggedIn ?? false
    }

    var wenku8Username: String? {
        wenku8Service?.loggedInUsername
    }

    func loginWenku8(username: String, password: String) async throws {
        guard let service = wenku8Service else {
            throw BookSourceError.bookNotFound(source: "文库8(在线)")
        }
        try await service.login(username: username, password: password)
        objectWillChange.send()
    }

    func logoutWenku8() {
        wenku8Service?.logout()
        objectWillChange.send()
    }

    // MARK: - 备份与恢复

    enum BackupError: LocalizedError {
        case invalidFile
        var errorDescription: String? { "备份文件格式不正确" }
    }

    private struct BackupEnvelope: Codable {
        var format: String
        var version: Int
        var exportedAt: String
        var wenku8Cookie: String?
        var state: AppStateSnapshot
    }

    /// 全量备份（书架/进度/设置/统计/登录态）导出为 JSON 文件
    func exportBackup() throws -> URL {
        let envelope = BackupEnvelope(
            format: "lightnovelreader-backup",
            version: 1,
            exportedAt: ISO8601DateFormatter().string(from: Date()),
            wenku8Cookie: wenku8Service?.savedCookie,
            state: makeSnapshot()
        )
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        let data = try encoder.encode(envelope)

        let stampFormatter = DateFormatter()
        stampFormatter.dateFormat = "yyyyMMdd-HHmm"
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("LightNovelReader-Backup-\(stampFormatter.string(from: Date())).json")
        try data.write(to: url, options: .atomic)
        return url
    }

    /// 从备份数据恢复：覆盖当前全部本地数据并重载书目
    func importBackup(_ data: Data) async throws {
        let envelope: BackupEnvelope
        do {
            envelope = try JSONDecoder().decode(BackupEnvelope.self, from: data)
        } catch {
            throw BackupError.invalidFile
        }
        guard envelope.format == "lightnovelreader-backup", envelope.version == 1 else {
            throw BackupError.invalidFile
        }
        let snapshot = envelope.state

        // 进度等状态在书目合并时以初始快照兜底，导入时同步替换并清空内存书架缓存
        initialSnapshot = snapshot
        sourceOverrideByID = snapshot.sourceByID
        books = []
        contentCache = [:]
        contentCacheOrder = []
        chaptersByBook = [:]

        theme = snapshot.theme
        source = snapshot.preferredSource
        savedIDs = snapshot.savedIDs
        searchHistory = snapshot.searchHistory
        readerPreferences = snapshot.readerPreferences
        shelves = snapshot.shelves
        selectedShelfID = snapshot.selectedShelfID
        dailyStats = snapshot.dailyStats
        bookReadingSeconds = snapshot.bookReadingSeconds
        chapterOffsetByID = snapshot.chapterOffsetByID
        knownTotalChaptersByID = snapshot.knownTotalChaptersByID
        readBookIDs = snapshot.readBookIDs
        lastReadAtByID = snapshot.lastReadAtByID

        services["文库8(在线)"]?.savedCookie = envelope.wenku8Cookie

        await reload()
    }

    // MARK: - 离线缓存

    func isDownloading(_ bookID: String) -> Bool {
        offlineTasks[bookID] != nil
    }

    func cachedChapterCount(for book: Book) async -> Int {
        await disk.cachedIndices(bookID: book.id, source: book.source).count
    }

    /// 缓存整本书（目录 + 全部正文）；已在缓存中的章节直接跳过
    func downloadBook(_ book: Book) async {
        if let existing = offlineTasks[book.id] {
            await existing.value
            return
        }
        let task = Task { await runDownload(book) }
        offlineTasks[book.id] = task
        await task.value
    }

    func cancelDownload(bookID: String) {
        offlineTasks[bookID]?.cancel()
    }

    private func runDownload(_ book: Book) async {
        defer {
            offlineTasks[book.id] = nil
            offlineProgress[book.id] = nil
        }
        guard let service = services[book.source] else { return }
        await loadChapters(for: book)
        guard let items = chaptersByBook[book.id], !items.isEmpty else { return }

        let total = items.count
        offlineProgress[book.id] = OfflineProgress(done: 0, total: total)

        // 第一轮：3 并发下载（上游实测的限流平衡点），失败章节收集后统一重试一轮
        var failedIndices: [Int] = []
        var downloaded = 0

        func downloadOne(_ index: Int) async -> Bool {
            if await ChapterDiskCache.shared.content(bookID: book.id, source: book.source, index: index) != nil {
                return true
            }
            guard let content = try? await service.fetchContent(for: book, chapter: index) else {
                return false
            }
            await disk.store(content, bookID: book.id, source: book.source)
            for (offset, urlString) in content.images.enumerated() {
                let name = Self.offlineImageName(
                    chapterIndex: content.index,
                    offset: offset,
                    urlString: urlString
                )
                if let url = URL(string: urlString),
                   let data = try? await URLSession.shared.data(from: url).0, !data.isEmpty {
                    await disk.storeImage(data, bookID: book.id, source: book.source, name: name)
                }
            }
            return true
        }

        await withTaskGroup(of: (Int, Bool).self) { group in
            var next = 0
            while next < total, next < 3 {
                let index = next
                next += 1
                group.addTask { (index, await downloadOne(index)) }
            }
            while let (index, ok) = await group.next() {
                if ok {
                    downloaded += 1
                } else {
                    failedIndices.append(index)
                }
                offlineProgress[book.id] = OfflineProgress(done: downloaded, total: total)
                if Task.isCancelled {
                    group.cancelAll()
                } else if next < total {
                    let index = next
                    next += 1
                    group.addTask { (index, await downloadOne(index)) }
                }
            }
        }

        // 第二轮：重试失败的章节（串行 + 稍等，提高成功率）
        for index in failedIndices where !Task.isCancelled {
            if await downloadOne(index) {
                downloaded += 1
            }
            offlineProgress[book.id] = OfflineProgress(done: downloaded, total: total)
        }

        // 最终计数：以磁盘实际落盘为准
        let finalCount = await disk.cachedIndices(bookID: book.id, source: book.source).count
        offlineBookCounts[book.id] = finalCount
    }

    func diskUsage() async -> Int64 {
        await disk.totalSizeBytes()
    }

    func clearDiskCache() async {
        for task in offlineTasks.values {
            task.cancel()
        }
        await disk.clearAll()
        offlineProgress = [:]
        offlineBookCounts = [:]
    }

    // MARK: - 导出

    /// 先确保全书已缓存，再把整本书导出为 EPUB / TXT，返回临时文件供分享
    func exportBook(_ book: Book, format: ExportFormat) async throws -> URL {
        await downloadBook(book)
        guard let items = chaptersByBook[book.id], !items.isEmpty else {
            throw ExportBookError.catalogNotReady
        }
        var chapters: [(title: String, paragraphs: [String])] = []
        for (index, item) in items.enumerated() {
            // 个别章节拿不到（如插图章节、临时失败）时跳过，保证整本书仍可导出
            guard let content = try? await chapterContent(for: book, index: index) else { continue }
            chapters.append((content.title.isEmpty ? item.title : content.title, content.paragraphs))
        }
        guard !chapters.isEmpty else { throw ExportBookError.catalogNotReady }
        let fileBase = book.title
            .replacingOccurrences(of: "/", with: "-")
            .replacingOccurrences(of: ":", with: "-")
        switch format {
        case .epub:
            let url = FileManager.default.temporaryDirectory.appendingPathComponent("\(fileBase).epub")
            // 纯计算（XHTML 拼接 + deflate 压缩 + CRC）放到后台线程，避免大书在主线程冻结 UI
            let coverData = await fetchCoverData(for: book)
            let title = book.title
            let author = book.author
            try await Task.detached(priority: .userInitiated) {
                try EpubExporter.writeEpub(
                    title: title,
                    author: author,
                    chapters: chapters,
                    coverData: coverData,
                    to: url
                )
            }.value
            return url
        case .txt:
            let url = FileManager.default.temporaryDirectory.appendingPathComponent("\(fileBase).txt")
            let title = book.title
            let author = book.author
            let source = book.source
            // 全书文本拼接 + 写盘都放后台，避免大书在主线程冻结 UI
            try await Task.detached(priority: .userInitiated) {
                var text = "\(title)\n作者：\(author)\n来源：\(source)\n"
                for chapter in chapters {
                    text += "\n\n\(chapter.title)\n\n"
                    text += chapter.paragraphs.joined(separator: "\n")
                }
                try text.data(using: .utf8)?.write(to: url, options: .atomic)
            }.value
            return url
        }
    }

    private func fetchCoverData(for book: Book) async -> Data? {
        guard let urlString = book.coverURL, let url = URL(string: urlString) else { return nil }
        return try? await URLSession.shared.data(from: url).0
    }

    // MARK: - 阅读统计

    static func dayKey(for date: Date) -> String {
        let calendar = Calendar(identifier: .gregorian)
        let c = calendar.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", c.year ?? 0, c.month ?? 0, c.day ?? 0)
    }

    func stat(for date: Date) -> DailyStat {
        dailyStats[Self.dayKey(for: date)] ?? DailyStat()
    }

    var todayStat: DailyStat {
        stat(for: Date())
    }

    func recordReadingTime(bookID: String, seconds: Int) {
        guard seconds > 0 else { return }
        dailyStats[Self.dayKey(for: Date()), default: DailyStat()].seconds += seconds
        bookReadingSeconds[bookID, default: 0] += seconds
    }

    func recordChapterRead(bookID: String) {
        dailyStats[Self.dayKey(for: Date()), default: DailyStat()].chapters += 1
    }

    // MARK: - 章内阅读位置（滚动模式恢复）

    func recordChapterOffset(bookID: String, chapter: Int, fraction: Double) {
        guard fraction > 0.02 else { return }
        chapterOffsetByID["\(bookID)#\(chapter)"] = min(fraction, 1)
    }

    func chapterOffset(bookID: String, chapter: Int) -> Double {
        chapterOffsetByID["\(bookID)#\(chapter)"] ?? 0
    }

    /// 最近 n 天（含今天）的统计序列，旧→新
    func statSeries(days: Int) -> [(date: Date, stat: DailyStat)] {
        let calendar = Calendar(identifier: .gregorian)
        return (0..<days).compactMap { offset in
            calendar.date(byAdding: .day, value: -(days - 1 - offset), to: Date()).map { ($0, stat(for: $0)) }
        }
    }

    /// 连续阅读天数（今天还没读则从昨天起算，保持当天断签前的显示）
    var readingStreakDays: Int {
        let calendar = Calendar(identifier: .gregorian)
        var date = Date()
        if stat(for: date).seconds == 0,
           let yesterday = calendar.date(byAdding: .day, value: -1, to: date) {
            date = yesterday
        }
        var streak = 0
        while stat(for: date).seconds > 0 {
            streak += 1
            guard let previous = calendar.date(byAdding: .day, value: -1, to: date) else { break }
            date = previous
        }
        return streak
    }
}
