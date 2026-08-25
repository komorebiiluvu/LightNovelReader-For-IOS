import SwiftUI

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

    /// 动态列数：按容器宽度与目标最小封面宽计算，填满整行。
    /// 平板用更大的最小封面宽（宁可少几列，也要让每本书大到合适）。
    private func columns(containerWidth: CGFloat) -> [GridItem] {
        let width = containerWidth
        let minCellWidth: CGFloat = sizeClass == .regular ? 160 : 104
        let spacing: CGFloat = 14
        let count = max(2, Int((width - spacing) / (minCellWidth + spacing)))
        return Array(repeating: GridItem(.flexible(), spacing: spacing), count: count)
    }

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
                LazyVStack(spacing: 14) {
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

    private var filteredBooks: [Book] {
        let state = store.exploreState(category, sort: tagSort.suffix)
        return state.books.filter { book in
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
        } else if filteredBooks.isEmpty {
            emptyHint("当前筛选下没有结果")
        } else {
            bookGrid(state, books: filteredBooks, containerWidth: containerWidth)

            if state.isLoading {
                ProgressView().padding(.vertical, 18)
            } else if let error = state.error {
                SourceErrorBanner(message: error) {
                    Task { await store.loadMoreExplore(category, sort: tagSort.suffix) }
                }
            } else if !state.canLoadMore {
                Text("已到底 · 共 \(state.totalPages) 页 / \(state.books.count) 本")
                    .font(.caption)
                    .foregroundStyle(.tertiary)
                    .padding(.vertical, 14)
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

    private func bookGrid(_ state: AppStore.ExploreBrowseState, books: [Book], containerWidth: CGFloat) -> some View {
        LazyVGrid(columns: columns(containerWidth: containerWidth), spacing: 14) {
            ForEach(books) { book in
                NavigationLink {
                    BookDetailView(book: book)
                } label: {
                    exploreCell(book)
                }
                .buttonStyle(.plain)
                .onAppear {
                    // 接近底部时自动加载下一页
                    if books.suffix(4).contains(where: { $0.id == book.id }) {
                        Task { await store.loadMoreExplore(category, sort: tagSort.suffix) }
                    }
                }
            }
        }
    }

    private func exploreCell(_ book: Book) -> some View {
        VStack(alignment: .leading, spacing: 7) {
            BookCoverView(book: book, showsProgress: false, shadowRadius: 0)
                .frame(maxWidth: .infinity)   // 等宽，高度由 3:4 自动等比
            Text(book.title)
                .font(.caption)
                .fontWeight(.medium)
                .foregroundStyle(.primary)
                .lineLimit(2)
                .multilineTextAlignment(.leading)
            Text(book.author)
                .font(.caption2)
                .foregroundStyle(.secondary)
                .lineLimit(1)
        }
    }

}
