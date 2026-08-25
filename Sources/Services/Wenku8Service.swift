import Foundation

/// wenku8 真实书源：解析 www.wenku8.net 的静态页面。
/// 书目由「探索 → 书库浏览」全站拉取；全站搜索采用上游 LightNovelReader 同款方案：
/// 站点 search.php 要求登录 cookie + 关键词 GB2312 编码 + 两次搜索间隔 ≥ 5 秒。
final class Wenku8Service: BookSourceService {
    let name = "文库8(在线)"

    /// 内置账号（免手动登录）。⚠️ 这是账号凭据，随 App 分发即对所有使用者可见；
    /// 请确认该账号可以共享，否则建议改为让用户自行登录。
    static let bundledUsername = "komorebiiluv"
    static let bundledPassword = "komorebi041016"

    private static let host = "https://www.wenku8.net"
    private static let coverHost = "http://img.wenku8.com"

    /// GBK → String.Encoding(GB18030 是 GBK 超集)
    private static let gbk: String.Encoding = {
        let cfEnc = CFStringEncodings.GB_18030_2000
        let nsEnc = CFStringConvertEncodingToNSStringEncoding(CFStringEncoding(cfEnc.rawValue))
        return String.Encoding(rawValue: nsEnc)
    }()

    private static let session: URLSession = {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 15
        config.httpAdditionalHeaders = [
            "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
        ]
        return URLSession(configuration: config)
    }()

    // 目录缓存：bookID → [(cid, 标题, 卷名)]，fetchContent 依赖它。
    // 标记 @MainActor：实例由 AppStore(@MainActor) 唯一持有并调用，消除 SE-0338
    // 隐式隔离依赖，保证多入口并发调用时对该字典的读写始终串行。
    @MainActor
    private var chapterIndexCache: [String: [(cid: Int, title: String, volume: String?)]] = [:]

    // MARK: - 登录态（jieqi CMS 会话 cookie）

    private static let cookieDefaultsKey = "wenku8.cookies"

    static var savedCookie: String? {
        get { UserDefaults.standard.string(forKey: cookieDefaultsKey) }
        set { UserDefaults.standard.set(newValue, forKey: cookieDefaultsKey) }
    }

    var isLoggedIn: Bool {
        Self.savedCookie?.contains("jieqiUserInfo") == true
    }

    var loggedInUsername: String? {
        guard let cookie = Self.savedCookie else { return nil }
        // HTTPCookieStorage 保存的值是整体 percent-encode 的，先解码再解析
        let decoded = cookie.removingPercentEncoding ?? cookie
        guard let range = decoded.range(of: "jieqiUserName=") else { return nil }
        let name = decoded[range.upperBound...].prefix { $0 != "," && $0 != ";" && !$0.isWhitespace }
        return name.isEmpty ? nil : String(name)
    }

    enum LoginError: LocalizedError {
        case failed(String)
        var errorDescription: String? {
            detail.isEmpty ? "登录失败，请检查用户名与密码" : detail
        }
        var detail: String {
            switch self {
            case .failed(let detail): return detail
            }
        }
    }

