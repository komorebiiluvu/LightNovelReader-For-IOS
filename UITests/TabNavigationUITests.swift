import XCTest

/// 验证底部 Tab 栏各页面能正常进入并渲染关键内容。
/// 覆盖「书架」「探索」「设置」三个主 Tab（「阅读」为默认页，另测）。
final class TabNavigationUITests: XCTestCase {

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    /// 等待 app 启动并越过开屏（Splash 至少展示 1.5s，这里等 3s 保证遮罩淡出）
    /// 等待 app 启动并越过开屏：等 splash 的「LightNovelReader」文字消失（splash 淡出才算就绪）
    private func launchApp() -> XCUIApplication {
        let app = XCUIApplication()
        app.launch()
        // splash 覆盖在 Tab 栏之上，Tab 按钮在底层一直存在；要等 splash 淡出后点击才不会被拦截
        let splashText = app.staticTexts["LightNovelReader"]
        if splashText.waitForExistence(timeout: 5) {
            _ = splashText.waitForNonExistence(timeout: 20)
        }
        return app
    }

    /// 按标题点击底部 Tab 栏按钮（UIKit UITabBarController 的按钮带 accessibility 标签）
    private func tapTab(_ app: XCUIApplication, _ title: String) {
        let tab = app.tabBars.buttons[title]
        XCTAssertTrue(tab.waitForExistence(timeout: 5), "Tab 栏应有「\(title)」按钮")
        tab.tap()
    }

    /// 截图存到 /tmp，方便人工查看各 Tab 实际渲染
    private func snapshot(_ app: XCUIApplication, _ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    func testBookshelfTab() {
        let app = launchApp()
        tapTab(app, "书架")
        // 书架页有导航标题「书架」，且（无书时）显示书籍数量统计
        let navBar = app.navigationBars["书架"]
        XCTAssertTrue(navBar.waitForExistence(timeout: 5), "书架页应有导航栏")
        let countText = app.staticTexts.matching(NSPredicate(format: "label CONTAINS '本'")).firstMatch
        XCTAssertTrue(countText.waitForExistence(timeout: 5), "书架页应显示书籍数量")
        snapshot(app, "tab-bookshelf")
    }

    func testExploreTab() {
        let app = launchApp()
        tapTab(app, "探索")
        let navBar = app.navigationBars["探索"]
        XCTAssertTrue(navBar.waitForExistence(timeout: 5), "探索页应有导航栏")
        // 探索页顶部应有搜索框（占位文字稳定存在，不依赖网络加载）
        let searchField = app.textFields["搜索书名、作者或标签"]
        XCTAssertTrue(searchField.waitForExistence(timeout: 5), "探索页应显示搜索框")
        snapshot(app, "tab-explore")
    }

    func testSettingsTab() {
        let app = launchApp()
        tapTab(app, "设置")
        let navBar = app.navigationBars["设置"]
        XCTAssertTrue(navBar.waitForExistence(timeout: 5), "设置页应有导航栏")
        // 设置页应有「数据源」区块（「特别鸣谢」置顶后需滚动到可见）
        let dataSource = app.staticTexts["数据源"]
        for _ in 0..<3 where !dataSource.exists {
            app.swipeUp()
        }
        XCTAssertTrue(dataSource.waitForExistence(timeout: 5), "设置页应显示「数据源」")
        snapshot(app, "tab-settings")
    }
}
