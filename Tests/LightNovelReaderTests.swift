import XCTest
@testable import LightNovelReader

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
