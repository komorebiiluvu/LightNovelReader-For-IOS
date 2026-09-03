import SwiftUI

/// 探索分页的纯策略，独立出来便于单元测试。
enum ExplorePaginationPolicy {
    /// 手机网格约 3 列，12 本约等于提前 4 行（约 1.5~2 屏）请求下一页。
    static let preloadDistance = 12

    static func prefetchIndex(itemCount: Int) -> Int? {
        guard itemCount > 0 else { return nil }
        return max(0, itemCount - preloadDistance)
    }

    static func shouldPrefetch(visibleIndex: Int, itemCount: Int) -> Bool {
        guard visibleIndex >= 0, visibleIndex < itemCount else { return false }
        // 每一批数据只让一个稳定单元格触发预取，避免末尾 12 个 cell 各自创建 Task。
        return visibleIndex == prefetchIndex(itemCount: itemCount)
    }
}

/// 固定列宽与固定行高让滚动容器从一开始就知道每一行的准确几何尺寸。
struct ExploreGridMetrics: Equatable {
    static let horizontalContentPadding: CGFloat = 32
    static let spacing: CGFloat = 14
    /// 两行 14pt 书名的固定区域；显式保留两行空间，使同一行作者基线一致。
    static let titleHeight: CGFloat = 36
    static let authorHeight: CGFloat = 16
    static let coverTextSpacing: CGFloat = 8
    static let titleAuthorSpacing: CGFloat = 4

    let columnCount: Int
    let cellWidth: CGFloat

    static func resolve(containerWidth: CGFloat, regularWidth: Bool) -> ExploreGridMetrics {
        let availableWidth = max(containerWidth - horizontalContentPadding, 1)
        let minimumWidth: CGFloat = regularWidth ? 160 : 104
        let count = max(2, Int((availableWidth + spacing) / (minimumWidth + spacing)))
        let width = max(
            1,
            (availableWidth - CGFloat(count - 1) * spacing) / CGFloat(count)
        )
        return ExploreGridMetrics(columnCount: count, cellWidth: width)
    }

    var columns: [GridItem] {
        Array(
            repeating: GridItem(.fixed(cellWidth), spacing: Self.spacing),
            count: columnCount
        )
    }

    var coverHeight: CGFloat { cellWidth * 4 / 3 }

    var cellHeight: CGFloat {
        coverHeight
            + Self.titleHeight
            + Self.authorHeight
            + Self.coverTextSpacing
            + Self.titleAuthorSpacing
    }
}

/// LazyVGrid 会在一行真正出现时重新测量整行，iOS 16/17 上可能修正 ScrollView
/// 的 contentOffset，体感像滚到行缝时被上一行吸住。改成等高、稳定身份的显式行。
struct ExploreGridRow: Identifiable, Equatable {
    let index: Int
    let books: [Book]

    var id: String { books.first?.id ?? "empty-row-\(index)" }

    static func make(from books: [Book], columnCount: Int) -> [ExploreGridRow] {
        guard columnCount > 0, !books.isEmpty else { return [] }
        return stride(from: 0, to: books.count, by: columnCount).map { start in
            let end = min(start + columnCount, books.count)
            return ExploreGridRow(
                index: start / columnCount,
                books: Array(books[start..<end])
            )
        }
    }
}

/// 独立且可比较的书卡：追加下一页时，已有书卡不参与 SwiftUI 的重复重建。
struct ExploreBookCell: View, Equatable {
    static let coverCornerRadius: CGFloat = 16

    let book: Book
    let width: CGFloat
    let height: CGFloat

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            BookCoverView(
                book: book,
                showsProgress: false,
                cornerRadius: Self.coverCornerRadius,
                shadowRadius: 0,
                animatesImageLoading: false,
                imageLoadDelayNanoseconds: CoverLoadingPolicy.exploreCellStartDelayNanoseconds
            )
            .frame(width: width, height: width * 4 / 3)
            .contentShape(
                RoundedRectangle(
                    cornerRadius: Self.coverCornerRadius,
                    style: .continuous
                )
            )
            Text(book.title)
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(.primary)
                .lineLimit(2, reservesSpace: true)
                .truncationMode(.tail)
                .multilineTextAlignment(.leading)
                .frame(
                    maxWidth: .infinity,
                    minHeight: ExploreGridMetrics.titleHeight,
                    maxHeight: ExploreGridMetrics.titleHeight,
                    alignment: .topLeading
                )
                .padding(.top, ExploreGridMetrics.coverTextSpacing)
            Text(book.author)
                .font(.system(size: 12, weight: .regular))
                .foregroundStyle(.secondary)
                .lineLimit(1)
                .truncationMode(.tail)
                .frame(
                    maxWidth: .infinity,
                    minHeight: ExploreGridMetrics.authorHeight,
                    maxHeight: ExploreGridMetrics.authorHeight,
                    alignment: .topLeading
                )
                .padding(.top, ExploreGridMetrics.titleAuthorSpacing)
        }
        .frame(width: width, height: height, alignment: .topLeading)
    }
}

