import SwiftUI
import UIKit

// MARK: - 竖版电池图标（填充高度表示电量，不显示百分比）

struct VerticalBattery: View {
    let level: Double
    let charging: Bool
    let color: Color

    var body: some View {
        // level < 0 表示电量未知（模拟器等），显示满格但更淡
        let unknown = level < 0
        let fill = CGFloat(max(0, min(unknown ? 1 : level, 1)))
        VStack(spacing: 1.5) {
            RoundedRectangle(cornerRadius: 0.8)
                .fill(color.opacity(0.45))
                .frame(width: 5, height: 2)
            RoundedRectangle(cornerRadius: 2.5)
                .strokeBorder(color, lineWidth: 1.1)
                .frame(width: 11, height: 18)
                .overlay(alignment: .bottom) {
                    GeometryReader { geo in
                        RoundedRectangle(cornerRadius: 1.5)
                            // 电量低时变红提示
                            .fill(unknown ? color.opacity(0.35) : (fill < 0.2 ? Color.red : color.opacity(0.9)))
                            .frame(height: max(1.5, (geo.size.height - 3.5) * fill))
                            .padding(1.75)
                    }
                }
                .overlay {
                    if charging {
                        Image(systemName: "bolt.fill")
                            .font(.system(size: 7, weight: .bold))
                            .foregroundStyle(Color.green)
                    }
                }
        }
        .accessibilityLabel(unknown ? "电量未知" : "电量 \(Int(fill * 100))%")
    }
}

// MARK: - 工具

extension String {
    /// 过长时保留头尾、省略中间
    func middleTruncated(head: Int = 6, tail: Int = 5) -> String {
        guard count > head + tail + 2 else { return self }
        return prefix(head) + "…" + suffix(tail)
    }
}

// MARK: - 中间药丸（收起态）

private struct ReaderStatusPill: View {
    let book: Book
    let chapterLabel: String
    let foreground: Color
    let background: Color

    var body: some View {
        HStack(spacing: 8) {
            BookCoverView(book: book, showsProgress: false, showsTitle: false, showsUpdateDot: false,
                          cornerRadius: 5, shadowRadius: 0)
                .frame(width: 25, height: 33)
            VStack(alignment: .center, spacing: 2) {
                Text(book.title.middleTruncated())
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(foreground)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                Text(chapterLabel.middleTruncated(head: 8, tail: 6))
                    .font(.system(size: 10))
                    .foregroundStyle(foreground.opacity(0.6))
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
        }
        .padding(.horizontal, 8)
        .frame(height: 42)
        .frame(maxWidth: 180)
        .background(Capsule().fill(background.opacity(0.99)))
        .overlay(Capsule().strokeBorder(foreground.opacity(0.16), lineWidth: 1))
        .shadow(color: .black.opacity(0.12), radius: 5, y: 2)
    }
}

// MARK: - 药丸展开后的浮窗（书架 / 下载）

struct ReaderBookPanel: View {
    @EnvironmentObject private var store: AppStore
    let book: Book
    let chapter: Int
    let foreground: Color
    let background: Color

    @State private var isDownloaded = false

    private var progress: AppStore.OfflineProgress? {
        store.offlineProgress[book.id]
    }

