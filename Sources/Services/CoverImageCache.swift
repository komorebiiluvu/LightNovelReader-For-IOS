import Foundation
import UIKit
import ImageIO

/// 封面加载的可测试性能策略。
enum CoverLoadingPolicy {
    /// 快速滚动时，短暂停留后才真正加载；一闪而过的 cell 会先被 SwiftUI 取消。
    static let exploreCellStartDelayNanoseconds: UInt64 = 80_000_000
    /// 每次最多预热一行多一点，防止与可见 cell 争抢 CPU、I/O 和网络。
    static let defaultPrewarmLimit = 4
    /// 网络/磁盘同时完成时，把 UIImage 状态提交摊到相邻显示帧。
    static let presentationIntervalNanoseconds: UInt64 = 8_000_000

    static func isForeground(_ priority: TaskPriority) -> Bool {
        priority.rawValue >= TaskPriority.userInitiated.rawValue
    }
}

/// 分前台/后台队列限制重任务。前台可见封面始终先于预热任务取得名额，避免
/// 快速滚动留下的后台队列挡住当前屏幕。
private actor CoverLoadGate {
    private struct Waiter {
        let id: UUID
        let continuation: CheckedContinuation<Bool, Never>
    }

    private let limit: Int
    private var running = 0
    private var foregroundWaiters: [Waiter] = []
    private var backgroundWaiters: [Waiter] = []

    init(limit: Int) {
        self.limit = max(1, limit)
    }

    func acquire(priority: TaskPriority) async -> Bool {
        guard !Task.isCancelled else { return false }
        if running < limit {
            running += 1
            return true
        }

        let id = UUID()
        return await withTaskCancellationHandler(
            handler: {
                Task { await self.cancelWaiter(id) }
            },
            operation: {
                await self.enqueueWaiter(id: id, priority: priority)
            }
        )
    }

    /// 单独留在 actor 隔离域内，兼容工程使用的 Swift 5.7 并发检查规则。
    private func enqueueWaiter(id: UUID, priority: TaskPriority) async -> Bool {
        await withCheckedContinuation { continuation in
            guard !Task.isCancelled else {
                continuation.resume(returning: false)
                return
            }
            let waiter = Waiter(id: id, continuation: continuation)
            if CoverLoadingPolicy.isForeground(priority) {
                foregroundWaiters.append(waiter)
            } else {
                backgroundWaiters.append(waiter)
            }
        }
    }

    private func cancelWaiter(_ id: UUID) {
        if let index = foregroundWaiters.firstIndex(where: { $0.id == id }) {
            foregroundWaiters.remove(at: index).continuation.resume(returning: false)
        } else if let index = backgroundWaiters.firstIndex(where: { $0.id == id }) {
            backgroundWaiters.remove(at: index).continuation.resume(returning: false)
        }
    }

    private func resumeFirstWaiter() -> Bool {
        if !foregroundWaiters.isEmpty {
            foregroundWaiters.removeFirst().continuation.resume(returning: true)
            return true
        }
        if !backgroundWaiters.isEmpty {
            backgroundWaiters.removeFirst().continuation.resume(returning: true)
            return true
        }
        return false
    }

    func release() {
        if !resumeFirstWaiter() {
            running = max(0, running - 1)
        }
    }
}

/// 原图磁盘写入串行化且不阻塞封面上屏。避免同一时刻多次 atomic write 与滚动
/// 争用文件系统，同时让已经解码的 UIImage 可以先交给 UI。
private actor CoverDiskWriter {
    func write(_ data: Data, to fileURL: URL) {
        try? data.write(to: fileURL, options: .atomic)
    }
}

