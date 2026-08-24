import Foundation

struct Book: Identifiable, Hashable, Codable {
    /// 稳定 id，重启后本地进度才能对应回书籍
    let id: String
    let title: String
    let author: String
    let tags: [String]
    var source: String
    var totalChapters: Int
    var lastChapter: Int
    var hasUpdate: Bool
    let hits: Int
    let intro: String
    let coverIndex: Int
    /// 真实书源的封面地址；模拟数据为 nil，用渐变占位
    var coverURL: String? = nil
    // 列表页解析出的元数据（探索页筛选用）
    /// 所属文库（如“电击文库”）
    var publishingHouse: String? = nil
    /// 是否完结（nil = 未知）
    var isCompleted: Bool? = nil
    /// 字数，单位千字
    var wordCountK: Int? = nil
    /// 最后更新时间（wenku8 书页的“最后更新：yyyy-MM-dd”）
    var lastUpdate: String? = nil

    var progress: Double {
        guard totalChapters > 0 else { return 0 }
        return min(Double(lastChapter) / Double(totalChapters), 1)
    }

    var hasReadLatest: Bool {
        lastChapter >= totalChapters - 1
    }

    static func chapterTitle(_ index: Int) -> String {
        "第 \(index + 1) 章 \(chapterNames[index % chapterNames.count])"
    }

    enum CodingKeys: String, CodingKey {
        case id, title, author, tags, source, totalChapters, lastChapter
        case hasUpdate, hits, intro, coverIndex, coverURL
        case publishingHouse, isCompleted, wordCountK, lastUpdate
    }

    init(
        id: String, title: String, author: String, tags: [String],
        source: String, totalChapters: Int, lastChapter: Int, hasUpdate: Bool,
        hits: Int, intro: String, coverIndex: Int, coverURL: String? = nil,
        publishingHouse: String? = nil, isCompleted: Bool? = nil, wordCountK: Int? = nil,
        lastUpdate: String? = nil
    ) {
        self.id = id
        self.title = title
        self.author = author
        self.tags = tags
        self.source = source
        self.totalChapters = totalChapters
        self.lastChapter = lastChapter
        self.hasUpdate = hasUpdate
        self.hits = hits
        self.intro = intro
        self.coverIndex = coverIndex
        self.coverURL = coverURL
        self.publishingHouse = publishingHouse
        self.isCompleted = isCompleted
        self.wordCountK = wordCountK
        self.lastUpdate = lastUpdate
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        title = try c.decode(String.self, forKey: .title)
        author = try c.decode(String.self, forKey: .author)
        tags = try c.decode([String].self, forKey: .tags)
        source = try c.decode(String.self, forKey: .source)
        totalChapters = try c.decode(Int.self, forKey: .totalChapters)
        lastChapter = try c.decode(Int.self, forKey: .lastChapter)
        hasUpdate = try c.decode(Bool.self, forKey: .hasUpdate)
        hits = try c.decode(Int.self, forKey: .hits)
        intro = try c.decode(String.self, forKey: .intro)
        coverIndex = try c.decode(Int.self, forKey: .coverIndex)
        // 旧缓存/旧书单数据没有以下字段
        coverURL = try c.decodeIfPresent(String.self, forKey: .coverURL)
        publishingHouse = try c.decodeIfPresent(String.self, forKey: .publishingHouse)
        isCompleted = try c.decodeIfPresent(Bool.self, forKey: .isCompleted)
        wordCountK = try c.decodeIfPresent(Int.self, forKey: .wordCountK)
        lastUpdate = try c.decodeIfPresent(String.self, forKey: .lastUpdate)
    }
}

struct ReaderConfig: Identifiable {
    let id = UUID()
    let book: Book
    let chapter: Int
}
