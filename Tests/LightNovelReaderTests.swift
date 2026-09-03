import XCTest
import UIKit
import SwiftUI
@testable import LightNovelReader

final class AppPresentationConsistencyTests: XCTestCase {
    func testAccentThemeDefaultsToBlue() {
        XCTAssertEqual(AccentTheme.defaultValue, .blue)
        XCTAssertEqual(AccentTheme.resolve(nil), .blue)
        XCTAssertEqual(AccentTheme.resolve("已失效的旧值"), .blue)
    }

    func testAccentThemeKeepsAValidSelection() {
        XCTAssertEqual(AccentTheme.resolve(AccentTheme.purple.rawValue), .purple)
    }

    func testVersionUsesRequestedDisplayFormat() {
        XCTAssertEqual(
            AppVersionDisplay.text(marketingVersion: "1.1.5"),
            "Version 1.1.5"
        )
        XCTAssertEqual(AppVersionDisplay.text(marketingVersion: "  "), "Version —")
    }

    func testSplashMetricsMatchLaunchScreenConstraints() {
        XCTAssertEqual(SplashLayoutMetrics.iconSize, 88)
        XCTAssertEqual(SplashLayoutMetrics.iconTitleSpacing, 12)
        XCTAssertEqual(SplashLayoutMetrics.titleHeight, 24)
        XCTAssertEqual(SplashLayoutMetrics.bottomInset, 60)
        XCTAssertEqual(SplashLayoutMetrics.contentHeight, 184)
    }
}

final class ReaderPaginationTests: XCTestCase {
    func testPaginateFillsPage() {
        let paragraphs = (0..<100).map { "这是第 \($0) 段测试文字，用来验证分页器是否正常工作。" }
        let pages = ReaderPagination.paginate(
            paragraphs: paragraphs, width: 300, height: 500,
            fontSize: 17, lineSpacing: 8, family: .system, bold: false
        )
        XCTAssertFalse(pages.isEmpty, "分页结果不应为空")
        // 每页（除最后一页）至少应有一段
        XCTAssertGreaterThan(pages.count, 1)
    }

    func testPaginateEmptyInput() {
        let pages = ReaderPagination.paginate(
            paragraphs: [], width: 300, height: 500,
            fontSize: 17, lineSpacing: 8
        )
        XCTAssertTrue(pages.isEmpty)
    }

    func testPaginateSingleShortParagraph() {
        let pages = ReaderPagination.paginate(
            paragraphs: ["短文本"], width: 300, height: 500,
            fontSize: 17, lineSpacing: 8
        )
        XCTAssertEqual(pages.count, 1)
        XCTAssertEqual(pages[0], ["短文本"])
    }

    func testPaginateStopsWhenCancelled() {
        let pages = ReaderPagination.paginate(
            paragraphs: Array(repeating: "需要取消的长段落", count: 100),
            width: 300,
            height: 500,
            fontSize: 17,
            lineSpacing: 8,
            shouldCancel: { true }
        )
        XCTAssertTrue(pages.isEmpty)
    }
}

final class ReaderScrollChapterAdvancePolicyTests: XCTestCase {
    func testUpwardPullStartedAtBottomAdvances() {
        XCTAssertTrue(
            ReaderScrollChapterAdvancePolicy.shouldAdvance(
                startedAtBottom: true,
                verticalTranslation: -48,
                canAdvance: true
            )
        )
    }

    func testOrdinaryScrollThatOnlyEndsAtBottomDoesNotAdvance() {
        XCTAssertFalse(
            ReaderScrollChapterAdvancePolicy.shouldAdvance(
                startedAtBottom: false,
                verticalTranslation: -300,
                canAdvance: true
            )
        )
    }

    func testShortOrDownwardPullDoesNotAdvance() {
        XCTAssertFalse(
            ReaderScrollChapterAdvancePolicy.shouldAdvance(
                startedAtBottom: true,
                verticalTranslation: -47,
                canAdvance: true
            )
        )
        XCTAssertFalse(
            ReaderScrollChapterAdvancePolicy.shouldAdvance(
                startedAtBottom: true,
                verticalTranslation: 100,
                canAdvance: true
            )
        )
    }

    func testLastChapterNeverAdvances() {
        XCTAssertFalse(
            ReaderScrollChapterAdvancePolicy.shouldAdvance(
                startedAtBottom: true,
                verticalTranslation: -200,
                canAdvance: false
            )
        )
    }

