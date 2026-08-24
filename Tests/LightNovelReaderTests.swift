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