    var body: some View {
        VStack(spacing: 14) {
            HStack(spacing: 12) {
                BookCoverView(book: book, showsProgress: false, showsTitle: false, showsUpdateDot: false,
                              cornerRadius: 10, shadowRadius: 0)
                    .frame(width: 62, height: 86)
                VStack(alignment: .leading, spacing: 6) {
                    Text(book.title)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(foreground)
                        .lineLimit(2)
                    Text(book.author)
                        .font(.caption)
                        .foregroundStyle(foreground.opacity(0.55))
                    Text(book.totalChapters > 0 ? "第 \(chapter + 1) 章 · 共 \(book.totalChapters) 章" : "目录加载中…")
                        .font(.caption2)
                        .foregroundStyle(foreground.opacity(0.55))
                }
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity)

            HStack(spacing: 10) {
                Button {
                    store.toggleSaved(book)
                } label: {
                    Label(store.isSaved(book) ? "已在书架" : "加入书架",
                          systemImage: store.isSaved(book) ? "checkmark" : "books.vertical")
                        .font(.footnote.weight(.semibold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 9)
                        .background(Capsule().fill(Color.accentPurple.opacity(0.14)))
                        .foregroundStyle(Color.accentPurple)
                }
                .buttonStyle(.plain)

                if let progress {
                    HStack(spacing: 8) {
                        ProgressView(value: Double(progress.done), total: Double(max(progress.total, 1)))
                            .tint(Color.accentPurple)
                        Button {
                            store.cancelDownload(bookID: book.id)
                        } label: {
                            Text("取消")
                                .font(.footnote)
                                .foregroundStyle(foreground.opacity(0.7))
                        }
                    }
                    .frame(maxWidth: .infinity)
                } else {
                    Button {
                        Task { await store.downloadBook(book) }
                    } label: {
                        Label(isDownloaded ? "已下载" : "下载", systemImage: "arrow.down.circle")
                            .font(.footnote.weight(.semibold))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 9)
                            .background(Capsule().fill(Color.accentPurple.opacity(0.14)))
                            .foregroundStyle(Color.accentPurple)
                    }
                    .buttonStyle(.plain)
                    .disabled(book.totalChapters == 0 || isDownloaded)
                }
            }
        }
        .padding(14)
        .frame(maxWidth: 320)
        .background(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(background.opacity(0.98))
                .shadow(color: .black.opacity(0.18), radius: 14, y: 5)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .strokeBorder(foreground.opacity(0.12), lineWidth: 1)
        )
        .task(id: book.id) {
            refreshDownloadedState()
        }
        .onChange(of: store.offlineProgress[book.id] != nil) { downloading in
            if !downloading {
                refreshDownloadedState()
            }
        }
    }

    private func refreshDownloadedState() {
        Task {
            let cached = await store.cachedChapterCount(for: book)
            isDownloaded = book.totalChapters > 0 && cached >= book.totalChapters
        }
    }
}

// MARK: - 底部状态栏（左：电池+时间；中：药丸；右：设置+目录）

struct ReaderStatusBar: View {
    let book: Book
    let chapterLabel: String
    let foreground: Color
    let background: Color
    let now: Date
    let batteryLevel: Float
    let isCharging: Bool
    @Binding var pillExpanded: Bool
    let onOpenSettings: () -> Void
    let onOpenCatalog: () -> Void

    private static let timeFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "h:mm a"
        return formatter
    }()

    var body: some View {
        ZStack {
            HStack(spacing: 6) {
                VerticalBattery(
                    level: Double(batteryLevel),
                    charging: isCharging,
                    color: foreground
                )
                Text(Self.timeFormatter.string(from: now))
                    .font(.system(size: 12, weight: .medium))
                    .monospacedDigit()
                    .foregroundStyle(foreground)
                Spacer(minLength: 0)

                Button(action: onOpenSettings) {
                    Text("Aa")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(foreground)
                        .frame(width: 40, height: 40)
                        .contentShape(Circle())
                }
                .buttonStyle(.plain)
                Button(action: onOpenCatalog) {
                    Image(systemName: "list.bullet")
                        .font(.system(size: 18))
                        .foregroundStyle(foreground)
                        .frame(width: 40, height: 40)
                        .contentShape(Circle())
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 16)

            ReaderStatusPill(book: book, chapterLabel: chapterLabel, foreground: foreground, background: background)
                .onTapGesture {
                    withAnimation(.spring(response: 0.38, dampingFraction: 0.82)) {
                        pillExpanded.toggle()
                    }
                }
        }
        .frame(height: 52)
        .background(background.opacity(0.97))
    }
}