    func testNativeScrollOffsetUsesBottomTolerance() {
        XCTAssertTrue(
            ReaderScrollChapterAdvancePolicy.isAtBottom(
                contentOffsetY: 976,
                maximumOffsetY: 1_000
            )
        )
        XCTAssertFalse(
            ReaderScrollChapterAdvancePolicy.isAtBottom(
                contentOffsetY: 975,
                maximumOffsetY: 1_000
            )
        )
        XCTAssertTrue(
            ReaderScrollChapterAdvancePolicy.isAtBottom(
                contentOffsetY: 0,
                maximumOffsetY: 0
            ),
            "不足一屏的短章节应当视为已经位于底部"
        )
    }
}

final class StableCacheKeyTests: XCTestCase {
    func testStableHashMatchesKnownSHA256() {
        XCTAssertEqual(
            StableHash.hex("abc"),
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        )
    }

    func testOfflineImageNameIsDeterministic() {
        let url = "https://example.com/illustration.jpg"
        let first = AppStore.offlineImageName(chapterIndex: 7, offset: 2, urlString: url)
        let second = AppStore.offlineImageName(chapterIndex: 7, offset: 2, urlString: url)
        XCTAssertEqual(first, second)
        XCTAssertTrue(first.hasPrefix("7-2-"))
    }

    func testLegacyOfflineImageIsMigratedOnRead() async throws {
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("lnr-cache-test-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: root) }

        let cache = ChapterDiskCache(root: root)
        let payload = Data([0x01, 0x02, 0x03])
        await cache.storeImage(
            payload,
            bookID: "book",
            source: "source",
            name: "7-2-123456"
        )

        let stableName = AppStore.offlineImageName(
            chapterIndex: 7,
            offset: 2,
            urlString: "https://example.com/illustration.jpg"
        )
        let migrated = await cache.imageURL(
            bookID: "book",
            source: "source",
            name: stableName,
            legacyPrefix: "7-2-"
        )

        let url = try XCTUnwrap(migrated)
        XCTAssertEqual(url.lastPathComponent, stableName)
        XCTAssertEqual(try Data(contentsOf: url), payload)
    }
}

@MainActor
final class ChapterLoadDeduplicationTests: XCTestCase {
    func testConcurrentChapterLoadsShareOneRequest() async {
        let service = CountingBookSourceService()
        let store = AppStore(services: [service])
        let book = Book(
            id: "dedup-\(UUID().uuidString)",
            title: "去重测试",
            author: "测试",
            tags: [],
            source: service.name,
            totalChapters: 0,
            lastChapter: 0,
            hasUpdate: false,
            hits: 0,
            intro: "",
            coverIndex: 0
        )

        async let first: Void = store.loadChapters(for: book)
        async let second: Void = store.loadChapters(for: book)
        _ = await (first, second)

        XCTAssertEqual(service.chapterFetchCount, 1)
        XCTAssertEqual(store.chapterTitles(for: book)?.count, 1)
    }
}

private final class CountingBookSourceService: BookSourceService {
    let name = "测试书源"
    private let lock = NSLock()
    private var fetchCount = 0

    var chapterFetchCount: Int {
        lock.lock(); defer { lock.unlock() }
        return fetchCount
    }

    func fetchBooks() async throws -> [Book] { [] }

    func fetchChapters(for book: Book) async throws -> [ChapterItem] {
        lock.lock()
        fetchCount += 1
        lock.unlock()
        try await Task.sleep(nanoseconds: 100_000_000)
        return [ChapterItem(index: 0, title: "第一章")]
    }

    func fetchContent(for book: Book, chapter index: Int) async throws -> ChapterContent {
        ChapterContent(index: index, title: "第一章", paragraphs: ["正文"])
    }

    func search(_ term: String) async throws -> [Book] { [] }
}

@MainActor
final class ExplorePaginationTests: XCTestCase {
    func testNextPageAppendsBooksAndAdvancesState() async {
        let firstPage = [testBook("one"), testBook("two")]
        let secondPage = [testBook("three"), testBook("four")]
        let service = ExplorePagingService(responses: [
            1: [.page(firstPage, totalPages: 3)],
            2: [.page(secondPage, totalPages: 3)],
        ])
        let store = AppStore(services: [service])
        let category = testCategory()

        await store.loadExplore(category)
        await store.loadMoreExplore(category)

        let state = store.exploreState(category)
        XCTAssertEqual(state.loadedPages, 2)
        XCTAssertEqual(state.totalPages, 3)
        XCTAssertEqual(state.books.map(\.id), ["one", "two", "three", "four"])
        XCTAssertEqual(state.bookIDs, ["one", "two", "three", "four"])
        XCTAssertNil(state.error)
    }

