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
struct CoverImageView: View {
    let urlString: String?
    let placeholder: LinearGradient

    @State private var imageData: Data?
    @State private var failed = false

    var body: some View {
        GeometryReader { geo in
            Group {
                if let imageData, let uiImage = decode(imageData, size: geo.size) {
                    Image(uiImage: uiImage)
                        .resizable()
                        .scaledToFill()
                } else {
                    placeholder
                }
            }
            .animation(.easeOut(duration: 0.18), value: imageData)
        }
        .task(id: urlString) {
            await load()
        }
    }

    /// 按视图实际渲染尺寸解码（@3x 屏幕 1pt ≈ 3px）。
    /// 内存缓存已按 420px 下采样，这里对详情页等大尺寸场景再按需放大/缩小解码，
    /// 保证小列表不放大原图、大封面不清。
    private func decode(_ data: Data, size: CGSize) -> UIImage? {
        guard !data.isEmpty, size.width > 0, size.height > 0 else { return nil }
        let scale = UIScreen.main.scale
        let maxPixel = min(max(size.width, size.height) * scale, CoverImageCache.downsampledMaxPixel)
        if let downsampled = CoverImageCache.downsample(data, maxPixel: maxPixel) {
            return UIImage(data: downsampled)
        }
        return UIImage(data: data)
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
