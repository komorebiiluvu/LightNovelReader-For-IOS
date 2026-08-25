import SwiftUI

struct BookCoverView: View {
    let book: Book
    var showsProgress = true
    var showsTitle = false
    var showsUpdateDot = true
    var cornerRadius: CGFloat = 16
    var shadowRadius: CGFloat = 8

    private var colors: [Color] {
        coverPalette[book.coverIndex % coverPalette.count]
    }

    private var placeholder: LinearGradient {
        LinearGradient(colors: colors, startPoint: .topLeading, endPoint: .bottomTrailing)
    }

    var body: some View {
        Group {
            CoverImageView(urlString: book.coverURL, placeholder: placeholder)
        }
        .overlay(alignment: .bottomLeading) {
            if showsTitle {
                Text(book.title)
                    .font(.caption)
                    .fontWeight(.semibold)
                    .foregroundStyle(.white)
                    .multilineTextAlignment(.leading)
                    .lineLimit(3)
                    .padding(9)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(
                        LinearGradient(colors: [.clear, .black.opacity(0.55)], startPoint: .center, endPoint: .bottom)
                    )
            }
        }
        .overlay(alignment: .topLeading) {
            if showsProgress && book.progress > 0 {
                Text("\(Int(book.progress * 100))%")
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 7)
                    .padding(.vertical, 3)
                    .background(.black.opacity(0.35), in: Capsule())
                    .padding(8)
            }
        }
        .overlay(alignment: .topTrailing) {
            if showsUpdateDot && book.hasUpdate {
                Circle()
                    .fill(.yellow)
                    .frame(width: 8, height: 8)
                    .padding(9)
            }
        }
        .aspectRatio(3 / 4, contentMode: .fit)
        .frame(maxWidth: .infinity)
        .clipped()
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
        .shadow(color: .black.opacity(shadowRadius > 0 ? 0.18 : 0), radius: shadowRadius, y: shadowRadius > 0 ? 4 : 0)
    }
}

/// 封面图加载视图：走 CoverImageCache（磁盘 + 内存 + 重试），
/// 失败或加载中显示占位渐变；视图重入（滑出再滑回）会重新触发加载。
/// 内存命中时首帧直接渲染图片（同步读取），图片淡入避免闪动。
/// 展示尺寸的下采样/位图解压在后台 Task 执行，结果按 url+尺寸缓存，
/// 避免滚动时每个封面 cell 在主线程重复解码导致掉帧。
struct CoverImageView: View {
    let urlString: String?
    let placeholder: LinearGradient

    @State private var imageData: Data?
    @State private var failed = false

    /// 后台下采样结果缓存：url + 展示尺寸 → 位图 Data。
    /// 同一封面在书架/探索等多处显示、或滚动重入时复用，避免主线程重复解码。
    /// 封面位图几十 KB/张，量级可控；内存警告时由系统回收。
    /// 读写都在 @MainActor 的 decodeIfNeeded 内串行进行，无需加锁。
    private static var decodeCache: [String: Data] = [:]

    var body: some View {
        GeometryReader { geo in
            Group {
                if let imageData, let uiImage = UIImage(data: imageData) {
                    Image(uiImage: uiImage)
                        .resizable()
                        .scaledToFill()
                } else {
                    placeholder
                }
            }
            .animation(.easeOut(duration: 0.18), value: imageData)
            .task(id: decodeTaskKey(size: geo.size)) {
                await decodeIfNeeded(size: geo.size)
            }
        }
        .task(id: urlString) {
            await load()
        }
    }

    /// 解码任务标识：url + 数据版本 + 展示尺寸。
    /// 数据版本（count）保证 imageData 从 nil→data→已解码 的每次变化都重新求值；
    /// 尺寸变化（旋转/换设备）时同样自动重新触发。
    private func decodeTaskKey(size: CGSize) -> String {
        "\(urlString ?? "")#\(imageData?.count ?? -1)#\(Int(size.width))x\(Int(size.height))"
    }

    /// 缓存标识：url + 展示尺寸（同尺寸复用，不随数据版本变化）
    private func decodeCacheKey(size: CGSize) -> String {
        "\(urlString ?? "")#\(Int(size.width))x\(Int(size.height))"
    }

    /// 位图已按展示尺寸解码则跳过；否则在后台下采样并写缓存。
    private func decodeIfNeeded(size: CGSize) async {
        let cacheKey = decodeCacheKey(size: size)
        if Self.decodeCache[cacheKey] != nil { return }
        guard let imageData, !imageData.isEmpty else { return }
        let decoded = await Task.detached(priority: .utility) {
            Self.decodedData(imageData, size: size)
        }.value
        guard let decoded else { return }
        Self.decodeCache[cacheKey] = decoded
        // 解码期间尺寸未再变化才更新显示，避免旧尺寸覆盖
        if decodeCacheKey(size: size) == cacheKey {
            self.imageData = decoded
        }
    }

    /// 按展示尺寸下采样（ImageIO 缩略图解码，线程安全，可在后台执行）。
    private static func decodedData(_ data: Data, size: CGSize) -> Data? {
        guard !data.isEmpty, size.width > 0, size.height > 0 else { return data }
        let scale = UIScreen.main.scale
        let maxPixel = min(max(size.width, size.height) * scale, CoverImageCache.downsampledMaxPixel)
        return CoverImageCache.downsample(data, maxPixel: maxPixel) ?? data
    }

    private func load() async {
        guard let urlString, !urlString.isEmpty else {
            failed = true
            return
        }
        // 同步命中内存 → 首帧直接显示，不闪
        if let hit = CoverImageCache.shared.memoryData(urlString) {
            imageData = hit
            return
        }
        imageData = nil
        failed = false
        if let data = await CoverImageCache.shared.data(for: urlString) {
            imageData = data
        } else {
            failed = true
        }
    }
}