    func testRepeatedNextPageDoesNotMarkListAsFinished() async {
        let firstPage = [testBook("one"), testBook("two")]
        let service = ExplorePagingService(responses: [
            1: [.page(firstPage, totalPages: 3)],
            // 书源把第一页误当第二页返回时，不能推进页码或静默结束。
            2: [.page(firstPage, totalPages: 3)],
        ])
        let store = AppStore(services: [service])
        let category = testCategory()

        await store.loadExplore(category)
        await store.loadMoreExplore(category)

        let state = store.exploreState(category)
        XCTAssertEqual(state.loadedPages, 1)
        XCTAssertEqual(state.totalPages, 3)
        XCTAssertEqual(state.books.map(\.id), ["one", "two"])
        XCTAssertEqual(state.bookIDs, ["one", "two"])
        XCTAssertNotNil(state.error)
        XCTAssertTrue(state.canLoadMore)
    }

    func testFailedNextPageRemainsVisibleAndRetryable() async {
        let firstPage = [testBook("one"), testBook("two")]
        let secondPage = [testBook("three")]
        let service = ExplorePagingService(responses: [
            1: [.page(firstPage, totalPages: 2)],
            2: [.failure, .failure, .failure, .page(secondPage, totalPages: 2)],
        ])
        let store = AppStore(services: [service])
        let category = testCategory()

        await store.loadExplore(category)
        await store.loadMoreExplore(category)

        var state = store.exploreState(category)
        XCTAssertEqual(state.loadedPages, 1)
        XCTAssertNotNil(state.error, "已有首页数据时，后续页失败也必须显示重试入口")
        XCTAssertTrue(state.canLoadMore)

        await store.loadMoreExplore(category)
        state = store.exploreState(category)
        XCTAssertEqual(state.loadedPages, 2)
        XCTAssertEqual(state.books.map(\.id), ["one", "two", "three"])
        XCTAssertEqual(state.bookIDs, ["one", "two", "three"])
        XCTAssertNil(state.error)
        XCTAssertEqual(service.requestedPages, [1, 2, 2, 2, 2])
    }

    private func testCategory() -> ExploreCategory {
        ExploreCategory(
            id: "pagination-test-\(UUID().uuidString)",
            title: "分页测试",
            path: "/test",
            extraParams: ""
        )
    }

    private func testBook(_ id: String) -> Book {
        Book(
            id: id,
            title: id,
            author: "测试",
            tags: [],
            source: "分页测试书源",
            totalChapters: 0,
            lastChapter: 0,
            hasUpdate: false,
            hits: 0,
            intro: "",
            coverIndex: 0
        )
    }
}

@MainActor
final class ExplorePerformancePolicyTests: XCTestCase {
    func testPrefetchUsesOneStableTriggerBeforeTheBottom() {
        XCTAssertFalse(ExplorePaginationPolicy.shouldPrefetch(visibleIndex: 7, itemCount: 20))
        XCTAssertTrue(ExplorePaginationPolicy.shouldPrefetch(visibleIndex: 8, itemCount: 20))
        XCTAssertFalse(ExplorePaginationPolicy.shouldPrefetch(visibleIndex: 19, itemCount: 20))
        XCTAssertEqual(ExplorePaginationPolicy.prefetchIndex(itemCount: 20), 8)
    }

    func testPrefetchPolicyRejectsInvalidIndices() {
        XCTAssertFalse(ExplorePaginationPolicy.shouldPrefetch(visibleIndex: -1, itemCount: 20))
        XCTAssertFalse(ExplorePaginationPolicy.shouldPrefetch(visibleIndex: 20, itemCount: 20))
        XCTAssertFalse(ExplorePaginationPolicy.shouldPrefetch(visibleIndex: 0, itemCount: 0))
        XCTAssertNil(ExplorePaginationPolicy.prefetchIndex(itemCount: 0))
    }

