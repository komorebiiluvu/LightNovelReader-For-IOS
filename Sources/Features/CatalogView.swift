import SwiftUI

struct CatalogView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss

    let book: Book
    let currentChapter: Int
    /// 当前章内的已读比例（0~1）；从详情页打开时为 nil
    var chapterProgress: Double? = nil
    let onSelect: (Int) -> Void

    @State private var ascending = true
    /// 用户手动改过的折叠状态；nil = 尚未手动操作，使用计算式默认值（只展开当前章所在卷）。
    /// 用计算式默认值是为了在目录数据异步到位前后都不会出现“全展开”的中间态闪变。
    @State private var userCollapsed: Set<String>? = nil

    private var collapsedVolumes: Set<String> {
        userCollapsed ?? defaultCollapsedVolumes
    }

    private var defaultCollapsedVolumes: Set<String> {
        guard showsVolumeHeaders else { return [] }
        let currentVolume = items.first { $0.index == currentChapter }?.volume ?? "正文"
        return Set(groups.map(\.name).filter { $0 != currentVolume })
    }

    // MARK: - 数据分组

    private struct VolumeGroup: Identifiable {
        let name: String
        let chapters: [ChapterItem]
        var id: String { name }
    }

    private var items: [ChapterItem] {
        if let chapters = store.chapterTitles(for: book), chapters.count == book.totalChapters {
            return chapters
        }
        guard book.totalChapters > 0 else { return [] }
        return (0..<book.totalChapters).map { ChapterItem(index: $0, title: Book.chapterTitle($0)) }
    }

    private var groups: [VolumeGroup] {
        var order: [String] = []
        var buckets: [String: [ChapterItem]] = [:]
        for item in items {
            let key = item.volume ?? "正文"
            if buckets[key] == nil { order.append(key) }
            buckets[key, default: []].append(item)
        }
        var result = order.map { VolumeGroup(name: $0, chapters: buckets[$0] ?? []) }
        if !ascending {
            result.reverse()
            result = result.map { VolumeGroup(name: $0.name, chapters: $0.chapters.reversed()) }
        }
        return result
    }

    /// 目录带分卷信息时才显示分卷头（模拟源/目录未加载时退化为平铺）
    private var showsVolumeHeaders: Bool {
        items.contains { $0.volume != nil }
    }

    /// 整书已读比例（按当前进度章计算）
    private var bookReadPercent: Int {
        guard book.totalChapters > 0 else { return 0 }
        let read = min(max(currentChapter, 0), book.totalChapters - 1) + 1
        return Int(Double(read) / Double(book.totalChapters) * 100)
    }

    // MARK: -

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                Text(progressText)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 9)
                    .background(Color(uiColor: .secondarySystemBackground))

                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(spacing: 0) {
                            ForEach(groups) { group in
                                if showsVolumeHeaders {
                                    volumeHeader(group)
                                }
                                if !showsVolumeHeaders || !collapsedVolumes.contains(group.name) {
                                    ForEach(group.chapters) { item in
                                        chapterRow(item)
                                    }
                                }
                            }
                        }
                    }
                    .onAppear {
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
                            withAnimation {
                                proxy.scrollTo("chapter-\(currentChapter)", anchor: .center)
                            }
                        }
                    }
                }
            }
            .task {
                await store.loadChapters(for: book)
            }
            .navigationTitle("目录 · 共 \(book.totalChapters) 章")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        withAnimation { ascending.toggle() }
                    } label: {
                        Label(ascending ? "倒序" : "正序", systemImage: "arrow.up.arrow.down")
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("完成") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    private var progressText: String {
        if let chapterProgress {
            return "当前章节已读 \(Int(chapterProgress * 100))%，整书已读 \(bookReadPercent)%"
        }
        return "整书已读 \(bookReadPercent)%"
    }

    // MARK: - 行视图

    private func volumeHeader(_ group: VolumeGroup) -> some View {
        Button {
            withAnimation(.easeInOut(duration: 0.2)) {
                var next = userCollapsed ?? defaultCollapsedVolumes
                if next.contains(group.name) {
                    next.remove(group.name)
                } else {
                    next.insert(group.name)
                }
                userCollapsed = next
            }
        } label: {
            HStack(spacing: 8) {
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.semibold))
                    .rotationEffect(.degrees(collapsedVolumes.contains(group.name) ? 0 : 90))
                Text(group.name)
                    .font(.subheadline.weight(.semibold))
                Text("\(group.chapters.count) 章")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(Color(uiColor: .secondarySystemBackground).opacity(0.6))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func chapterRow(_ item: ChapterItem) -> some View {
        Button {
            onSelect(item.index)
            dismiss()
        } label: {
            HStack(spacing: 10) {
                Text("\(item.index + 1)")
                    .font(.caption.monospacedDigit())
                    .foregroundStyle(.secondary)
                    .frame(width: 30, alignment: .leading)
                // 不截断，超长章节名完整折行
                Text(item.title)
                    .foregroundStyle(rowColor(for: item.index))
                    .fixedSize(horizontal: false, vertical: true)
                Spacer()
                if item.index == currentChapter {
                    Text("上次读到")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                } else if item.index < currentChapter {
                    Text("已读")
                        .font(.caption2)
                        .foregroundStyle(.tertiary)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 9)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .id("chapter-\(item.index)")
    }

    private func rowColor(for index: Int) -> Color {
        if index == currentChapter {
            return .accentPurple
        }
        return index < currentChapter ? .secondary : .primary
    }
}
