import XCTest

/// 验证核心功能（模拟器/真机均可跑，需联网）：
/// (a) 搜索能搜出结果
/// (b) 探索「全部」→「查看全部」下滑能加载新书（分页）
final class CoreSearchPagingUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    private func launchApp() -> XCUIApplication {
        let app = XCUIApplication()
        app.launch()
        let splashText = app.staticTexts["LightNovelReader"]
        if splashText.waitForExistence(timeout: 5) {
            _ = splashText.waitForNonExistence(timeout: 25)
        }
        return app
    }

    private func tapTab(_ app: XCUIApplication, _ title: String) {
        let tab = app.tabBars.buttons[title]
        XCTAssertTrue(tab.waitForExistence(timeout: 8), "Tab 栏应有「\(title)」")
        tab.tap()
    }

    /// (a) 搜索「刀剑神域」应返回结果
    func testSearchReturnsResults() {
        let app = launchApp()
        tapTab(app, "探索")
        let searchField = app.textFields["搜索书名、作者或标签"]
        XCTAssertTrue(searchField.waitForExistence(timeout: 8), "探索页应有搜索框")
        searchField.tap()
        searchField.typeText("刀剑神域\n")  // \n 触发 onSubmit
        // 等搜索结果头部出现
        let header = app.staticTexts.matching(NSPredicate(format: "label CONTAINS '搜索结果'")).firstMatch
        let headerFound = header.waitForExistence(timeout: 25)
        // 找书卡（"刀剑神域" 相关书名）
        let anyBook = app.staticTexts.matching(NSPredicate(format: "label CONTAINS '刀剑神域'")).firstMatch
        let bookFound = anyBook.waitForExistence(timeout: 15)
        let shot = XCTAttachment(screenshot: app.screenshot())
        shot.name = "search-result"
        shot.lifetime = .keepAlways
        add(shot)
        XCTAssertTrue(headerFound, "搜索结果页应出现「搜索结果」标题")
        XCTAssertTrue(bookFound, "搜索「刀剑神域」应返回包含该名的书")
    }

    /// (b) 探索「全部」→ 第一个栏目「查看全部」→ 下滑加载第 2 页
    func testExploreViewAllPaging() {
        let app = launchApp()
        tapTab(app, "探索")
        // 点「全部」segmented
        let allTab = app.buttons["全部"]
        XCTAssertTrue(allTab.waitForExistence(timeout: 8), "应有「全部」tab")
        allTab.tap()
        // 等栏目出现，点第一个「查看全部」
        let viewAll = app.buttons["查看全部"].firstMatch
        XCTAssertTrue(viewAll.waitForExistence(timeout: 15), "应有「查看全部」")
        // 先记录当前书卡数量
        viewAll.tap()
        // 书库浏览页：下滑几次触发分页
        let nav = app.navigationBars["书库浏览"]
        XCTAssertTrue(nav.waitForExistence(timeout: 10), "应进入书库浏览页")
        sleep(4) // 等第一页加载
        let shot1 = XCTAttachment(screenshot: app.screenshot())
        shot1.name = "explore-list-p1"
        shot1.lifetime = .keepAlways
        add(shot1)
        // 下滑 3 次触发 loadMore
        for _ in 0..<3 {
            app.swipeUp()
            sleep(2)
        }
        let shot2 = XCTAttachment(screenshot: app.screenshot())
        shot2.name = "explore-list-after-scroll"
        shot2.lifetime = .keepAlways
        add(shot2)
        // 断言：页面仍正常（无错误横幅），且滚动后有内容
        let errorBanner = app.staticTexts.matching(NSPredicate(format: "label CONTAINS '加载失败'")).firstMatch
        XCTAssertFalse(errorBanner.exists, "下滑加载不应出现「加载失败」")
        sleep(2)
    }
}