    func testExploreGridUsesStableFixedColumns() {
        let compact = ExploreGridMetrics.resolve(containerWidth: 375, regularWidth: false)
        XCTAssertEqual(compact.columnCount, 3)
        XCTAssertEqual(compact.cellWidth, 105, accuracy: 0.001)
        XCTAssertEqual(compact.coverHeight, 140, accuracy: 0.001)
        XCTAssertEqual(compact.cellHeight, 204, accuracy: 0.001)
        XCTAssertEqual(compact.columns.count, compact.columnCount)

        XCTAssertGreaterThanOrEqual(ExploreGridMetrics.titleHeight, 36)
        XCTAssertGreaterThanOrEqual(ExploreGridMetrics.authorHeight, 16)

        let regular = ExploreGridMetrics.resolve(containerWidth: 1_024, regularWidth: true)
        XCTAssertEqual(regular.columnCount, 5)
        XCTAssertEqual(regular.cellWidth, 187.2, accuracy: 0.001)
        XCTAssertEqual(ExploreBookCell.coverCornerRadius, 16)
    }

    func testExploreRowsHaveStableIdentityAndFixedOccupancy() {
        let books = (0..<8).map { index in
            Book(
                id: "row-\(index)", title: "测试", author: "作者", tags: [],
                source: "测试", totalChapters: 0, lastChapter: 0,
                hasUpdate: false, hits: 0, intro: "", coverIndex: 0
            )
        }
        let rows = ExploreGridRow.make(from: books, columnCount: 3)

        XCTAssertEqual(rows.count, 3)
        XCTAssertEqual(rows.map(\.books.count), [3, 3, 2])
        XCTAssertEqual(rows.map(\.id), ["row-0", "row-3", "row-6"])
    }

    func testCoverLoadingPolicyDefersAndPrioritizesWork() {
        XCTAssertEqual(CoverLoadingPolicy.defaultPrewarmLimit, 4)
        XCTAssertGreaterThan(CoverLoadingPolicy.exploreCellStartDelayNanoseconds, 0)
        XCTAssertEqual(CoverLoadingPolicy.presentationIntervalNanoseconds, 8_000_000)
        XCTAssertTrue(CoverLoadingPolicy.isForeground(.userInitiated))
        XCTAssertFalse(CoverLoadingPolicy.isForeground(.utility))
        XCTAssertFalse(CoverLoadingPolicy.isForeground(.background))
    }

    func testCoverCacheCreatesAProvidedRootDirectory() {
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("lnr-cover-cache-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: root) }

        _ = CoverImageCache(root: root, maxConcurrentLoads: 1)

        var isDirectory: ObjCBool = false
        XCTAssertTrue(FileManager.default.fileExists(atPath: root.path, isDirectory: &isDirectory))
        XCTAssertTrue(isDirectory.boolValue)
    }

    func testCoverURLCanonicalizationUsesHTTPS() {
        XCTAssertEqual(
            CoverImageCache.canonicalURLString("http://img.wenku8.com/image/1/2/2s.jpg"),
            "https://img.wenku8.com/image/1/2/2s.jpg"
        )
        XCTAssertEqual(
            CoverImageCache.canonicalURLString("https://example.com/cover.jpg"),
            "https://example.com/cover.jpg"
        )
    }

    func testDownsampleReturnsDecodedBoundedImage() throws {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let data = UIGraphicsImageRenderer(size: CGSize(width: 1_000, height: 1_500), format: format)
            .pngData { context in
                UIColor.systemPurple.setFill()
                context.fill(CGRect(x: 0, y: 0, width: 1_000, height: 1_500))
            }

        let image = try XCTUnwrap(CoverImageCache.downsample(data, maxPixel: 120))
        let longestEdge = max(image.cgImage?.width ?? 0, image.cgImage?.height ?? 0)
        XCTAssertLessThanOrEqual(longestEdge, 120)
    }
}

final class ReaderAppearancePolicyTests: XCTestCase {
    func testReaderFollowSystemUsesActualSystemAppearance() {
        let followSystem = ReaderBackgroundPolicy.followSystemIndex
        XCTAssertEqual(
            ReaderBackgroundPolicy.resolvedIndex(selection: followSystem, systemIsDark: false),
            ReaderBackgroundPolicy.systemLightIndex
        )
        XCTAssertEqual(
            ReaderBackgroundPolicy.resolvedIndex(selection: followSystem, systemIsDark: true),
            ReaderBackgroundPolicy.systemDarkIndex
        )
    }

