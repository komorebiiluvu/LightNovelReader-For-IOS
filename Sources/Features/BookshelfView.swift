import SwiftUI

struct BookshelfView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.horizontalSizeClass) private var sizeClass

    @State private var showShelfManager = false

    /// 动态列数：按容器宽度与目标最小封面宽计算，填满整行。
    /// 平板用更大的最小封面宽（宁可少几列，也要让每本书大到合适）。
    private func columns(containerWidth: CGFloat) -> [GridItem] {
        let width = containerWidth
        let minCellWidth: CGFloat = sizeClass == .regular ? 160 : 104
        let spacing: CGFloat = 12
        let count = max(2, Int((width - spacing) / (minCellWidth + spacing)))
        return Array(repeating: GridItem(.flexible(), spacing: spacing), count: count)
    }

    var body: some View {
        VStack(spacing: 0) {
            header
            contentArea
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .sheet(isPresented: $showShelfManager) {
            ShelfManageView()
        }
        .navigationTitle("书架")
    }

    /// 顶部固定区（书架切换 + 错误横幅 + 筛选 + 计数）
    private var header: some View {
        VStack(alignment: .leading, spacing: 16) {
            shelfSelector

            if let loadError = store.loadError {
                SourceErrorBanner(message: loadError) {
                    Task { await store.reload() }
                }
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(ShelfFilter.allCases) { filter in
                        Button {
                            store.shelfFilter = filter
                        } label: {
                            Text(filter.rawValue)
                                .font(.subheadline)
                                .padding(.horizontal, 15)
                                .padding(.vertical, 8)
                                .background(
                                    store.shelfFilter == filter
                                        ? Color.accentPurple.opacity(0.16)
                                        : Color(uiColor: .secondarySystemBackground),
                                    in: Capsule()
                                )
                                .foregroundStyle(store.shelfFilter == filter ? Color.accentPurple : Color.secondary)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }

            HStack {
                Text(currentShelfName).font(.headline)
                Spacer()
                Text("\(store.filteredBooks.count) 本")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                sortButton
            }
        }
        .padding(.horizontal)
        .padding(.top, 12)
        .padding(.bottom, 4)
    }

    /// 内容区：加载转圈 / 空态居中 / 网格（可下拉刷新）
    @ViewBuilder
    private var contentArea: some View {
        if store.isLoading && store.books.isEmpty {
            loadingIndicator
        } else if store.filteredBooks.isEmpty {
            emptyState
        } else {
            GeometryReader { proxy in
                ScrollView {
                    bookGrid(containerWidth: proxy.size.width)
                        .padding()
                }
                .refreshable {
                    await store.reload()
                    await store.checkForUpdates(force: true)
                }
            }
        }
    }

    // MARK: - 书架切换

    private var currentShelfName: String {
        switch store.selectedShelfID {
        case nil, "default": return "默认书架"
        case let id: return store.shelves.first(where: { $0.id == id })?.name ?? "书架"
        }
    }

    /// 排序区域：左侧正/倒序切换按钮，右侧排序依据选择菜单（两者职责分离）
    private var sortButton: some View {
        HStack(spacing: 6) {
            // 正/倒序切换按钮（独立，不再与排序依据菜单耦合）
            Button {
                store.shelfSortAscending.toggle()
            } label: {
                Image(systemName: store.shelfSortAscending ? "arrow.up" : "arrow.down")
                    .font(.caption.weight(.semibold))
                    .frame(width: 28, height: 28)
                    .background(Color(uiColor: .secondarySystemBackground), in: Circle())
                    .foregroundStyle(Color.secondary)
            }
            .buttonStyle(.plain)

            // 排序依据选择菜单
            Menu {
                ForEach(ShelfSort.allCases) { sort in
                    Button {
                        store.shelfSort = sort
                    } label: {
                        Label(sort.rawValue, systemImage: store.shelfSort == sort ? "checkmark" : "")
                    }
                }
            } label: {
                HStack(spacing: 4) {
                    Text(store.shelfSort.rawValue)
                        .font(.caption)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(Color(uiColor: .secondarySystemBackground), in: Capsule())
                .foregroundStyle(Color.secondary)
            }
        }
    }

    private var shelfSelector: some View {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        shelfChip(title: "默认书架", id: "default")
                        ForEach(store.shelves) { shelf in
                    Button {
                        store.selectedShelfID = shelf.id
                    } label: {
                        Text(shelf.name)
                            .font(.subheadline)
                            .fontWeight(store.selectedShelfID == shelf.id ? .semibold : .regular)
                            .padding(.horizontal, 13)
                            .padding(.vertical, 7)
                            .background(
                                store.selectedShelfID == shelf.id
                                    ? Color.accentPurple.opacity(0.16)
                                    : Color(uiColor: .secondarySystemBackground),
                                in: Capsule()
                            )
                            .foregroundStyle(store.selectedShelfID == shelf.id ? Color.accentPurple : Color.secondary)
                    }
                    .buttonStyle(.plain)
                }

                Button {
                    showShelfManager = true
                } label: {
                    Image(systemName: "plus.circle")
                        .font(.subheadline)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 7)
                        .background(Color(uiColor: .secondarySystemBackground), in: Capsule())
                        .foregroundStyle(Color.accentPurple)
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func shelfChip(title: String, id: String?) -> some View {
        Button {
            store.selectedShelfID = id
        } label: {
            Text(title)
                .font(.subheadline)
                .fontWeight(store.selectedShelfID == id ? .semibold : .regular)
                .padding(.horizontal, 13)
                .padding(.vertical, 7)
                .background(
                    store.selectedShelfID == id
                        ? Color.accentPurple.opacity(0.16)
                        : Color(uiColor: .secondarySystemBackground),
                    in: Capsule()
                )
                .foregroundStyle(store.selectedShelfID == id ? Color.accentPurple : Color.secondary)
        }
        .buttonStyle(.plain)
    }

    // MARK: - 网格

    private func bookGrid(containerWidth: CGFloat) -> some View {
        LazyVGrid(columns: columns(containerWidth: containerWidth), spacing: 16) {
            ForEach(store.filteredBooks) { book in
                NavigationLink {
                    BookDetailView(book: book)
                } label: {
                    VStack(alignment: .leading, spacing: 6) {
                        BookCoverView(book: book, shadowRadius: 0)
                            .overlay(alignment: .bottomTrailing) {
                                if store.siblingVersions(of: book).count > 1 {
                                    Image(systemName: "square.3.layers.3d")
                                        .font(.system(size: 12))
                                        .foregroundStyle(.white)
                                        .padding(6)
                                        .background(.black.opacity(0.45), in: Circle())
                                        .padding(6)
                                }
                            }
                        Text(store.displayTitle(for: book))
                            .font(.caption)
                            .foregroundStyle(.primary)
                            .lineLimit(1)
                        if book.progress > 0 {
                            ProgressView(value: book.progress)
                                .tint(.accentPurple)
                        }
                    }
                }
                .buttonStyle(.plain)
                .contextMenu {
                    Button {
                        store.toggleSaved(book)
                    } label: {
                        Label(store.isSaved(book) ? "移出默认书架" : "加入默认书架",
                              systemImage: store.isSaved(book) ? "trash" : "plus")
                    }
                    if !store.shelves.isEmpty {
                        Menu {
                            ForEach(store.shelves) { shelf in
                                Button {
                                    store.toggleBook(book.id, inShelf: shelf.id)
                                } label: {
                                    Label(
                                        store.isBook(book.id, inShelf: shelf.id) ? "从「\(shelf.name)」移出" : "加入「\(shelf.name)」",
                                        systemImage: store.isBook(book.id, inShelf: shelf.id) ? "minus.circle" : "plus.circle"
                                    )
                                }
                            }
                        } label: {
                            Label("移动到书架", systemImage: "books.vertical")
                        }
                    }
                    if book.hasUpdate {
                        Button {
                            store.clearUpdateFlag(for: book.id)
                        } label: {
                            Label("清除更新提醒", systemImage: "bell.slash")
                        }
                    }
                    Divider()
                    // 合并/拆分管理
                    if store.siblingVersions(of: book).count > 1 {
                        Button(role: .destructive) {
                            store.splitBook(book)
                        } label: {
                            Label("从合并中拆分", systemImage: "rectangle.split.3x1")
                        }
                    }
                    Button {
                        renameBook(book)
                    } label: {
                        Label("编辑书名", systemImage: "pencil")
                    }
                }
            }
        }
    }

    private func renameBook(_ book: Book) {
        // 分组改名：弹输入框
        let alert = UIAlertController(title: "编辑书名", message: nil, preferredStyle: .alert)
        alert.addTextField { $0.text = store.displayTitle(for: book) }
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        alert.addAction(UIAlertAction(title: "确定", style: .default) { _ in
            if let text = alert.textFields?.first?.text {
                store.renameGroup(for: book, to: text)
            }
        })
        if let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
           let root = scene.windows.first?.rootViewController {
            root.present(alert, animated: true)
        }
    }

    private var loadingIndicator: some View {
        VStack(spacing: 12) {
            ProgressView()
                .controlSize(.large)
            Text("正在加载书架…")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var emptyState: some View {
        VStack(spacing: 10) {
            Image(systemName: "books.vertical")
                .font(.system(size: 40))
                .foregroundStyle(.tertiary)
            Text(emptyTitle)
                .font(.subheadline)
                .fontWeight(.medium)
            Text(emptyHint)
                .font(.caption)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 40)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var emptyTitle: String {
        switch store.shelfFilter {
        case .all: return store.selectedShelfID == nil || store.selectedShelfID == "default" ? "书架还是空的" : "这个书架还没有书"
        case .saved: return "还没有收藏"
        case .updated: return "暂时没有更新"
        }
    }

    private var emptyHint: String {
        switch store.shelfFilter {
        case .all: return store.selectedShelfID == nil || store.selectedShelfID == "default" ? "去「探索」页找几本感兴趣的书，点「加入书架」收藏" : "长按书目里的书，选择「移动到书架」"
        case .saved: return "在书籍详情页点「加入书架」即可收藏"
        case .updated: return "书源有新章节时，这里会出现提醒"
        }
    }
}
