import SwiftUI

struct BookDetailView: View {
    @EnvironmentObject private var store: AppStore
    let book: Book

    @State private var readerConfig: ReaderConfig?
    @State private var showCatalog = false
    /// 当前展示的书 id（支持同名书切源后切换到另一个源版本）
    @State private var activeBookID: String
    @State private var showMergePicker = false
    @State private var showRename = false
    @State private var renameText = ""

    // 离线与导出
    @State private var cachedCount = 0
    @State private var isExporting = false
    @State private var shareItem: ShareItem?
    @State private var exportError: String?
    @State private var introExpanded = false

    struct ShareItem: Identifiable {
        let id = UUID()
        let url: URL
    }

    private static let previewChapterCount = 10

    init(book: Book) {
        self.book = book
        _activeBookID = State(initialValue: book.id)
    }

    private var current: Book {
        store.books.first(where: { $0.id == activeBookID }) ?? book
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                headerBlock

                actionButtons
                offlineSection

                VStack(alignment: .leading, spacing: 8) {
                    Text("简介").font(.headline)
                    Text(current.intro)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .lineSpacing(5)
                        .lineLimit(introExpanded ? nil : 4)
                    Button {
                        withAnimation(.easeInOut(duration: 0.2)) { introExpanded.toggle() }
                    } label: {
                        Text(introExpanded ? "收起" : "展开全部")
                            .font(.caption)
                            .foregroundStyle(Color.accentPurple)
                    }
                    .buttonStyle(.plain)
                }

                chapterList
            }
            .padding()
        }
        .navigationTitle("书籍详情")
        .navigationBarTitleDisplayMode(.inline)
        .task(id: current.id) {
            // 始终重拉完整详情（简介/字数/更新时间等），列表页的简介是截断的
            await store.refreshBookDetail(current)
            await store.loadChapters(for: current)
            cachedCount = await store.cachedChapterCount(for: current)
        }
        .onChange(of: store.offlineProgress[current.id] != nil) { downloading in
            if !downloading {
                // 优先用下载完成时的最终计数（offlineBookCounts），再兜底读磁盘
                if let count = store.offlineBookCounts[current.id] {
                    cachedCount = count
                } else {
                    Task { cachedCount = await store.cachedChapterCount(for: current) }
                }
            }
        }
        .sheet(item: $shareItem) { item in
            ShareSheet(items: [item.url])
        }
        .sheet(isPresented: $showMergePicker) {
            mergePicker
        }
        .alert("编辑书名", isPresented: $showRename) {
            TextField("书名", text: $renameText)
            Button("确定") {
                store.renameGroup(for: current, to: renameText)
            }
            Button("取消", role: .cancel) {}
        }
        .alert("导出失败", isPresented: Binding(
            get: { exportError != nil },
            set: { if !$0 { exportError = nil } }
        )) {
            Button("好", role: .cancel) {}
        } message: {
            Text(exportError ?? "")
        }
        .sheet(isPresented: $showCatalog) {
            CatalogView(book: current, currentChapter: current.lastChapter) { index in
                showCatalog = false
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
                    readerConfig = ReaderConfig(book: current, chapter: index)
                }
            }
        }
        .fullScreenCover(item: $readerConfig) { config in
            ReaderView(book: config.book, startChapter: config.chapter)
        }
    }

    // MARK: - 离线与导出

    private var downloadProgress: AppStore.OfflineProgress? {
        store.offlineProgress[current.id]
    }

    private var offlineSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 10) {
                Image(systemName: "arrow.down.circle")
                    .foregroundStyle(Color.accentPurple)
                VStack(alignment: .leading, spacing: 3) {
                    Text("离线缓存")
                        .font(.subheadline)
                        .fontWeight(.semibold)
                    Text(cacheStatusText)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                downloadControl
            }

            HStack(spacing: 12) {
                exportButton("导出 EPUB", systemImage: "book") {
                    await runExport(.epub)
                }
                exportButton("导出 TXT", systemImage: "doc.plaintext") {
                    await runExport(.txt)
                }
            }
        }
        .padding(12)
        .background(Color(uiColor: .secondarySystemBackground), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private var cacheStatusText: String {
        if let progress = downloadProgress {
            return "正在缓存 \(progress.done)/\(progress.total) 章"
        }
        let total = current.totalChapters
        if total == 0 { return "目录加载中…" }
        if cachedCount == 0 { return "未缓存，联网状态下可阅读" }
        if cachedCount >= total { return "已缓存全部 \(total) 章，可离线阅读" }
        return "已缓存 \(cachedCount)/\(total) 章"
    }

    @ViewBuilder
    private var downloadControl: some View {
        if let progress = downloadProgress {
            HStack(spacing: 10) {
                VStack(spacing: 4) {
                    ProgressView(value: Double(progress.done), total: Double(max(progress.total, 1)))
                        .frame(width: 80)
                    Text("\(progress.done)/\(progress.total)")
                        .font(.caption2.monospacedDigit())
                        .foregroundStyle(.secondary)
                }
                Button("取消") {
                    store.cancelDownload(bookID: current.id)
                }
                .font(.caption)
            }
        } else {
            Button(current.totalChapters > 0 ? "缓存全书" : "缓存") {
                Task { await store.downloadBook(current) }
            }
            .font(.subheadline)
            .fontWeight(.semibold)
            .foregroundStyle(Color.accentPurple)
            .disabled(current.totalChapters == 0)
        }
    }

    private func exportButton(_ label: String, systemImage: String, action: @escaping () async -> Void) -> some View {
        Button {
            Task { await action() }
        } label: {
            Label(label, systemImage: systemImage)
                .font(.subheadline)
                .fontWeight(.semibold)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
                .background(Color.accentPurple.opacity(0.12), in: Capsule())
                .foregroundStyle(Color.accentPurple)
        }
        .buttonStyle(.plain)
        .disabled(isExporting || downloadProgress != nil)
    }

    private func runExport(_ format: ExportFormat) async {
        isExporting = true
        defer { isExporting = false }
        do {
            shareItem = ShareItem(url: try await store.exportBook(current, format: format))
        } catch {
            exportError = error.localizedDescription
        }
    }

    // MARK: - 顶部信息块（突出封面、书名、标签、作者/文库/章数/字数/更新时间）

    private var headerBlock: some View {
        HStack(alignment: .top, spacing: 18) {
            BookCoverView(book: current)
                .frame(width: 130, height: 176)

            VStack(alignment: .leading, spacing: 10) {
                Text(store.displayTitle(for: current))
                    .font(.title2)
                    .fontWeight(.bold)
                    .lineLimit(3)
                    .multilineTextAlignment(.leading)

                sourceSwitcher

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(current.tags, id: \.self) { tag in
                            Text(tag)
                                .font(.caption)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 5)
                                .background(Color(uiColor: .secondarySystemBackground), in: Capsule())
                                .foregroundStyle(.secondary)
                        }
                    }
                }

                VStack(alignment: .leading, spacing: 4) {
                    infoLine("作者", current.author)
                    if let house = current.publishingHouse {
                        infoLine("文库", house)
                    }
                    if current.totalChapters > 0 {
                        infoLine("章数", "\(current.totalChapters) 章")
                    }
                    if let k = current.wordCountK, k > 0 {
                        infoLine("字数", "\(k) 千字")
                    }
                    if let update = current.lastUpdate {
                        infoLine("更新", update)
                    }
                }
            }
        }
    }

    /// 同名书切源 + 合并管理：显示当前源，点开列出其他源版本与管理项
    private var sourceSwitcher: some View {
        let siblings = store.siblingVersions(of: current)
        return Menu {
            ForEach(siblings, id: \.id) { sibling in
                Button {
                    switchTo(sibling)
                } label: {
                    Label(sibling.source, systemImage: sibling.id == current.id ? "checkmark" : "")
                }
            }
            Divider()
            Button {
                renameText = store.displayTitle(for: current)
                showRename = true
            } label: {
                Label("编辑书名", systemImage: "pencil")
            }
            Button {
                showMergePicker = true
            } label: {
                Label("合并到其他书", systemImage: "square.2.layers.3d")
            }
            if siblings.count > 1 {
                Button(role: .destructive) {
                    store.splitBook(current)
                } label: {
                    Label("从合并中拆分", systemImage: "rectangle.split.3x1")
                }
            }
        } label: {
            HStack(spacing: 4) {
                Text(current.source)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Image(systemName: "chevron.down")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(Color(uiColor: .secondarySystemBackground), in: Capsule())
        }
    }

    /// 手动合并：从书架/书库所有书里选一本与当前书合并
    private var mergePicker: some View {
        NavigationStack {
            List(store.books.filter { $0.id != current.id }) { other in
                Button {
                    store.mergeBooks(current, other)
                    showMergePicker = false
                } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(other.title).font(.subheadline).foregroundStyle(.primary)
                        Text(other.source).font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
            .navigationTitle("选择要合并的书")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("取消") { showMergePicker = false }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    private func switchTo(_ book: Book) {
        guard book.id != current.id else { return }
        // 切到同名书的另一个源版本：更新当前展示的书
        activeBookID = book.id
        Task {
            await store.loadChapters(for: book)
        }
    }

    private func infoLine(_ label: String, _ value: String) -> some View {
        HStack(spacing: 6) {
            Text(label)
                .font(.caption)
                .foregroundStyle(.secondary)
                .frame(width: 30, alignment: .leading)
            Text(value)
                .font(.caption)
                .foregroundStyle(.primary)
                .lineLimit(1)
        }
    }

    private var actionButtons: some View {
        HStack(spacing: 12) {
            Button {
                store.toggleSaved(current)
            } label: {
                Text(store.isSaved(current) ? "已加入书架" : "加入书架")
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(
                        store.isSaved(current) ? Color(uiColor: .secondarySystemBackground) : Color.accentPurple,
                        in: Capsule()
                    )
                    .foregroundStyle(store.isSaved(current) ? Color.primary : Color.white)
            }
            .buttonStyle(.plain)

            Button {
                let target = current.totalChapters > 0
                    ? min(current.lastChapter, current.totalChapters - 1)
                    : 0
                readerConfig = ReaderConfig(book: current, chapter: target)
            } label: {
                Text(current.lastChapter > 0 ? "继续阅读" : "开始阅读")
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(Color.accentPurple, in: Capsule())
                    .foregroundStyle(.white)
            }
            .buttonStyle(.plain)
        }
    }

    private var chapterList: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text("目录").font(.headline)
                Spacer()
                Text(current.totalChapters > 0 ? "共 \(current.totalChapters) 章" : "目录加载中…")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .padding(.bottom, 6)

            ForEach(0..<min(Self.previewChapterCount, current.totalChapters), id: \.self) { index in
                Button {
                    readerConfig = ReaderConfig(book: current, chapter: index)
                } label: {
                    chapterRow(index)
                }
                .buttonStyle(.plain)
            }

            if current.totalChapters > Self.previewChapterCount {
                Button {
                    showCatalog = true
                } label: {
                    HStack {
                        Text("查看完整目录")
                        Spacer()
                        Image(systemName: "chevron.right")
                    }
                    .font(.subheadline)
                    .foregroundStyle(Color.accentPurple)
                    .padding(.vertical, 10)
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func chapterRow(_ index: Int) -> some View {
        HStack(spacing: 10) {
            Text("\(index + 1)")
                .font(.caption.monospacedDigit())
                .foregroundStyle(.secondary)
                .frame(width: 30, alignment: .leading)
            Text(title(for: index))
                .foregroundStyle(
                    index == current.lastChapter
                        ? Color.accentPurple
                        : (index < current.lastChapter ? Color.secondary : Color.primary)
                )
            Spacer()
            if index == current.lastChapter {
                Text("上次读到")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            } else if index < current.lastChapter {
                Text("已读")
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
            }
        }
        .padding(.vertical, 8)
        .contentShape(Rectangle())
    }

    private func title(for index: Int) -> String {
        if let chapters = store.chapterTitles(for: current), index < chapters.count {
            return chapters[index].title
        }
        return Book.chapterTitle(index)
    }
}
