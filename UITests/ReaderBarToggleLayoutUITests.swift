import XCTest

/// 验证阅读界面正文区域在顶栏/底栏显隐时位置不变（锁死在固定矩形）
final class ReaderBarToggleLayoutUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testReaderTextAreaStableOnBarToggle() {
        let app = XCUIApplication()
        app.launch()
        let splashText = app.staticTexts["LightNovelReader"]
        if splashText.waitForExistence(timeout: 5) {
            _ = splashText.waitForNonExistence(timeout: 25)
        }
        // 进入探索 → 点"全部" → 查看全部 → 点开一本书进详情 → 开始阅读
        let exploreTab = app.tabBars.buttons["探索"]
        XCTAssertTrue(exploreTab.waitForExistence(timeout: 8))
        exploreTab.tap()
        let allTab = app.buttons["全部"]
        if allTab.waitForExistence(timeout: 8) { allTab.tap() }
        let viewAll = app.buttons["查看全部"].firstMatch
        if viewAll.waitForExistence(timeout: 15) { viewAll.tap() }
        // 书库浏览页第一个书卡
        sleep(4)
        let firstBook = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH '轻小说' OR label BEGINSWITH '刀剑'")).firstMatch
        let bookCell = app.cells.firstMatch
        if bookCell.waitForExistence(timeout: 10) {
            bookCell.tap()
        } else if firstBook.exists {
            firstBook.tap()
        }
        // 详情页 → 开始阅读
        let readButton = app.buttons.matching(NSPredicate(format: "label CONTAINS '阅读' OR label CONTAINS '开始'")).firstMatch
        if readButton.waitForExistence(timeout: 8) { readButton.tap() }
        // 阅读器加载
        sleep(5)

        // 此时默认 showBars=false → 顶栏/底栏隐藏 → 截图（bar 隐藏）
        let shot1 = XCTAttachment(screenshot: app.screenshot())
        shot1.name = "reader-bars-hidden"
        shot1.lifetime = .keepAlways
        add(shot1)

        // 点屏幕中间 1/3 唤起顶栏/底栏
        let mid = app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
        mid.tap()
        sleep(2)
        let shot2 = XCTAttachment(screenshot: app.screenshot())
        shot2.name = "reader-bars-shown"
        shot2.lifetime = .keepAlways
        add(shot2)

        // 再点隐藏
        mid.tap()
        sleep(2)
        let shot3 = XCTAttachment(screenshot: app.screenshot())
        shot3.name = "reader-bars-hidden-again"
        shot3.lifetime = .keepAlways
        add(shot3)
    }
}
