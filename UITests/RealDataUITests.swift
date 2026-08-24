import XCTest

/// 真机/有网环境下的真实数据验证：探索页应显示真实书目（不是空/0本）
final class RealDataUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testExploreShowsRealData() {
        let app = XCUIApplication()
        app.launch()
        // 等 splash 淡出
        let splash = app.staticTexts["LightNovelReader"]
        if splash.waitForExistence(timeout: 5) {
            _ = splash.waitForNonExistence(timeout: 25)
        }
        // 点探索 Tab
        let exploreTab = app.tabBars.buttons["探索"]
        XCTAssertTrue(exploreTab.waitForExistence(timeout: 10), "应有探索 Tab")
        exploreTab.tap()
        // 等探索页加载（首页板块应出现推荐块标题，或至少不出现"书目 0 本"）
        let nav = app.navigationBars["探索"]
        XCTAssertTrue(nav.waitForExistence(timeout: 10), "应有探索导航栏")
        sleep(6) // 等真实数据加载
        // 截图
        let shot = XCTAttachment(screenshot: app.screenshot())
        shot.name = "explore-real-data"
        shot.lifetime = .keepAlways
        add(shot)
        // 断言：不应出现"0 本"（mock 空态）
        let zeroBook = app.staticTexts.matching(NSPredicate(format: "label CONTAINS '0 本'")).firstMatch
        XCTAssertFalse(zeroBook.exists, "探索页不应显示「0 本」空态")
    }
}