/// 书库浏览：按栏目（全部/榜单/标签）浏览全站书目，无限下滑 + 本地筛选 + 标签栏目的服务端排序
struct ExploreListView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.horizontalSizeClass) private var sizeClass
    @State var category: ExploreCategory

    // 筛选状态（本地过滤，作用于已加载数据，与上游一致）
    @State private var statusFilter: StatusFilter = .all
    @State private var houseFilter: String = "全部文库"
    @State private var lengthFilter: LengthFilter = .all
    @State private var tagSort: TagSort = .default

    enum StatusFilter: String, CaseIterable {
        case all = "全部状态"
        case ongoing = "连载中"
        case completed = "已完结"
    }

    enum LengthFilter: String, CaseIterable {
        case all = "全部字数"
        case short = "50万字以下"
        case medium = "50~200万字"
        case long = "200万字以上"
    }

    enum TagSort: String, CaseIterable {
        case `default` = "默认"
        case hot = "按热度"
        case anime = "仅动画化"
        var suffix: String {
            switch self {
            case .default: return ""
            case .hot: return "&v=1"
            case .anime: return "&v=3"
            }
        }
    }

    var body: some View {
        GeometryReader { proxy in
            ScrollView {
                // 网格内部使用等高显式行；外层保持普通 VStack，避免多层惰性布局共同
                // 修正 contentOffset，保证行与行之间是连续滚动而不是视觉吸附。
                VStack(spacing: 14) {
                    categoryChips
                    filters
                    content(containerWidth: proxy.size.width)
                }
                .padding()
            }
            .navigationTitle("书库浏览")
            .navigationBarTitleDisplayMode(.inline)
            .refreshable {
                await store.loadExplore(category, refresh: true, sort: tagSort.suffix)
            }
            .task(id: loadKey) {
                await store.loadExplore(category, sort: tagSort.suffix)
            }
        }
    }

    private var loadKey: String {
        "\(category.id)#\(tagSort.suffix)"
    }

    // MARK: - 栏目切换

    private var categoryChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(store.exploreCategories ?? []) { item in
                    Button {
                        category = item
                        statusFilter = .all
                        houseFilter = "全部文库"
                        lengthFilter = .all
                        tagSort = .default
                    } label: {
                        HStack(spacing: 5) {
                            Text(item.title)
                            if item.id == category.id {
                                Image(systemName: "chevron.down")
                                    .font(.caption2.weight(.bold))
                            }
                        }
                        .font(.subheadline)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .background(
                            item.id == category.id
                                ? Color.accentPurple.opacity(0.16)
                                : Color(uiColor: .secondarySystemBackground),
                            in: Capsule()
                        )
                        .foregroundStyle(item.id == category.id ? Color.accentPurple : Color.secondary)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.vertical, 2)
        }
    }

    // MARK: - 筛选栏（一行横向排列，可滑动）

    private var filters: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(StatusFilter.allCases, id: \.self) { option in
                    filterChip(option.rawValue, selected: statusFilter == option) {
                        statusFilter = option
                    }
                }

                Menu {
                    Button("全部文库") { houseFilter = "全部文库" }
                    ForEach(availableHouses, id: \.self) { house in
                        Button(house) { houseFilter = house }
                    }
                } label: {
                    menuLabel(houseFilter, icon: "building.columns")
                }

                Menu {
                    ForEach(LengthFilter.allCases, id: \.self) { option in
                        Button(option.rawValue) { lengthFilter = option }
                    }
                } label: {
                    menuLabel(lengthFilter.rawValue, icon: "textformat.size")
                }

                if category.supportsSort {
                    Menu {
                        ForEach(TagSort.allCases, id: \.self) { option in
                            Button(option.rawValue) { tagSort = option }
                        }
                    } label: {
                        menuLabel(tagSort.rawValue, icon: "arrow.up.arrow.down")
                    }
                }
            }
            .padding(.vertical, 2)
        }
    }

    private func filterChip(_ label: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.subheadline)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(selected ? Color.accentPurple.opacity(0.16) : Color(uiColor: .secondarySystemBackground), in: Capsule())
                .foregroundStyle(selected ? Color.accentPurple : Color.secondary)
        }
        .buttonStyle(.plain)
    }

    private func menuLabel(_ label: String, icon: String) -> some View {
        HStack(spacing: 4) {
            Image(systemName: icon).font(.caption)
            Text(label).font(.subheadline)
            Image(systemName: "chevron.down").font(.caption)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 8)
        .background(Color(uiColor: .secondarySystemBackground), in: Capsule())
        .foregroundStyle(Color.secondary)
    }

    /// 从已加载数据里收集文库选项
    private var availableHouses: [String] {
        let state = store.exploreState(category, sort: tagSort.suffix)
        let houses = state.books.compactMap(\.publishingHouse)
        return Array(Set(houses)).sorted()
    }

    // MARK: - 内容（筛选后）

    private func filteredBooks(in books: [Book]) -> [Book] {
        books.filter { book in
            switch statusFilter {
            case .all: break
            case .ongoing: if book.isCompleted != false { return false }
            case .completed: if book.isCompleted != true { return false }
            }
            if houseFilter != "全部文库", book.publishingHouse != houseFilter { return false }
            switch lengthFilter {
            case .all: break
            case .short: if let k = book.wordCountK, k >= 500 { return false } else if book.wordCountK == nil { return false }
            case .medium: if let k = book.wordCountK, !(500..<2000).contains(k) { return false } else if book.wordCountK == nil { return false }
            case .long: if let k = book.wordCountK, k < 2000 { return false } else if book.wordCountK == nil { return false }
            }
            return true
        }
    }

    @ViewBuilder
    private func content(containerWidth: CGFloat) -> some View {
        let state = store.exploreState(category, sort: tagSort.suffix)
        let visibleBooks = filteredBooks(in: state.books)
        if state.books.isEmpty && state.isLoading {
            VStack(spacing: 12) {
                ProgressView()
                    .controlSize(.large)
                Text("正在加载…")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 80)
        } else if state.books.isEmpty, let error = state.error {
            SourceErrorBanner(message: error) {
                Task { await store.loadExplore(category, refresh: true, sort: tagSort.suffix) }
            }
        } else if state.books.isEmpty {
            emptyHint("暂无内容")
        } else if visibleBooks.isEmpty {
            emptyHint("当前筛选下没有结果")
        } else {
            bookGrid(
                books: visibleBooks,
                containerWidth: containerWidth
            )

            if state.isLoading {
                HStack(spacing: 8) {
                    ProgressView()
                    Text("正在准备下一页…")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, minHeight: 58, maxHeight: 58)
            } else if let error = state.error {
                SourceErrorBanner(message: error) {
                    Task { await store.loadMoreExplore(category, sort: tagSort.suffix) }
                }
            } else if state.canLoadMore {
                loadMoreSentinel
            } else if !state.canLoadMore {
                Text("已到底 · 共 \(state.totalPages) 页 / \(state.books.count) 本")
                    .font(.caption)
                    .foregroundStyle(.tertiary)
                    .frame(maxWidth: .infinity, minHeight: 58, maxHeight: 58)
            }
        }
    }

    private func emptyHint(_ text: String) -> some View {
        VStack(spacing: 8) {
            Image(systemName: "books.vertical")
                .font(.system(size: 30))
                .foregroundStyle(.tertiary)
            Text(text)
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 60)
    }

    private var loadMoreSentinel: some View {
        HStack(spacing: 8) {
            ProgressView()
            Text("继续加载")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, minHeight: 58, maxHeight: 58)
        // 独立于书卡的触底哨兵：空页/重复页不会让最后几张书卡失去再次触发的机会。
        .onAppear {
            Task { await store.loadMoreExplore(category, sort: tagSort.suffix) }
        }
    }

    private func bookGrid(books: [Book], containerWidth: CGFloat) -> some View {
        let metrics = ExploreGridMetrics.resolve(
            containerWidth: containerWidth,
            regularWidth: sizeClass == .regular
        )
        let triggerID = ExplorePaginationPolicy.prefetchIndex(itemCount: books.count)
            .map { books[$0].id }
        let rows = ExploreGridRow.make(from: books, columnCount: metrics.columnCount)

        return LazyVStack(spacing: ExploreGridMetrics.spacing) {
            ForEach(rows) { row in
                HStack(alignment: .top, spacing: ExploreGridMetrics.spacing) {
                    ForEach(row.books) { book in
                        NavigationLink {
                            BookDetailView(book: book)
                        } label: {
                            ExploreBookCell(
                                book: book,
                                width: metrics.cellWidth,
                                height: metrics.cellHeight
                            )
                            .equatable()
                        }
                        .buttonStyle(.plain)
                    }

                    // 末行不足列数时也补齐同尺寸槽位，行宽与前面各行完全一致。
                    ForEach(row.books.count..<metrics.columnCount, id: \.self) { _ in
                        Color.clear
                            .frame(width: metrics.cellWidth, height: metrics.cellHeight)
                            .accessibilityHidden(true)
                    }
                }
                .frame(height: metrics.cellHeight, alignment: .top)
                .onAppear {
                    guard let triggerID,
                          row.books.contains(where: { $0.id == triggerID }) else { return }
                    // 一整行只有一个预取任务，图片任务和分页任务不会在行缝处同时爆发。
                    Task { await store.loadMoreExplore(category, sort: tagSort.suffix) }
                }
            }
        }
        // 数据发布或封面完成时禁止把布局刷新隐式动画化；滚动只由 UIScrollView 物理驱动。
        .transaction { transaction in
            transaction.animation = nil
        }
    }

}
