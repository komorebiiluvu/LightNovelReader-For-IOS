import Foundation
import SwiftUI

/// 把多张封面完成后的 SwiftUI 状态更新分散到相邻帧，避免一批网络请求同时
/// 返回时集中创建纹理、触发布局和合成。
private actor CoverPresentationPacer {
    static let shared = CoverPresentationPacer()
    private var nextPresentation: UInt64 = 0

    func reservationDelay() -> UInt64 {
        let now = DispatchTime.now().uptimeNanoseconds
        let scheduled = max(now, nextPresentation)
        nextPresentation = scheduled + CoverLoadingPolicy.presentationIntervalNanoseconds
        return scheduled - now
    }
}

struct BookCoverView: View {
    let book: Book
    var showsProgress = true
    var showsTitle = false
    var showsUpdateDot = true
    var cornerRadius: CGFloat = 16
    var shadowRadius: CGFloat = 8
    /// 长列表滚动时关闭封面淡入，避免每个新行出现时同时提交多组动画事务。
    var animatesImageLoading = true
    /// 长列表允许短暂延后缓存未命中的请求，一闪而过的 cell 会在启动网络前取消。
    var imageLoadDelayNanoseconds: UInt64 = 0

    private var colors: [Color] {
        coverPalette[book.coverIndex % coverPalette.count]
    }

    private var placeholder: LinearGradient {
        LinearGradient(colors: colors, startPoint: .topLeading, endPoint: .bottomTrailing)
    }

    var body: some View {
        Group {
            CoverImageView(
                urlString: book.coverURL,
                placeholder: placeholder,
                animatesLoading: animatesImageLoading,
                startDelayNanoseconds: imageLoadDelayNanoseconds
            )
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
                    .fill(.red)
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

/// 封面图加载视图：缓存层直接返回后台下采样并解码好的 UIImage。
/// SwiftUI body 不再调用 UIImage(data:)，避免新页出现时集中占用主线程。
struct CoverImageView: View {
    let urlString: String?
    let placeholder: LinearGradient
    let animatesLoading: Bool
    let startDelayNanoseconds: UInt64

    @State private var loadedImage: UIImage?
    @State private var loadedURL: String?

    var body: some View {
        Group {
            if let uiImage = displayImage {
                Image(uiImage: uiImage)
                    .resizable()
                    .scaledToFill()
            } else {
                placeholder
            }
        }
        .animation(animatesLoading ? .easeOut(duration: 0.14) : nil, value: displayImage != nil)
        .task(id: urlString) {
            await load()
        }
    }

    /// 内存命中时不需要等待异步 task 的下一轮视图刷新，首帧即可取到现成 UIImage。
    private var displayImage: UIImage? {
        guard let urlString, !urlString.isEmpty else { return nil }
        if loadedURL == urlString, let loadedImage {
            return loadedImage
        }
        return CoverImageCache.shared.memoryImage(urlString)
    }

    private func load() async {
        guard let urlString, !urlString.isEmpty else {
            loadedImage = nil
            loadedURL = nil
            return
        }
        if let hit = CoverImageCache.shared.memoryImage(urlString) {
            loadedImage = hit
            loadedURL = urlString
            return
        }
        loadedImage = nil
        loadedURL = nil

        if startDelayNanoseconds > 0 {
            do {
                try await Task.sleep(nanoseconds: startDelayNanoseconds)
            } catch {
                return
            }
        }
        guard !Task.isCancelled else { return }

        let image = await CoverImageCache.shared.image(for: urlString)
        guard !Task.isCancelled else { return }

        if image != nil {
            let delay = await CoverPresentationPacer.shared.reservationDelay()
            if delay > 0 {
                do {
                    try await Task.sleep(nanoseconds: delay)
                } catch {
                    return
                }
            }
        }
        guard !Task.isCancelled else { return }
        loadedImage = image
        loadedURL = image == nil ? nil : urlString
    }
}