    /// 登录 wenku8（jieqi CMS 表单）。注意：站点用注册时的「用户名」登录，邮箱通常查不到账号。
    func login(username: String, password: String) async throws {
        guard let url = URL(string: Self.host + "/login.php?do=submit") else {
            throw BookSourceError.unreachable
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.setValue(Self.host + "/login.php", forHTTPHeaderField: "Referer")
        request.httpBody = Data(
            ("username=\(Self.formEncode(username))"
                + "&password=\(Self.formEncode(password))"
                + "&action=login&usecookie=31536000"
                + "&submit=%E7%99%BB+%E5%BD%95"
                + "&jumpurl=\(Self.formEncode(Self.host + "/index.php"))").utf8
        )

        let (data, response) = try await Self.session.data(for: request)
        guard let http = response as? HTTPURLResponse, http.statusCode == 200 else {
            throw BookSourceError.unreachable
        }

        // 从会话 cookie 存储提取 jieqi 会话
        let cookies = Self.session.configuration.httpCookieStorage?.cookies ?? []
        let jieqiCookies = cookies.filter { $0.name.hasPrefix("jieqi") }
        guard jieqiCookies.contains(where: { $0.name == "jieqiUserInfo" }) else {
            let html = String(data: data, encoding: Self.gbk) ?? ""
            if let message = Self.loginErrorMessage(in: html) {
                throw LoginError.failed(message)
            }
            throw LoginError.failed("")
        }
        Self.savedCookie = jieqiCookies
            .sorted { $0.name < $1.name }
            .map { "\($0.name)=\($0.value)" }
            .joined(separator: "; ")
    }

    func logout() {
        Self.savedCookie = nil
        if let storage = Self.session.configuration.httpCookieStorage {
            for cookie in storage.cookies ?? [] where cookie.name.hasPrefix("jieqi") {
                storage.deleteCookie(cookie)
            }
        }
    }

    /// 登录会话 cookie（协议要求，转发到 static 存储）
    var savedCookie: String? {
        get { Self.savedCookie }
        set { Self.savedCookie = newValue }
    }

    private static func loginErrorMessage(in html: String) -> String? {
        let plain = html
            .replacingOccurrences(of: "<[^>]+>", with: " ", options: .regularExpression)
        guard let range = plain.range(of: "错误原因：") else {
            if plain.contains("该用户不存在") { return "该用户不存在" }
            if plain.contains("密码错误") { return "密码错误" }
            return nil
        }
        let message = plain[range.upperBound...]
            .prefix(60)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return message.isEmpty ? nil : String(message)
    }

    // MARK: - BookSourceService

    var searchNotice: String? {
        isLoggedIn ? nil : "未登录：无法搜索，请登录 wenku8 账号"
    }

    func fetchBooks() async throws -> [Book] {
        // 文库8 不再内置精选书单：书目由「探索 → 书库浏览」全站拉取，
        // 用户接触过的书由 AppStore 持久化维护（books 数组），这里返回空即可。
        return []
    }

    func fetchChapters(for book: Book) async throws -> [ChapterItem] {
        let aid = try Self.aid(of: book)
        let index = try await chapterIndex(for: book, aid: aid)
        return index.enumerated().map { offset, entry in
            ChapterItem(index: offset, title: entry.title, volume: entry.volume, remoteID: entry.cid)
        }
    }

    func fetchContent(for book: Book, chapter index: Int) async throws -> ChapterContent {
        let aid = try Self.aid(of: book)
        let entries = try await chapterIndex(for: book, aid: aid)
        guard index >= 0, index < entries.count else { throw BookSourceError.unreachable }
        let entry = entries[index]

        let html = try await getWithRetry("/novel/\(aid / 1000)/\(aid)/\(entry.cid).htm")
        let paragraphs = Self.paragraphs(fromContentHTML: html)
        let images = Self.imageURLs(fromContentHTML: html)
        // 版权下架的书正文页只剩一句占位提示
        if paragraphs.count <= 2,
           paragraphs.contains(where: { $0.contains("因版权问题") || $0.contains("不再提供") }) {
            throw BookSourceError.contentUnavailable(title: book.title)
        }
        if paragraphs.isEmpty && images.isEmpty {
            throw BookSourceError.unreachable
        }
        return ChapterContent(index: index, title: entry.title, paragraphs: paragraphs, images: images)
    }

    // MARK: - 书库浏览（栏目列表 + 分页，需登录态）

    var exploreCategories: [ExploreCategory] {
        [
            ExploreCategory(id: "all", title: "轻小说列表", path: "/modules/article/articlelist.php", extraParams: ""),
            ExploreCategory(id: "allvisit", title: "热门轻小说", path: "/modules/article/toplist.php", extraParams: "&sort=allvisit"),
            ExploreCategory(id: "anime", title: "动画化作品", path: "/modules/article/toplist.php", extraParams: "&sort=anime"),
            ExploreCategory(id: "lastupdate", title: "今日更新", path: "/modules/article/toplist.php", extraParams: "&sort=lastupdate"),
            ExploreCategory(id: "postdate", title: "新书一览", path: "/modules/article/toplist.php", extraParams: "&sort=postdate"),
            ExploreCategory(id: "completed", title: "完结全本", path: "/modules/article/articlelist.php", extraParams: "&fullflag=1"),
        ]
    }

    /// 标签 → 对应的书库浏览栏目
    func tagCategory(_ tag: String) -> ExploreCategory {
        let encoded = Self.gb2312PercentEncoded(tag) ?? tag
        return ExploreCategory(
            id: "tag-\(tag)",
            title: tag,
            path: "/modules/article/tags.php",
            extraParams: "&t=\(encoded)",
            supportsSort: true
        )
    }

    /// 书库浏览列表的某一页（每页 20 本）；站点要求登录态，网络层带重试
    func fetchExplorePage(_ category: ExploreCategory, page: Int, sortSuffix: String = "") async throws -> (books: [Book], totalPages: Int) {
        guard isLoggedIn else { throw BookSourceError.loginRequired }
        let html = try await getWithRetry(category.url(page: page, sortSuffix: sortSuffix))
        var totalPages = 1
        if let raw = Self.firstCapture(pattern: "pagestats[^>]*>\\s*\\d+/(\\d+)", in: html),
           let parsed = Int(raw) {
            totalPages = parsed
        }
        return (parseSearchResultCards(html), totalPages)
    }

    /// 带登录态 + 指数退避重试的页面请求（探索/榜单等动态页在真机上偶发超时）
    private func getWithRetry(_ path: String, attempts: Int = 3) async throws -> String {
        guard let url = URL(string: Self.host + path) else { throw BookSourceError.unreachable }
        var lastError: Error = BookSourceError.unreachable
        for attempt in 0..<attempts {
            do {
                var request = URLRequest(url: url)
                request.setValue(Self.savedCookie, forHTTPHeaderField: "Cookie")
                request.setValue("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8", forHTTPHeaderField: "Accept")
                request.setValue("zh-CN,zh;q=0.9", forHTTPHeaderField: "Accept-Language")
                let (data, response) = try await Self.session.data(for: request)
                guard let http = response as? HTTPURLResponse, http.statusCode == 200 else {
                    throw BookSourceError.unreachable
                }
                if let finalURL = response.url, finalURL.path.contains("login.php") {
                    Self.savedCookie = nil
                    throw BookSourceError.loginRequired
                }
                return String(data: data, encoding: Self.gbk) ?? String(decoding: data, as: UTF8.self)
            } catch {
                lastError = error
                if attempt < attempts - 1 {
                    try? await Task.sleep(nanoseconds: UInt64(attempt + 1) * 500_000_000)
                }
            }
        }
        throw lastError
    }

    func search(_ term: String) async throws -> [Book] {
        // 站点搜索要求登录态；未登录时直接提示（已无精选书单可降级筛选）
        guard isLoggedIn else {
            throw BookSourceError.loginRequired
        }
        return try await siteSearch(term)
    }

    /// 全站搜索：search.php + 登录 cookie + GB2312 编码关键词，遵守站点 5 秒间隔限制
    private func siteSearch(_ term: String) async throws -> [Book] {
        guard let encoded = Self.gb2312PercentEncoded(term),
              let url = URL(string: Self.host + "/modules/article/search.php?searchtype=articlename&searchkey=\(encoded)&page=1") else {
            throw BookSourceError.unreachable
        }

        for attempt in 0..<2 {
            var request = URLRequest(url: url)
            request.setValue(Self.savedCookie, forHTTPHeaderField: "Cookie")
            request.setValue("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8", forHTTPHeaderField: "Accept")
            request.setValue("zh-CN,zh;q=0.9", forHTTPHeaderField: "Accept-Language")

            let (data, response) = try await Self.session.data(for: request)
            guard let http = response as? HTTPURLResponse, http.statusCode == 200 else {
                throw BookSourceError.unreachable
            }
            // 被重定向到登录页 = 登录态已失效
            if let finalURL = response.url, finalURL.path.contains("login.php") {
                Self.savedCookie = nil
                throw BookSourceError.loginRequired
            }
            let html = String(data: data, encoding: Self.gbk) ?? String(decoding: data, as: UTF8.self)
            if html.contains("间隔时间不得少于") {
                if attempt == 0 {
                    try await Task.sleep(nanoseconds: 5_200_000_000)
                    continue
                }
                throw BookSourceError.searchThrottled
            }
            // 精确命中单书：站点直接 302 到书籍详情页
            if let finalURL = response.url,
               finalURL.path.hasPrefix("/book/"),
               let aidText = finalURL.lastPathComponent.split(separator: ".").first,
               let aid = Int(aidText) {
                return [try await fetchBookDetail(aid: aid)]
            }
            // 或跳到含「小说目录」链接的中间页
            if html.contains("小说目录"),
               let aidText = Self.firstCapture(pattern: "href=\"(?:https?://[^\"]*)?/book/(\\d+)\\.htm\"[^>]*>[^<]*小说目录", in: html),
               let aid = Int(aidText) {
                return [try await fetchBookDetail(aid: aid)]
            }
            return parseSearchResultCards(html)
        }
        throw BookSourceError.searchThrottled
    }

    /// 解析搜索结果卡片（每页 20 个）：<b><a .../book/{aid}.htm" title="{书名}">…作者/Tags/简介
    private func parseSearchResultCards(_ html: String) -> [Book] {
        var books: [Book] = []
        var seen = Set<String>()
        guard let regex = try? NSRegularExpression(pattern: "<b><a[^>]*href=\"/book/(\\d+)\\.htm\"[^>]*title=\"([^\"]*)\"") else {
            return books
        }
        for match in regex.matches(in: html, range: NSRange(html.startIndex..., in: html)) {
            guard match.numberOfRanges > 2,
                  let fullRange = Range(match.range, in: html),
                  let aidRange = Range(match.range(at: 1), in: html),
                  let titleRange = Range(match.range(at: 2), in: html),
                  let aid = Int(html[aidRange]) else { continue }
            let id = "wk8-\(aid)"
            guard !seen.contains(id) else { continue }
            seen.insert(id)

            let title = String(html[titleRange])
            // 卡片后续 1200 字符里有作者/文库/更新状态/字数/Tags/简介
            let cardTail = String(html[fullRange.upperBound...].prefix(1200))
            let author = Self.firstCapture(pattern: "作者[：:]([^/<]{1,40})/", in: cardTail) ?? "未知作者"
            let house = Self.firstCapture(pattern: "分类[：:]([^/<]{1,20})", in: cardTail)?
                .trimmingCharacters(in: .whitespaces)
            let isCompleted: Bool?
            switch Self.firstCapture(pattern: "(连载中|已完结)", in: cardTail) {
            case "已完结": isCompleted = true
            case "连载中": isCompleted = false
            default: isCompleted = nil
            }
            let wordCountK = Self.firstCapture(pattern: "字数[：:](\\d+)K", in: cardTail).flatMap(Int.init)
            let tagsRaw = Self.firstCapture(pattern: "Tags:(?:</?span[^>]*>)*([^<]{1,100})", in: cardTail) ?? ""
            let tags = tagsRaw.split(separator: " ").map(String.init)
            let intro = Self.firstCapture(pattern: "简介[：:]?\\s*(?:</?span[^>]*>)*([^<]{1,160})", in: cardTail) ?? "暂无简介"

            books.append(Book(
                id: id,
                title: title,
                author: author.trimmingCharacters(in: .whitespaces),
                tags: tags.isEmpty ? ["文库8"] : tags,
                source: name,
                totalChapters: 0,
                lastChapter: 0,
                hasUpdate: false,
                hits: 0,
                intro: intro,
                coverIndex: aid % 9,
                coverURL: "\(Self.coverHost)/image/\(aid / 1000)/\(aid)/\(aid)s.jpg",
                publishingHouse: (house?.isEmpty ?? true) ? nil : house,
                isCompleted: isCompleted,
                wordCountK: wordCountK
            ))
        }
        return books
    }

    // MARK: - 页面解析

    func fetchBookDetail(aid: Int) async throws -> Book {
        let html = try await get("/book/\(aid).htm")

        // <title>书名 - 作者 - 分类(文库) - 轻小说文库</title>
        var title = "未知书名"
        var author = "未知作者"
        var tags: [String] = []
        var publishingHouse: String? = nil
        if let raw = Self.firstCapture(pattern: "<title>([^<]+)</title>", in: html) {
            let parts = raw
                .replacingOccurrences(of: "轻小说文库", with: "")
                .components(separatedBy: " - ")
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty }
            if parts.count >= 1 { title = parts[0] }
            if parts.count >= 2 { author = parts[1] }
            if parts.count >= 3 {
                tags = [parts[2]]
                publishingHouse = parts[2]
            }
        }

        // 最后更新：yyyy-MM-dd
        let lastUpdate = Self.firstCapture(pattern: "最后更新[：:](\\d{4}-\\d{2}-\\d{2})", in: html)
        // 全文长度：xxxxxxx字
        let wordCount = Self.firstCapture(pattern: "全文长度[：:](\\d+)字", in: html).flatMap(Int.init)
        let wordCountK = wordCount.map { $0 / 1000 }

        var intro = "暂无简介"
        // 简介在「内容简介：</span><br /><span style="...">…</span><br />」里，正文可能含 <br />
        // 先定位「内容简介」，再截到该行结尾 </td>，剥掉标签取纯文本
        if let start = html.range(of: "内容简介") {
            let tail = html[start.lowerBound...]
            if let end = tail.range(of: "</td>") {
                let block = String(tail[..<end.lowerBound])
                let plain = block
                    .replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)
                    .replacingOccurrences(of: "&nbsp;", with: " ")
                    .replacingOccurrences(of: "&amp;", with: "&")
                    .replacingOccurrences(of: "&lt;", with: "<")
                    .replacingOccurrences(of: "&gt;", with: ">")
                    .replacingOccurrences(of: "内容简介：", with: "")
                    .replacingOccurrences(of: "内容简介:", with: "")
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                if !plain.isEmpty {
                    intro = plain
                }
            }
        }

