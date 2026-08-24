import Foundation

/// 章节正文、目录与书目的磁盘缓存（离线阅读）。
/// 目录结构：<root>/<source>/<bookID>/{catalog.json, chapters/<index>.json}
actor ChapterDiskCache {
    static let shared = ChapterDiskCache()

    private let root: URL

    init(root: URL? = nil) {
        if let root {
            self.root = root
        } else {
            let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
                .appendingPathComponent("LNROffline", isDirectory: true)
            try? FileManager.default.createDirectory(at: base, withIntermediateDirectories: true)
            self.root = base
        }
    }

    // MARK: - 正文

    func content(bookID: String, source: String, index: Int) -> ChapterContent? {
        let url = bookDir(bookID: bookID, source: source)
            .appendingPathComponent("chapters/\(index).json")
        guard let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode(ChapterContent.self, from: data)
    }

    func store(_ content: ChapterContent, bookID: String, source: String) {
        let dir = bookDir(bookID: bookID, source: source).appendingPathComponent("chapters", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        if let data = try? JSONEncoder().encode(content) {
            try? data.write(to: dir.appendingPathComponent("\(content.index).json"), options: .atomic)
        }
    }

    // MARK: - 目录

    func chapters(bookID: String, source: String) -> [ChapterItem]? {
        let url = bookDir(bookID: bookID, source: source).appendingPathComponent("catalog.json")
        guard let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode([ChapterItem].self, from: data)
    }

    func storeChapters(_ items: [ChapterItem], bookID: String, source: String) {
        let dir = bookDir(bookID: bookID, source: source)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        if let data = try? JSONEncoder().encode(items) {
            try? data.write(to: dir.appendingPathComponent("catalog.json"), options: .atomic)
        }
    }

    // MARK: - 插图缓存

    func storeImage(_ data: Data, bookID: String, source: String, name: String) {
        let dir = bookDir(bookID: bookID, source: source).appendingPathComponent("images", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        try? data.write(to: dir.appendingPathComponent(safe(name)), options: .atomic)
    }

    /// 已缓存的插图文件地址（离线阅读用）
    func imageURL(bookID: String, source: String, name: String) -> URL? {
        let url = bookDir(bookID: bookID, source: source)
            .appendingPathComponent("images/\(safe(name))")
        return FileManager.default.fileExists(atPath: url.path) ? url : nil
    }

    // MARK: - 书目（整源离线兜底）

    func storeCatalog(_ books: [Book], source: String) {
        let dir = root.appendingPathComponent(safe(source), isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        if let data = try? JSONEncoder().encode(books) {
            try? data.write(to: dir.appendingPathComponent("books.json"), options: .atomic)
        }
    }

    func catalog(source: String) -> [Book]? {
        let url = root.appendingPathComponent(safe(source), isDirectory: true).appendingPathComponent("books.json")
        guard let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode([Book].self, from: data)
    }

    // MARK: - 统计与清理

    func cachedIndices(bookID: String, source: String) -> Set<Int> {
        let dir = bookDir(bookID: bookID, source: source).appendingPathComponent("chapters", isDirectory: true)
        let files = (try? FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil)) ?? []
        return Set(files.compactMap { Int($0.deletingPathExtension().lastPathComponent) })
    }

    func totalSizeBytes() -> Int64 {
        guard let enumerator = FileManager.default.enumerator(at: root, includingPropertiesForKeys: [.fileSizeKey]) else {
            return 0
        }
        var total: Int64 = 0
        for case let file as URL in enumerator {
            if let size = (try? file.resourceValues(forKeys: [.fileSizeKey]))?.fileSize {
                total += Int64(size)
            }
        }
        return total
    }

    func clearAll() {
        let entries = (try? FileManager.default.contentsOfDirectory(at: root, includingPropertiesForKeys: nil)) ?? []
        for url in entries {
            try? FileManager.default.removeItem(at: url)
        }
    }

    private func bookDir(bookID: String, source: String) -> URL {
        root.appendingPathComponent("\(safe(source))/\(safe(bookID))", isDirectory: true)
    }

    /// 替换文件系统不安全的字符
    private func safe(_ name: String) -> String {
        String(name.map { character in
            character.isLetter || character.isNumber || character == "-" || character == "_" ? character : "_"
        })
    }
}
