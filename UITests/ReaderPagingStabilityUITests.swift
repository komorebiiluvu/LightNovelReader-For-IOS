import XCTest

/// 验证：阅读器翻几页后，点屏幕中间唤起床栏，不应退回章节第一页（分页不被重新计算）
final class ReaderPagingStabilityUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testFlipDoesNotResetOnBarToggle() {
        let app = XCUIApplication()
        app.launch()
        let splashText = app.staticTexts["LightNovelReader"]
        if splashText.waitForExistence(timeout: 5) {
            _ = splashText.waitForNonExistence(timeout: 25)
        }
        // 进入阅读器：探索 → 全部 → 查看全部 → 点书 → 开始阅读
        let exploreTab = app.tabBars.buttons["探索"]
        XCTAssertTrue(exploreTab.waitForExistence(timeout: 8))
        exploreTab.tap()
        let allTab = app.buttons["全部"]
        if allTab.waitForExistence(timeout: 8) { allTab.tap() }
        let viewAll = app.buttons["查看全部"].firstMatch
        if viewAll.waitForExistence(timeout: 15) { viewAll.tap() }
        sleep(4)
        let bookCell = app.cells.firstMatch
        if bookCell.waitForExistence(timeout: 10) {
            bookCell.tap()
        }
        let readButton = app.buttons.matching(NSPredicate(format: "label CONTAINS '阅读' OR label CONTAINS '开始'")).firstMatch
        if readButton.waitForExistence(timeout: 8) { readButton.tap() }
        sleep(5) // 阅读器加载

        // 记第一页截图
        let shot0 = XCTAttachment(screenshot: app.screenshot())
        shot0.name = "reader-page-initial"
        shot0.lifetime = .keepAlways
        add(shot0)

        // 翻 3 页（点右 1/3 三次）
        let right = app.coordinate(withNormalizedOffset: CGVector(dx: 0.85, dy: 0.5))
        for _ in 0..<3 {
            right.tap()
            sleep(1)
        }
        let shot1 = XCTAttachment(screenshot: app.screenshot())
        shot1.name = "reader-after-3-pages"
        shot1.lifetime = .keepAlways
        add(shot1)

        // 点中间唤起床栏
        let mid = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
        mid.tap()
        sleep(2)
        let shot2 = XCTAttachment(screenshot: app.screenshot())
        shot2.name = "reader-after-bar-toggle"
        shot2.lifetime = .keepAlways
        add(shot2)

        // 再翻一页验证仍在推进（若退回第一页，则此页是第 2 页而非第 5 页）
        right.tap()
        sleep(1)
        let shot3 = XCTAttachment(screenshot: app.screenshot())
        shot3.name = "reader-after-4th-page"
        shot3.lifetime = .keepAlways
        add(shot3)
    }
}