    func testExplicitReaderBackgroundIsIndependentFromSystemAndAppAppearance() {
        XCTAssertEqual(ReaderBackgroundPolicy.resolvedIndex(selection: 1, systemIsDark: false), 1)
        XCTAssertEqual(ReaderBackgroundPolicy.resolvedIndex(selection: 1, systemIsDark: true), 1)
        XCTAssertNil(ThemePreference.system.colorScheme)
    }
}

final class ReaderPreferencesDefaultsTests: XCTestCase {
    func testFreshDefaultsMatchRequestedReadingLayout() {
        assertRequestedDefaults(ReaderPreferences())
    }

    func testMissingPersistedFieldsUseRequestedDefaults() throws {
        let preferences = try JSONDecoder().decode(
            ReaderPreferences.self,
            from: Data("{}".utf8)
        )
        assertRequestedDefaults(preferences)
    }

    private func assertRequestedDefaults(
        _ preferences: ReaderPreferences,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        XCTAssertEqual(preferences.fontFamily, .kaiti, file: file, line: line)
        XCTAssertFalse(preferences.bold, file: file, line: line)
        XCTAssertEqual(preferences.fontSize, 22, file: file, line: line)
        XCTAssertEqual(preferences.lineSpacing, 8, file: file, line: line)
        XCTAssertEqual(preferences.marginLeft, 35, file: file, line: line)
        XCTAssertEqual(preferences.marginRight, 35, file: file, line: line)
        XCTAssertEqual(preferences.marginTop, 72, file: file, line: line)
        XCTAssertEqual(preferences.marginBottom, 24, file: file, line: line)
        XCTAssertEqual(preferences.mode, .flip, file: file, line: line)
    }
}

@MainActor
final class PageCurlSingleSidedTests: XCTestCase {
    private func makeReader(pageCount: Int = 3) -> PageCurlReaderView {
        let paperColor = UIColor(red: 0.11, green: 0.12, blue: 0.15, alpha: 1)
        return PageCurlReaderView(
            pageCount: pageCount,
            initialPage: 0,
            background: paperColor,
            renderToken: "single-sided-test",
            pageBuilder: { _ in AnyView(EmptyView()) },
            onEdge: { _ in },
            onPageChanged: { _ in },
            onToggleBars: {}
        )
    }

    func testConfigurationUsesOneSingleSidedController() {
        XCTAssertFalse(PageCurlConfiguration.isDoubleSided)
        XCTAssertEqual(PageCurlConfiguration.targetControllerCount, 1)
        let controller = PageCurlReaderView.makePageViewController()
        XCTAssertFalse(controller.isDoubleSided)
        XCTAssertEqual(controller.spineLocation, .min)
    }

    func testSingleSidedSequenceMovesDirectlyBetweenContentPages() {
        XCTAssertNil(PageCurlPageSequence.previousIndex(from: 0, pageCount: 3))
        XCTAssertEqual(PageCurlPageSequence.previousIndex(from: 1, pageCount: 3), 0)
        XCTAssertEqual(PageCurlPageSequence.previousIndex(from: 2, pageCount: 3), 1)
        XCTAssertEqual(PageCurlPageSequence.nextIndex(from: 0, pageCount: 3), 1)
        XCTAssertEqual(PageCurlPageSequence.nextIndex(from: 1, pageCount: 3), 2)
        XCTAssertNil(PageCurlPageSequence.nextIndex(from: 2, pageCount: 3))
        XCTAssertNil(PageCurlPageSequence.nextIndex(from: -1, pageCount: 3))
        XCTAssertNil(PageCurlPageSequence.previousIndex(from: 0, pageCount: 0))
    }

    func testCoordinatorBuildsOnlyOneOpaqueContentPage() throws {
        let reader = makeReader()
        let coordinator = reader.makeCoordinator()
        let page = try XCTUnwrap(coordinator.pageVC(for: 1))

        XCTAssertEqual(page.pageIndex, 1)
        XCTAssertNotNil(page.content)
        XCTAssertTrue(page.view.isOpaque)
        XCTAssertEqual(page.view.backgroundColor, reader.background)
        XCTAssertEqual(coordinator.visibleControllers(at: 1).count, 1)
        XCTAssertNil(coordinator.pageVC(for: -1))
        XCTAssertNil(coordinator.pageVC(for: 3))
    }

