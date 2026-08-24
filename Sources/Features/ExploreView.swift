import SwiftUI

struct ExploreView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.horizontalSizeClass) private var sizeClass
    @State private var query = ""
    @State private var exploreTab: ExploreTab = .home

    enum ExploreTab: String, CaseIterable {
        case home = "首页"
        case all = "全部"
        case tags = "分类"
    }

    /// 输入过程中的即时本地过滤
    private var localResults: [Book] {
        guard !query.isEmpty else { return [] }
        return store.books.filter {
            $0.title.localizedCaseInsensitiveContains(query)
                || $0.author.localizedCaseInsensitiveContains(query)
                || $0.tags.contains(where: { $0.localizedCaseInsensitiveContains(query) })
        }
    }

    /// 提交后展示书源返回的结果
    private var usingServiceResults: Bool {
        !store.submittedTerm.isEmpty && query == store.submittedTerm
    }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 22) {
                searchField

                if query.isEmpty {
                    if !store.searchHistory.isEmpty {
                        historySection
                    }
                    normalContent
                } else if usingServiceResults {
                    serviceResults
                } else {
                    localResultsSection
                }
            }
            .padding()
        }
        .refreshable {
            await store.reload()
            if let categories = store.exploreCategories {
                for category in categories {
                    await store.loadExplore(category, refresh: true)
                }
            }
        }
        .task {
            if let categories = store.exploreCategories {
                for category in categories {
                    await store.loadExplore(category)
                }
            }
            await store.loadHomeBlocks()
            await store.loadTagList()
        }
        .navigationTitle("探索")
    }

    private var searchField: some View {
        HStack(spacing: 10) {
            Image(systemName: "magnifyingglass")
                .foregroundStyle(.secondary)
            TextField("搜索书名、作者或标签", text: $query)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .onSubmit {
                    Task { await store.submitSearch(query) }
                }
            if !query.isEmpty {
                Button {
                    query = ""
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundStyle(.tertiary)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 16)
        .frame(height: 46)
        .background(Color(uiColor: .secondarySystemBackground), in: RoundedRectangle(cornerRadius: 23, style: .continuous))
    }

    private var historySection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("搜索历史").font(.headline)
                Spacer()
                Button("清除") {
                    store.clearSearchHistory()
                }
                .font(.caption)
                .foregroundStyle(.secondary)
            }
            FlowTagList(tags: store.searchHistory) { term in
                query = term
                Task { await store.submitSearch(term) }
            }
        }
    }

    // MARK: - 探索主页（上游同款三大板块：首页 / 全部 / 分类）

    private var normalContent: some View {
        VStack(alignment: .leading, spacing: 22) {
            if let loadError = store.loadError {
                SourceErrorBanner(message: loadError) {
                    Task { await store.reload() }
                }
            }

            if store.exploreCategories == nil {
                // 模拟源：直接平铺当前书目
                mockSourceSection
            } else if !store.isWenku8LoggedIn {
                loginHintBanner
            } else {
                segmentedTabs
                switch exploreTab {
                case .home: homeTab
                case .all: allTab
                case .tags: tagsTab
                }
            }
        }
    }

    private var segmentedTabs: some View {
        HStack(spacing: 6) {
            ForEach(ExploreTab.allCases, id: \.self) { tab in
                Button {
                    withAnimation(.easeInOut(duration: 0.2)) { exploreTab = tab }
                } label: {
                    Text(tab.rawValue)
                        .font(.subheadline)
                        .fontWeight(exploreTab == tab ? .semibold : .regular)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .background(
                            exploreTab == tab ? Color.accentPurple : Color(uiColor: .secondarySystemBackground),
                            in: RoundedRectangle(cornerRadius: 10, style: .continuous)
                        )
                        .foregroundStyle(exploreTab == tab ? Color.white : Color.secondary)
                }
                .buttonStyle(.plain)
            }
        }
    }

    // 首页板块：wenku8 首页推荐块（新书风云榜/本周会员推荐榜/最近更新）
    private var homeTab: some View {
        VStack(alignment: .leading, spacing: 24) {
            if store.homeBlocks.isEmpty, store.homeBlocksError != nil {
                SourceErrorBanner(message: store.homeBlocksError ?? "") {
                    Task { await store.loadHomeBlocks(force: true) }
                }
            } else if store.homeBlocks.isEmpty {
                VStack(spacing: 12) {
                    ProgressView()
                        .controlSize(.large)
                    Text("正在加载…")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 80)
            } else {
                ForEach(store.homeBlocks, id: \.title) { block in
                    VStack(alignment: .leading, spacing: 12) {
                        Text(block.title).font(.headline)
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 12) {
                                ForEach(block.books) { book in
                                    NavigationLink {
                                        BookDetailView(book: book)
                                    } label: {
                                        exploreCarouselCell(book, width: carouselCoverWidth)
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                            .padding(.vertical, 2)
                        }
                    }
                }
            }
        }
    }

    // 全部板块：6 个栏目
    private var allTab: some View {
        VStack(alignment: .leading, spacing: 26) {
            ForEach(store.exploreCategories ?? []) { category in
                exploreSection(category)
            }
        }
    }

    // 分类板块：标签云
    private var tagsTab: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("按标签浏览").font(.headline)
            if store.tagList.isEmpty, store.tagListError != nil {
                SourceErrorBanner(message: store.tagListError ?? "") {
                    Task { await store.loadTagList(force: true) }
                }
            } else if store.tagList.isEmpty {
                ProgressView().frame(maxWidth: .infinity).padding(.vertical, 30)
            } else {
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 72), spacing: 8)], alignment: .leading, spacing: 8) {
                    ForEach(store.tagList, id: \.self) { tag in
                        NavigationLink {
                            ExploreListView(category: Wenku8Service.tagCategory(tag))
                        } label: {
                            Text(tag)
                                .font(.subheadline)
                                .lineLimit(1)
                                .fixedSize()
                                .padding(.horizontal, 14)
                                .padding(.vertical, 7)
                                .background(Color(uiColor: .secondarySystemBackground), in: Capsule())
                                .foregroundStyle(.secondary)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    private var loginHintBanner: some View {
        HStack(spacing: 10) {
            Image(systemName: "person.crop.circle.badge.exclamationmark")
            Text("登录 wenku8 账号后即可浏览全站书库")
                .font(.subheadline)
            Spacer()
        }
        .foregroundStyle(.secondary)
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(uiColor: .secondarySystemBackground), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    /// 首页横排封面宽度：随屏幕宽度动态计算，iPad 上放大以填满一行
    private var carouselCoverWidth: CGFloat {
        let width = UIScreen.main.bounds.width
        let divisor: CGFloat = sizeClass == .regular ? 6.5 : 3.6
        return min(max(width / divisor, 96), 200)
    }
    private var carouselCoverHeight: CGFloat {
        carouselCoverWidth * 4 / 3
    }

    private func exploreSection(_ category: ExploreCategory) -> some View {
        let state = store.exploreState(category)
        let rowBooks = Array(state.books.prefix(8))
        let coverWidth = carouselCoverWidth
        let coverHeight = carouselCoverHeight
        return VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(category.title).font(.headline)
                Spacer()
                NavigationLink {
                    ExploreListView(category: category)
                } label: {
                    HStack(spacing: 3) {
                        Text("查看全部")
                        Image(systemName: "chevron.right")
                            .font(.caption.weight(.semibold))
                    }
                    .font(.subheadline)
                    .foregroundStyle(Color.accentPurple)
                }
                .buttonStyle(.plain)
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 14) {
                    if rowBooks.isEmpty {
                        if state.error != nil {
                            Button {
                                Task { await store.loadExplore(category, refresh: true) }
                            } label: {
                                HStack(spacing: 6) {
                                    Image(systemName: "arrow.clockwise")
                                    Text("加载失败，点击重试")
                                }
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            }
                            .buttonStyle(.plain)
                            .frame(width: coverWidth)
                        } else {
                            HStack {
                                Spacer()
                                ProgressView()
                                Spacer()
                            }
                            .frame(width: coverWidth, height: coverHeight)
                        }
                    } else {
                        ForEach(rowBooks) { book in
                            NavigationLink {
                                BookDetailView(book: book)
                            } label: {
                                exploreCarouselCell(book, width: coverWidth)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                .padding(.vertical, 2)
            }
        }
    }

    private func exploreCarouselCell(_ book: Book, width: CGFloat) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            BookCoverView(book: book, showsProgress: false, shadowRadius: 0)
                .frame(width: width)
            Text(book.title)
                .font(.caption)
                .fontWeight(.medium)
                .foregroundStyle(.primary)
                .lineLimit(1)
                .frame(width: width, alignment: .leading)
        }
    }

    private var mockSourceSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("书目 · \(store.books.count) 本").font(.headline)
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 96), spacing: 14)], spacing: 16) {
                ForEach(store.books) { book in
                    NavigationLink {
                        BookDetailView(book: book)
                    } label: {
                        VStack(alignment: .leading, spacing: 6) {
                            BookCoverView(book: book, shadowRadius: 0)
                            Text(book.title)
                                .font(.caption)
                                .fontWeight(.medium)
                                .lineLimit(2)
                                .multilineTextAlignment(.leading)
                                .foregroundStyle(.primary)
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    // MARK: - 搜索结果

    private var localResultsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("输入中 · \(localResults.count) 本").font(.headline)
            if localResults.isEmpty {
                searchEmptyHint
            }
            ForEach(localResults) { book in
                searchResultRow(book)
            }
        }
    }

    private var serviceResults: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("搜索结果 · 来自「\(store.source)」").font(.headline)
            if let notice = store.searchNotice {
                Text(notice)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            if store.isSearching {
                HStack(spacing: 10) {
                    ProgressView()
                    Text("正在搜索「\(store.submittedTerm)」…")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 24)
            } else if let searchError = store.searchError {
                SourceErrorBanner(message: searchError) {
                    Task { await store.runSearch(query) }
                }
            } else if store.searchResults.isEmpty {
                searchEmptyHint
            } else {
                ForEach(store.searchResults) { book in
                    searchResultRow(book)
                }
            }
        }
    }

    private var searchEmptyHint: some View {
        VStack(spacing: 8) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 32))
                .foregroundStyle(.tertiary)
            Text("没有找到「\(query)」相关的书")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 40)
    }

    private func searchResultRow(_ book: Book) -> some View {
        NavigationLink {
            BookDetailView(book: book)
        } label: {
            HStack(spacing: 12) {
                BookCoverView(book: book, showsProgress: false, shadowRadius: 0)
                    .frame(width: 40, height: 53)
                VStack(alignment: .leading, spacing: 3) {
                    Text(book.title)
                        .font(.subheadline)
                        .fontWeight(.medium)
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                    Text("\(book.author) · \(book.tags.joined(separator: " / "))")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
            }
            .padding(12)
            .background(Color(uiColor: .secondarySystemBackground), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}

private struct FlowTagList: View {
    let tags: [String]
    let onTap: (String) -> Void

    private let columns = [GridItem(.adaptive(minimum: 64), spacing: 8)]

    var body: some View {
        LazyVGrid(columns: columns, alignment: .leading, spacing: 8) {
            ForEach(tags, id: \.self) { tag in
                Button {
                    onTap(tag)
                } label: {
                    Text(tag)
                        .font(.subheadline)
                        .lineLimit(1)
                        .fixedSize()
                        .padding(.horizontal, 14)
                        .padding(.vertical, 7)
                        .background(Color(uiColor: .secondarySystemBackground), in: Capsule())
                        .foregroundStyle(.secondary)
                }
                .buttonStyle(.plain)
            }
        }
    }
}
