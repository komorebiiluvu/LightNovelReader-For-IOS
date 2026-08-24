import Foundation

/// 封面图片缓存：内存 LRU + 磁盘文件，下载带重试与并发去重。
/// 用 NSLock 保护，但所有锁操作都收口在同步方法内（Swift 5.7 模式下无并发警告）。
/// 支持同步内存命中——封面视图首帧即可直接拿到已缓存图片，避免闪动。
final class CoverImageCache {
    static let shared = CoverImageCache()

    private let lock = NSLock()
    private var memory: [String: Data] = [:]
    private var memoryOrder: [String] = []
    private let maxMemory = 120
    private var inflight: [String: Task<Data?, Never>] = [:]
    private let root: URL

    init(root: URL? = nil) {
        if let root {
            self.root = root
        } else {
            let base = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
                .appendingPathComponent("LNRCovers", isDirectory: true)
            try? FileManager.default.createDirectory(at: base, withIntermediateDirectories: true)
            self.root = base
        }
    }

    // MARK: - 同步内存读写（收口锁操作）

    /// 同步检查内存缓存（主线程首帧使用，避免闪动）
    func memoryData(_ urlString: String) -> Data? {
        lock.lock(); defer { lock.unlock() }
        return memory[urlString]
    }

    private func insertMemory(_ key: String, _ data: Data) {
        lock.lock(); defer { lock.unlock() }
        memory[key] = data
        memoryOrder.append(key)
        while memoryOrder.count > maxMemory {
            let oldest = memoryOrder.removeFirst()
            memory[oldest] = nil
        }
    }

    private func readInflight(_ key: String) -> Task<Data?, Never>? {
        lock.lock(); defer { lock.unlock() }
        return inflight[key]
    }

    private func writeInflight(_ key: String, _ task: Task<Data?, Never>?) {
        lock.lock(); defer { lock.unlock() }
        inflight[key] = task
    }

    // MARK: - 异步加载

    func data(for urlString: String) async -> Data? {
        guard !urlString.isEmpty else { return nil }
        if let cached = memoryData(urlString) { return cached }
        if let existing = readInflight(urlString) { return await existing.value }
        let task = Task<Data?, Never> { await load(urlString) }
        writeInflight(urlString, task)
        let result = await task.value
        writeInflight(urlString, nil)
        return result
    }

    func prewarm(_ urlStrings: [String]) {
        for url in urlStrings {
            Task { _ = await data(for: url) }
        }
    }

    /// 同步清理旧缓存（主线程调用也无碍，只做文件属性遍历）
    func pruneOlderThan(days: Int) {
        let cutoff = Date().addingTimeInterval(-Double(days) * 86_400)
        guard let files = try? FileManager.default.contentsOfDirectory(at: root, includingPropertiesForKeys: [.contentModificationDateKey]) else { return }
        for file in files {
            if let mod = (try? file.resourceValues(forKeys: [.contentModificationDateKey]))?.contentModificationDate,
               mod < cutoff {
                try? FileManager.default.removeItem(at: file)
            }
        }
    }

    private func load(_ urlString: String) async -> Data? {
        let fileURL = root.appendingPathComponent(hash(urlString))
        if let cached = try? Data(contentsOf: fileURL), !cached.isEmpty {
            insertMemory(urlString, cached)
            return cached
        }
        guard let url = URL(string: urlString) else { return nil }
        for attempt in 0..<3 {
            do {
                let (data, response) = try await URLSession.shared.data(from: url)
                if let http = response as? HTTPURLResponse, http.statusCode == 200, !data.isEmpty {
                    try? data.write(to: fileURL, options: .atomic)
                    insertMemory(urlString, data)
                    return data
                }
            } catch {
                // 继续重试
            }
            if attempt < 2 {
                try? await Task.sleep(nanoseconds: UInt64(attempt + 1) * 400_000_000)
            }
        }
        return nil
    }

    private func hash(_ text: String) -> String {
        var hash: UInt64 = 5_381
        for byte in text.utf8 { hash = hash &* 33 &+ UInt64(byte) }
        return "\(hash)"
    }
}