    func testDataSourceReturnsAdjacentContentPagesWithoutBackSurfaces() throws {
        let reader = makeReader()
        let coordinator = reader.makeCoordinator()
        let controller = UIPageViewController(
            transitionStyle: .pageCurl,
            navigationOrientation: .horizontal,
            options: [.spineLocation: UIPageViewController.SpineLocation.min.rawValue]
        )
        controller.isDoubleSided = PageCurlConfiguration.isDoubleSided
        controller.dataSource = coordinator
        coordinator.pvc = controller
        let current = try XCTUnwrap(coordinator.pageVC(for: 1))
        controller.setViewControllers([current], direction: .forward, animated: false)

        let previous = try XCTUnwrap(
            coordinator.pageViewController(controller, viewControllerBefore: current)
                as? PageHostController
        )
        let next = try XCTUnwrap(
            coordinator.pageViewController(controller, viewControllerAfter: current)
                as? PageHostController
        )

        XCTAssertEqual(previous.pageIndex, 0)
        XCTAssertEqual(next.pageIndex, 2)
        XCTAssertEqual(coordinator.currentPage(in: controller), 1)
    }

    func testOneControllerIsAcceptedForBothProgrammaticDirections() throws {
        let reader = makeReader()
        let coordinator = reader.makeCoordinator()
        let controller = UIPageViewController(
            transitionStyle: .pageCurl,
            navigationOrientation: .horizontal,
            options: [.spineLocation: UIPageViewController.SpineLocation.min.rawValue]
        )
        controller.isDoubleSided = PageCurlConfiguration.isDoubleSided
        controller.dataSource = coordinator
        coordinator.pvc = controller

        let middle = try XCTUnwrap(coordinator.pageVC(for: 1))
        let next = try XCTUnwrap(coordinator.pageVC(for: 2))
        let previous = try XCTUnwrap(coordinator.pageVC(for: 0))
        controller.setViewControllers([middle], direction: .forward, animated: false)
        controller.setViewControllers([next], direction: .forward, animated: false)
        controller.setViewControllers([previous], direction: .reverse, animated: false)

        XCTAssertEqual(controller.viewControllers?.count, 1)
        XCTAssertEqual(coordinator.currentPage(in: controller), 0)
    }
}

private final class ExplorePagingService: BookSourceService {
    enum Outcome {
        case page([Book], totalPages: Int)
        case failure
    }

    let name = "分页测试书源"
    private var responses: [Int: [Outcome]]
    private(set) var requestedPages: [Int] = []

    init(responses: [Int: [Outcome]]) {
        self.responses = responses
    }

    func fetchBooks() async throws -> [Book] { [] }

    func fetchChapters(for book: Book) async throws -> [ChapterItem] { [] }

    func fetchContent(for book: Book, chapter index: Int) async throws -> ChapterContent {
        ChapterContent(index: index, title: "", paragraphs: [])
    }

    func search(_ term: String) async throws -> [Book] { [] }

    func fetchExplorePage(_ category: ExploreCategory, page: Int, sortSuffix: String) async throws -> (books: [Book], totalPages: Int) {
        requestedPages.append(page)
        var outcomes = responses[page] ?? [.failure]
        let outcome = outcomes.removeFirst()
        responses[page] = outcomes
        switch outcome {
        case .page(let books, let totalPages):
            return (books, totalPages)
        case .failure:
            throw BookSourceError.unreachable
        }
    }
}

@MainActor
final class TitleNormalizationTests: XCTestCase {
    func testNormalizedTitleStripsBrackets() {
        let normalized = AppStore.normalizedTitle("文学少女（合集）")
        XCTAssertEqual(normalized, "文学少女")
    }