        return Book(
            id: "wk8-\(aid)",
            title: title,
            author: author,
            tags: tags.isEmpty ? ["文库8"] : tags,
            source: name,
            totalChapters: 0,   // 目录未加载，详情页打开后由 store 回填
            lastChapter: 0,
            hasUpdate: false,
            hits: 0,            // 站点未提供热度数据
            intro: intro,
            coverIndex: aid % 9,
            coverURL: "\(Self.coverHost)/image/\(aid / 1000)/\(aid)/\(aid)s.jpg",
            publishingHouse: publishingHouse,
            isCompleted: nil,
            wordCountK: wordCountK,
            lastUpdate: lastUpdate
        )
    }

    @MainActor
    private func chapterIndex(for book: Book, aid: Int) async throws -> [(cid: Int, title: String, volume: String?)] {
        if let cached = chapterIndexCache[book.id] {
            return cached
        }
        let html = try await get("/novel/\(aid / 1000)/\(aid)/index.htm")
        let entries = Self.parseChapterIndex(html)
        guard !entries.isEmpty else { throw BookSourceError.unreachable }
        chapterIndexCache[book.id] = entries
        return entries
    }

    /// 按文档顺序扫描卷标题(vcss)与章节链接(ccss)
    static func parseChapterIndex(_ html: String) -> [(cid: Int, title: String, volume: String?)] {
        guard let volumeRegex = try? NSRegularExpression(pattern: "<td[^>]*class=\"vcss\"[^>]*>([^<]+)</td>"),
              let chapterRegex = try? NSRegularExpression(pattern: "<td[^>]*class=\"ccss\"[^>]*>\\s*<a[^>]*href=\"(\\d+)\\.htm\"[^>]*>([^<]*)</a>")
        else { return [] }

        typealias RawEntry = (location: Int, cid: Int, title: String, volume: String?)
        var raws: [RawEntry] = []
        let ns = html as NSString

        var volumes: [(location: Int, name: String)] = []
        volumeRegex.enumerateMatches(in: html, range: NSRange(location: 0, length: ns.length)) { match, _, _ in
            guard let match, match.numberOfRanges > 1 else { return }
            let text = ns.substring(with: match.range(at: 1))
            volumes.append((match.range.location, text.trimmingCharacters(in: .whitespacesAndNewlines)))
        }

        chapterRegex.enumerateMatches(in: html, range: NSRange(location: 0, length: ns.length)) { match, _, _ in
            guard let match, match.numberOfRanges > 2,
                  let cid = Int(ns.substring(with: match.range(at: 1)))
            else { return }
            let title = ns.substring(with: match.range(at: 2)).trimmingCharacters(in: .whitespacesAndNewlines)
            let volume = volumes.last(where: { $0.location < match.range.location })?.name
            raws.append((match.range.location, cid, title.isEmpty ? "第 \(cid) 章" : title, volume))
        }

        raws.sort { $0.location < $1.location }
        return raws.map { (cid: $0.cid, title: $0.title, volume: $0.volume) }
    }

    /// 正文：取 <div id="content"> 到下一个 </div>，按 <br /> 分段并清洗
    /// 提取正文区的插图地址（ wenku8 的插图走 https 图床；相对路径补全为主站域名 ）
    static func imageURLs(fromContentHTML html: String) -> [String] {
        guard let start = html.range(of: "<div id=\"content\">"),
              let end = html.range(of: "</div>", range: start.upperBound..<html.endIndex) else {
            return []
        }
        let body = String(html[start.upperBound..<end.lowerBound])
        guard let regex = try? NSRegularExpression(pattern: "<img[^>]+src=\"([^\"]+)\"") else { return [] }
        return regex.matches(in: body, range: NSRange(body.startIndex..., in: body)).compactMap { match in
            guard let range = Range(match.range(at: 1), in: body) else { return nil }
            let src = String(body[range])
            if src.hasPrefix("http://") || src.hasPrefix("https://") { return src }
            return Self.host + (src.hasPrefix("/") ? src : "/" + src)
        }
    }

    static func paragraphs(fromContentHTML html: String) -> [String] {
        guard let start = html.range(of: "<div id=\"content\">") else { return [] }
        let after = html[start.upperBound...]
        let body = after.prefix(while: { $0 != "\0" })
        // 找收尾 </div>
        var raw = String(body)
        if let end = raw.range(of: "</div>") {
            raw = String(raw[..<end.lowerBound])
        }

        raw = raw
            .replacingOccurrences(of: "<ul id=\"contentdp\">[\\s\\S]*?</ul>", with: "\n", options: .regularExpression)
            .replacingOccurrences(of: "<br\\s*/?>", with: "\n", options: .regularExpression)
            .replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)

        for (entity, replacement) in [("&nbsp;", ""), ("&amp;", "&"), ("&lt;", "<"), ("&gt;", ">"), ("&quot;", "\""), ("&#39;", "'")] {
            raw = raw.replacingOccurrences(of: entity, with: replacement)
        }

        return raw
            .components(separatedBy: "\n")
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty && !$0.contains("轻小说文库") }
    }

    // MARK: - 工具

    private static func firstCapture(pattern: String, in text: String) -> String? {
        guard let regex = try? NSRegularExpression(pattern: pattern),
              let match = regex.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)),
              match.numberOfRanges > 1,
              let range = Range(match.range(at: 1), in: text)
        else { return nil }
        return String(text[range])
    }

    private static func aid(of book: Book) throws -> Int {
        guard book.id.hasPrefix("wk8-"), let aid = Int(book.id.dropFirst(4)) else {
            throw BookSourceError.unreachable
        }
        return aid
    }

    private func get(_ path: String) async throws -> String {
        guard let url = URL(string: Self.host + path) else { throw BookSourceError.unreachable }
        let (data, response) = try await Self.session.data(from: url)
        guard let http = response as? HTTPURLResponse, http.statusCode == 200 else {
            throw BookSourceError.unreachable
        }
        return String(data: data, encoding: Self.gbk) ?? String(decoding: data, as: UTF8.self)
    }

    // MARK: - 探索首页与标签（需登录态）

    /// wenku8 首页的推荐块（新书风云榜 / 本周会员推荐榜 / 最近更新轻小说）
    func fetchHomeBlocks() async throws -> [HomeExploreBlock] {
        guard isLoggedIn else { throw BookSourceError.loginRequired }
        guard let url = URL(string: Self.host + "/index.php") else { throw BookSourceError.unreachable }
        var request = URLRequest(url: url)
        request.setValue(Self.savedCookie, forHTTPHeaderField: "Cookie")
        let (data, response) = try await Self.session.data(for: request)
        guard let http = response as? HTTPURLResponse, http.statusCode == 200 else {
            throw BookSourceError.unreachable
        }
        let html = String(data: data, encoding: Self.gbk) ?? String(decoding: data, as: UTF8.self)

        var blocks: [HomeExploreBlock] = []
        let segments = html.components(separatedBy: "<div class=\"blocktitle\">")
        for segment in segments.dropFirst() {
            guard let titleEnd = segment.range(of: "</div>") else { continue }
            // 标题里可能混入 HTML（如推广链接），只保留纯文本
            let title = String(segment[..<titleEnd.lowerBound])
                .replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)
                .trimmingCharacters(in: .whitespacesAndNewlines)
            guard !title.isEmpty, !title.contains("公告"), !title.contains("推广") else { continue }

            // 每项：<a href="/book/{aid}.htm" title="{书名}"><img src="{封面}">
            guard let regex = try? NSRegularExpression(pattern: "<a href=\"/book/(\\d+)\\.htm\"[^>]*title=\"([^\"]*)\"[^>]*>\\s*<img src=\"([^\"]+)\"") else { continue }
            var books: [Book] = []
            for match in regex.matches(in: segment, range: NSRange(segment.startIndex..., in: segment)) {
                guard match.numberOfRanges > 3,
                      let aidRange = Range(match.range(at: 1), in: segment),
                      let titleRange = Range(match.range(at: 2), in: segment),
                      let coverRange = Range(match.range(at: 3), in: segment),
                      let aid = Int(segment[aidRange]) else { continue }
                books.append(Book(
                    id: "wk8-\(aid)",
                    title: String(segment[titleRange]),
                    author: "",
                    tags: ["文库8"],
                    source: name,
                    totalChapters: 0,
                    lastChapter: 0,
                    hasUpdate: false,
                    hits: 0,
                    intro: "",
                    coverIndex: aid % 9,
                    coverURL: String(segment[coverRange])
                ))
            }
            guard !books.isEmpty else { continue }
            blocks.append(HomeExploreBlock(title: title, books: Array(books.prefix(10))))
            if blocks.count >= 3 { break }
        }
        guard !blocks.isEmpty else { throw BookSourceError.unreachable }
        return blocks
    }

    /// 全站标签列表：与上游一致，内置 49 个标签
    /// （tags.php 首页是说明页不含标签链接，上游也是硬编码标签名后拼 tags.php?t= 请求）
    static let wenku8TagList = [
        "校园", "青春", "恋爱", "治愈", "群像", "竞技", "音乐", "美食", "旅行", "欢乐向",
        "经营", "职场", "斗智", "脑洞", "宅文化", "穿越", "奇幻", "魔法", "异能", "战斗",
        "科幻", "机战", "战争", "冒险", "龙傲天", "悬疑", "犯罪", "复仇", "黑暗", "猎奇",
        "惊悚", "间谍", "末日", "游戏", "大逃杀", "青梅竹马", "妹妹", "女儿", "JK", "JC",
        "大小姐", "性转", "伪娘", "人外", "后宫", "百合", "耽美", "NTR", "女性视角",
    ]

    func fetchTagList() async throws -> [String] {
        guard isLoggedIn else { throw BookSourceError.loginRequired }
        return Self.wenku8TagList
    }

    // MARK: 编码工具

    /// 表单值 URL 编码
    private static func formEncode(_ text: String) -> String {
        text.addingPercentEncoding(withAllowedCharacters: .alphanumerics) ?? text
    }

    /// wenku8 搜索要求关键词以 GB2312 字节序列做 URL 编码
    private static func gb2312PercentEncoded(_ text: String) -> String? {
        guard let bytes = text.data(using: gbk) else { return nil }
        return bytes.map { String(format: "%%%02X", $0) }.joined()
    }
}