/// 封面图片缓存：已解码 UIImage 内存缓存 + 原图磁盘缓存 + 请求去重。
///
/// 关键约束：
/// - `BookCoverView.body` 只能读取已经解码的 UIImage，不能在滚动时执行 UIImage(data:)；
/// - 磁盘/解码与网络使用独立限流器，慢网络不会堵住本地缓存命中；
/// - 同一 URL 只允许一个底层任务，并按订阅者计数取消已经完全离屏的任务；
/// - 前台可见封面优先于后台预热，图片完成后再由视图层按帧摊开状态提交；
/// - NSCache 按像素成本自动回收，收到内存警告时立即清空。
final class CoverImageCache {
    static let shared = CoverImageCache()

    private struct InflightLoad {
        let task: Task<UIImage?, Never>
        var subscribers: Set<UUID>
    }

    private let lock = NSLock()
    private let memory = NSCache<NSString, UIImage>()
    private var inflight: [String: InflightLoad] = [:]
    private let networkGate: CoverLoadGate
    private let decodeGate: CoverLoadGate
    private let diskWriter = CoverDiskWriter()
    private let root: URL

    /// 封面展示宽约 100~190pt；420px 足够 @3x 手机和 @2x iPad。
    static let downsampledMaxPixel: CGFloat = 420
    static let defaultPrewarmLimit = CoverLoadingPolicy.defaultPrewarmLimit

    private static let session: URLSession = {
        let configuration = URLSessionConfiguration.default
        configuration.requestCachePolicy = .returnCacheDataElseLoad
        configuration.timeoutIntervalForRequest = 15
        configuration.httpMaximumConnectionsPerHost = 4
        configuration.urlCache = .shared
        return URLSession(configuration: configuration)
    }()