    func testNormalizedTitleStripsFullWidthBrackets() {
        let normalized = AppStore.normalizedTitle("无职转生～到了异世界就拿出真本事～(无职转生)")
        XCTAssertEqual(normalized, "无职转生～到了异世界就拿出真本事～")
    }
}

final class BackupRoundTripTests: XCTestCase {
    @MainActor
    func testBackupRoundTrip() async throws {
        // 用临时 UserDefaults 隔离测试
        let suiteName = "test.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suiteName)!
        defaults.removePersistentDomain(forName: suiteName)

        // 注意：AppStore 用 UserDefaults.standard，这里改为注入较复杂，
        // 故直接测快照编解码往返（不依赖全局存储）
        let book = Book(id: "wk8-1", title: "测试", author: "作者", tags: [],
                        source: "文库8(在线)", totalChapters: 10, lastChapter: 5,
                        hasUpdate: true, hits: 0, intro: "简介", coverIndex: 0)
        let data = try JSONEncoder().encode([book])
        let decoded = try JSONDecoder().decode([Book].self, from: data)
        XCTAssertEqual(decoded.count, 1)
        XCTAssertEqual(decoded[0].id, "wk8-1")
        XCTAssertEqual(decoded[0].lastChapter, 5)
        defaults.removePersistentDomain(forName: suiteName)
    }
}

final class ChapterContentBackwardCompatTests: XCTestCase {
    func testDecodeWithoutImagesField() throws {
        // 旧缓存 JSON 无 images 字段，解码不应崩溃且 images 应为空
        let oldJSON = """
        {"index": 3, "title": "旧章节", "paragraphs": ["第一段", "第二段"]}
        """
        let content = try JSONDecoder().decode(ChapterContent.self, from: Data(oldJSON.utf8))
        XCTAssertEqual(content.title, "旧章节")
        XCTAssertEqual(content.paragraphs.count, 2)
        XCTAssertTrue(content.images.isEmpty)
    }
}

final class Wenku8ExplorePageParserTests: XCTestCase {
    func testExplorePageCountAcceptsWhitespaceAndPaginationLinks() {
        let html = """
        <div id="pagelink"><em id="pagestats">2 / 105</em>
        <a href="articlelist.php?page=104">104</a>
        <a href="articlelist.php?page=105">105</a></div>
        """
        XCTAssertEqual(Wenku8Service.exploreTotalPages(in: html), 105)
    }

    func testExplorePageCountFallsBackToLargestLinkedPage() {
        let html = """
        <div id="pagelink"><a href="toplist.php?sort=anime&amp;page=3">3</a>
        <a href="toplist.php?sort=anime&amp;page=12">12</a></div>
        """
        XCTAssertEqual(Wenku8Service.exploreTotalPages(in: html), 12)
    }
}

#if canImport(SharedKit)
import SharedKit

final class KmpExploreWiringTests: XCTestCase {
    func testKmpAdapterProvidesUpstreamExploreCategories() {
        let adapter = KmpBookSourceAdapter()
        let categories = adapter.exploreCategories
        // 对齐上游 6 个栏目（轻小说列表/热门/动画化/今日更新/新书一览/完结全本）
        XCTAssertEqual(categories.count, 6, "KMP 适配器应提供上游的 6 个探索栏目")
        let titles = Set(categories.map(\.title))
        XCTAssertTrue(titles.contains("轻小说列表"), "应包含「轻小说列表」栏目")
        XCTAssertTrue(titles.contains("热门轻小说"), "应包含「热门轻小说」栏目")
        XCTAssertTrue(titles.contains("完结全本"), "应包含「完结全本」栏目")
    }

    func testKmpAdapterProvidesUpstreamTagList() async throws {
        let adapter = KmpBookSourceAdapter()
        let tags = try await adapter.fetchTagList()
        XCTAssertGreaterThanOrEqual(tags.count, 48, "KMP 适配器应提供上游硬编码的 48 个标签")
        XCTAssertTrue(tags.contains("校园"))
        XCTAssertTrue(tags.contains("NTR"))
    }

    func testKmpAdapterTagCategory() {
        let adapter = KmpBookSourceAdapter()
        let cat = adapter.tagCategory("校园")
        XCTAssertEqual(cat.id, "tag-校园")
        XCTAssertTrue(cat.path.contains("tags.php"), "标签栏目应指向 tags.php")
        XCTAssertTrue(cat.supportsSort, "标签栏目应支持服务端排序")
    }
}
#endif

#if canImport(SharedKit)
import SharedKit

/// 验证 KMP Gbk 完整解码表（含 GBK 扩展繁体/日文汉字）
final class GbkDecodeTests: XCTestCase {
    func testDecodeFullGbkChars() {
        // 「不吉波普系列」gb18030 字节
        let result = GbkBridge.shared.decodeHex(hex: "B2BBBCAAB2A8C6D5CFB5C1D0")
        XCTAssertEqual(result, "不吉波普系列", "完整 GBK 表应正确解码书名")
    }

    func testDecodeGbkExtChars() {
        // 繁体「體」(GBK 扩展 0xF3 0x77)
        let result = GbkBridge.shared.decodeHex(hex: "F377")
        XCTAssertEqual(result, "體", "GBK 扩展繁体字应正确解码")
    }
}
#endif

#if canImport(SharedKit)
import SharedKit

/// 详情页解析验证：简介应正常，不应是错误/乱码
final class BookDetailParseTests: XCTestCase {
    func testBookInformationDescription() async throws {
        let api = Wenku8DataSourceApi()
        // 先登录（真实网络）
        let loggedIn = try await withCheckedThrowingContinuation { (c: CheckedContinuation<Bool, Error>) in
            api.login(username: "komorebiiluv", password: "komorebi041016") { msg, err in
                if let err {
                    let nsErr = NSError(domain: "test", code: 1, userInfo: [NSLocalizedDescriptionKey: String(describing: err)])
                    c.resume(throwing: nsErr); return
                }
                c.resume(returning: msg == nil)
            }
        }
        XCTAssertTrue(loggedIn, "应登录成功")
        let info: SharedKit.BookInformation = try await withCheckedThrowingContinuation { c in
            api.getBookInformation(bookId: "855") { info, err in
                if let err {
                    let nsErr = NSError(domain: "test", code: 1, userInfo: [NSLocalizedDescriptionKey: String(describing: err)])
                    c.resume(throwing: nsErr); return
                }
                c.resume(returning: info!)
            }
        }
        print("[DetailTest] title=\(info.title)")
        print("[DetailTest] desc=\(info.description.prefix(100))")
        XCTAssertFalse(info.author.contains("文章状态"), "作者不应含文章状态（td 拼接 bug），实际: \(info.author.prefix(50))")
        XCTAssertFalse(info.description.contains("插图"), "简介不应是章节链接（插图）")
        XCTAssertFalse(info.description.isEmpty, "简介不应为空")
        print("[DetailTest] descContent=\(info.description_.prefix(60))")
    }
}
#endif

/// 崩溃报告功能验证（写崩溃文件 → 能读回）
final class CrashReporterTests: XCTestCase {
    func testWriteAndReadCrash() {
        // 触发一次崩溃记录（模拟异常捕获）
        CrashReporter.shared.writeTestCrash()
        XCTAssertTrue(CrashReporter.shared.hasCrashes, "应有崩溃报告")
        let content = CrashReporter.shared.exportContent()
        XCTAssertTrue(content.contains("测试崩溃"), "崩溃报告应含测试内容")
        CrashReporter.shared.clear()
        XCTAssertFalse(CrashReporter.shared.hasCrashes, "清除后应无报告")
    }
}

#if canImport(SharedKit)
import SharedKit

/// Flat 版正文获取验证（对应「开始阅读」链路，避免 JsonObject 桥接崩溃）
final class FlatContentTests: XCTestCase {
    func testFetchFlatContent() async throws {
        let api = Wenku8DataSourceApi()
        _ = try await withCheckedThrowingContinuation { (c: CheckedContinuation<Bool, Error>) in
            api.login(username: "komorebiiluv", password: "komorebi041016") { msg, err in
                if let err {
                    let nsErr = NSError(domain: "t", code: 1, userInfo: [NSLocalizedDescriptionKey: String(describing: err)])
                    c.resume(throwing: nsErr); return
                }
                c.resume(returning: msg == nil)
            }
        }
        // 拉第一章正文（Flat 版）
        let flat: SharedKit.ChapterContentFlat = try await withCheckedThrowingContinuation { c in
            api.getChapterContentFlat(chapterId: "178183", bookId: "855") { ct, err in
                if let err {
                    let nsErr = NSError(domain: "t", code: 1, userInfo: [NSLocalizedDescriptionKey: String(describing: err)])
                    c.resume(throwing: nsErr); return
                }
                c.resume(returning: ct!)
            }
        }
        print("[FlatTest] title=\(flat.title)")
        print("[FlatTest] paragraphs=\(flat.paragraphs.count)")
        print("[FlatTest] firstPara=\(flat.paragraphs.first?.prefix(50) ?? "")")
        XCTAssertFalse(flat.title.isEmpty, "标题不应为空")
        XCTAssertGreaterThan(flat.paragraphs.count, 3, "正文应分段（<br> 切分），实际 \(flat.paragraphs.count) 段")
        // 用断言消息带出段落信息（测试输出可见）
        let summary = "段落数=\(flat.paragraphs.count) 前3段=" + flat.paragraphs.prefix(3).map { String($0.prefix(20)) }.joined(separator: " | ")
        XCTAssertTrue(flat.paragraphs.count >= 10, summary)
    }
}
#endif