    init(root: URL? = nil, maxConcurrentLoads: Int = 4) {
        if let root {
            self.root = root
        } else {
            self.root = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
                .appendingPathComponent("LNRCovers", isDirectory: true)
        }
        try? FileManager.default.createDirectory(at: self.root, withIntermediateDirectories: true)

        networkGate = CoverLoadGate(limit: maxConcurrentLoads)
        decodeGate = CoverLoadGate(limit: min(2, max(1, maxConcurrentLoads)))

        // 3:4 封面最长边 420px 时约 315×420×4 = 0.5 MiB；按真实像素成本回收。
        memory.countLimit = 96
        memory.totalCostLimit = 80 * 1_024 * 1_024
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleMemoryWarning),
            name: UIApplication.didReceiveMemoryWarningNotification,
            object: nil
        )
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    @objc private func handleMemoryWarning() {
        memory.removeAllObjects()
    }

    // MARK: - 内存缓存

    /// 同步返回已下采样且已解码的图片，供 SwiftUI 首帧直接使用。
    func memoryImage(_ urlString: String) -> UIImage? {
        memory.object(forKey: canonicalURLString(urlString) as NSString)
    }

    private func insertMemory(_ key: String, _ image: UIImage) {
        let bytesPerRow = image.cgImage?.bytesPerRow ?? Int(image.size.width * image.scale * 4)
        let height = image.cgImage?.height ?? Int(image.size.height * image.scale)
        memory.setObject(image, forKey: key as NSString, cost: max(1, bytesPerRow * height))
    }

    // MARK: - 可取消的请求去重

    private func loadTask(
        for key: String,
        priority: TaskPriority
    ) -> (task: Task<UIImage?, Never>, subscriber: UUID) {
        let subscriber = UUID()
        lock.lock()
        defer { lock.unlock() }

        if var existing = inflight[key] {
            existing.subscribers.insert(subscriber)
            inflight[key] = existing
            return (existing.task, subscriber)
        }

        let task = Task.detached(priority: priority) { [weak self] in
            await self?.load(key, priority: priority)
        }
        inflight[key] = InflightLoad(task: task, subscribers: [subscriber])
        return (task, subscriber)
    }

    /// 每个可见 cell 是一个订阅者。最后一个订阅者取消时才取消共享任务；若同一封面
    /// 同时被另一处显示或预热，仍保留那一份任务。
    private func releaseSubscriber(
        _ subscriber: UUID,
        key: String,
        cancelIfUnused: Bool
    ) {
        var taskToCancel: Task<UIImage?, Never>?
        lock.lock()
        if var entry = inflight[key], entry.subscribers.remove(subscriber) != nil {
            if entry.subscribers.isEmpty {
                inflight[key] = nil
                if cancelIfUnused {
                    taskToCancel = entry.task
                }
            } else {
                inflight[key] = entry
            }
        }
        lock.unlock()
        taskToCancel?.cancel()
    }

    // MARK: - 异步加载

    func image(for urlString: String, priority: TaskPriority = .userInitiated) async -> UIImage? {
        guard !urlString.isEmpty, !Task.isCancelled else { return nil }
        let key = canonicalURLString(urlString)
        if let cached = memory.object(forKey: key as NSString) { return cached }

        let load = loadTask(for: key, priority: priority)
        return await withTaskCancellationHandler(
            handler: {
                releaseSubscriber(load.subscriber, key: key, cancelIfUnused: true)
            },
            operation: {
                let result = await load.task.value
                releaseSubscriber(load.subscriber, key: key, cancelIfUnused: false)
                return result
            }
        )
    }

    /// 只预热传入序列的前几张图，避免页数增加后反复为全部历史书创建任务。
    func prewarm<S: Sequence>(
        _ urlStrings: S,
        limit: Int = CoverImageCache.defaultPrewarmLimit
    ) where S.Element == String {
        let urls = Array(urlStrings.lazy.filter { !$0.isEmpty }.prefix(max(0, limit)))
        guard !urls.isEmpty else { return }
        Task.detached(priority: .utility) { [weak self] in
            guard let self else { return }
            for url in urls {
                guard !Task.isCancelled else { return }
                _ = await self.image(for: url, priority: .utility)
            }
        }
    }

    /// 清理旧缓存。调用方应放在后台任务中，避免大量文件属性读取阻塞首帧。
    func pruneOlderThan(days: Int) {
        let cutoff = Date().addingTimeInterval(-Double(days) * 86_400)
        guard let files = try? FileManager.default.contentsOfDirectory(
            at: root,
            includingPropertiesForKeys: [.contentModificationDateKey]
        ) else { return }
        for file in files {
            if let modified = (try? file.resourceValues(forKeys: [.contentModificationDateKey]))?.contentModificationDate,
               modified < cutoff {
                try? FileManager.default.removeItem(at: file)
            }
        }
    }

    /// 先让本地缓存经过独立的双并发解码通道；即使网络有 4 个慢请求在等待，
    /// 已缓存封面也可以立即读取、下采样并返回。
    private func load(_ canonicalURL: String, priority: TaskPriority) async -> UIImage? {
        guard !Task.isCancelled else { return nil }

        guard await decodeGate.acquire(priority: priority) else { return nil }
        guard !Task.isCancelled else {
            await decodeGate.release()
            return nil
        }
        let cached = loadCachedImage(canonicalURL)
        await decodeGate.release()
        if let cached { return cached }
        guard !Task.isCancelled, let url = URL(string: canonicalURL) else { return nil }

        let fileURL = root.appendingPathComponent(StableHash.hex(canonicalURL))
        for attempt in 0..<3 {
            guard !Task.isCancelled else { return nil }

            guard await networkGate.acquire(priority: priority) else { return nil }
            guard !Task.isCancelled else {
                await networkGate.release()
                return nil
            }

            var responseData: Data?
            do {
                var request = URLRequest(url: url)
                request.cachePolicy = .returnCacheDataElseLoad
                request.timeoutInterval = 15
                let (data, response) = try await Self.session.data(for: request)
                if let http = response as? HTTPURLResponse,
                   http.statusCode == 200,
                   !data.isEmpty {
                    responseData = data
                }
            } catch {
                // 离屏取消或临时网络失败，释放网络名额后在下方统一处理。
            }
            await networkGate.release()

            guard !Task.isCancelled else { return nil }
            if let data = responseData {
                guard await decodeGate.acquire(priority: priority) else { return nil }
                guard !Task.isCancelled else {
                    await decodeGate.release()
                    return nil
                }
                let image = Self.downsample(data, maxPixel: Self.downsampledMaxPixel)
                await decodeGate.release()

                if let image {
                    insertMemory(canonicalURL, image)
                    let writer = diskWriter
                    Task.detached(priority: .utility) {
                        await writer.write(data, to: fileURL)
                    }
                    return image
                }
            }

            if attempt < 2 {
                do {
                    try await Task.sleep(nanoseconds: UInt64(attempt + 1) * 400_000_000)
                } catch {
                    return nil
                }
            }
        }
        return nil
    }

    private func loadCachedImage(_ canonicalURL: String) -> UIImage? {
        let fileURL = root.appendingPathComponent(StableHash.hex(canonicalURL))
        let legacyURL = root.appendingPathComponent(legacyHash(canonicalURL))
        // 兼容上一版以 HTTP URL 计算出的稳定哈希及更早的 DJB2 哈希；命中后迁移到 HTTPS 键。
        let oldHTTPURL = canonicalURL.replacingOccurrences(
            of: "https://img.wenku8.com/",
            with: "http://img.wenku8.com/",
            options: [.anchored, .caseInsensitive]
        )
        let candidates = [
            fileURL,
            legacyURL,
            root.appendingPathComponent(StableHash.hex(oldHTTPURL)),
            root.appendingPathComponent(legacyHash(oldHTTPURL)),
        ]

        for cachedURL in candidates where FileManager.default.fileExists(atPath: cachedURL.path) {
            guard let data = try? Data(contentsOf: cachedURL, options: [.mappedIfSafe]),
                  !data.isEmpty,
                  let image = Self.downsample(data, maxPixel: Self.downsampledMaxPixel) else {
                try? FileManager.default.removeItem(at: cachedURL)
                continue
            }
            if cachedURL != fileURL {
                try? FileManager.default.moveItem(at: cachedURL, to: fileURL)
            }
            insertMemory(canonicalURL, image)
            return image
        }
        return nil
    }

    /// 直接生成已解码的缩略图；不再转回 JPEG Data，避免视图层二次解码。
    static func downsample(_ data: Data, maxPixel: CGFloat) -> UIImage? {
        guard !data.isEmpty else { return nil }
        return autoreleasepool {
            let sourceOptions = [kCGImageSourceShouldCache: false] as CFDictionary
            guard let source = CGImageSourceCreateWithData(data as CFData, sourceOptions) else { return nil }
            let options = [
                kCGImageSourceCreateThumbnailFromImageAlways: true,
                kCGImageSourceCreateThumbnailWithTransform: true,
                kCGImageSourceShouldCacheImmediately: true,
                kCGImageSourceThumbnailMaxPixelSize: maxPixel,
            ] as CFDictionary
            guard let cgImage = CGImageSourceCreateThumbnailAtIndex(source, 0, options) else { return nil }
            return UIImage(cgImage: cgImage)
        }
    }

    /// wenku8 图床支持 HTTPS/HTTP2；统一键值也避免同一封面形成两份缓存。
    static func canonicalURLString(_ urlString: String) -> String {
        urlString.replacingOccurrences(
            of: "http://img.wenku8.com/",
            with: "https://img.wenku8.com/",
            options: [.anchored, .caseInsensitive]
        )
    }

    private func canonicalURLString(_ urlString: String) -> String {
        Self.canonicalURLString(urlString)
    }

    private func legacyHash(_ text: String) -> String {
        var hash: UInt64 = 5_381
        for byte in text.utf8 { hash = hash &* 33 &+ UInt64(byte) }
        return "\(hash)"
    }
}
